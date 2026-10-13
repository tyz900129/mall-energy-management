package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountBill;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic="platform-sync")
@Service
@RequiredArgsConstructor
public class BalanceWarningService {
    public static final String EVENT="ENERGY_BALANCE_WARNING";
    private final JdbcTemplate jdbc;
    private final PlatformConfigService configService;
    private final PlatformAdapter adapter;
    private final NotificationService notificationService;
    private final AtomicBoolean running=new AtomicBoolean(false);

    public Map<String,Object> syncAndAnalyze(String trigger){
        if(!running.compareAndSet(false,true))throw BizException.badRequest("余额预警分析正在执行，请稍后再试");
        LocalDate date=LocalDate.now(),billEnd=date.minusDays(1);Long runId=null;
        try{
            jdbc.update("INSERT INTO energy_balance_warning_run(trigger_type,status,analysis_date) VALUES(?,'RUNNING',?)",trigger,date);
            runId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            PlatformConfigService.PlatformConnection c=configService.connect();
            List<PlatformAccount> accounts=adapter.fetchAccounts(c.baseUrl(),c.token(),c.spaceId()).stream().filter(this::enabledAccount).toList();
            int success=0,failed=0;
            for(PlatformAccount account:accounts){
                try{
                    ensureBills(c,account,billEnd);
                    analyze(account,date,billEnd);
                    success++;
                }catch(Exception ex){failed++;log.warn("balance warning analysis failed, account={}, error={}",account.getAccountNumber(),ex.getMessage());}
            }
            Integer warningCount=jdbc.queryForObject("SELECT COUNT(*) FROM energy_balance_warning_result WHERE analysis_date=? AND analysis_status='WARNING'",Integer.class,date);
            List<Map<String,Object>> pending=jdbc.queryForList("SELECT r.id,r.account_name,r.balance,r.daily_average,r.sample_days,r.available_days FROM energy_balance_warning_result r WHERE r.analysis_date=? AND r.analysis_status='WARNING' AND r.notified=0 AND COALESCE((SELECT a.webhook_enabled FROM energy_balance_warning_rule a WHERE a.account_number=r.account_number),(SELECT d.webhook_enabled FROM energy_balance_warning_rule d WHERE d.account_number='__DEFAULT__'),1)=1",date);
            if(!pending.isEmpty()){
                notificationService.notifyConfiguredEvent(EVENT,message(date,pending));
                pending.forEach(r->jdbc.update("UPDATE energy_balance_warning_result SET notified=1 WHERE id=?",r.get("id")));
            }
            jdbc.update("UPDATE energy_balance_warning_run SET status='SUCCESS',account_count=?,success_count=?,failed_count=?,warning_count=?,completed_at=NOW(3) WHERE id=?",accounts.size(),success,failed,warningCount==null?0:warningCount,runId);
            return run(runId);
        }catch(Exception ex){
            if(runId!=null)jdbc.update("UPDATE energy_balance_warning_run SET status='FAILED',error_message=?,completed_at=NOW(3) WHERE id=?",shortText(ex.getMessage(),500),runId);
            throw ex instanceof BizException b?b:BizException.badRequest("余额预警分析失败："+shortText(ex.getMessage(),180));
        }finally{running.set(false);}
    }

    private boolean enabledAccount(PlatformAccount a){String s=text(a.getPlatformStatus()).toUpperCase();return !text(a.getAccountNumber()).isBlank()&&!text(a.getPlatformAccountId()).isBlank()&&!Set.of("0","DISABLED","INACTIVE").contains(s);}
    private void ensureBills(PlatformConfigService.PlatformConnection c,PlatformAccount a,LocalDate end){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM energy_account_daily_bill WHERE account_number=? AND bill_date BETWEEN ? AND ?",Integer.class,a.getAccountNumber(),end.minusDays(6),end);
        if(count!=null&&count>0&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM energy_account_daily_bill WHERE account_number=? AND bill_date=?)",Boolean.class,a.getAccountNumber(),end)))return;
        for(PlatformAccountBill b:adapter.fetchAccountHistoryBills(c.baseUrl(),c.token(),a.getPlatformAccountId()))if(b.getBillDate()!=null&&b.getAmount()!=null&&!b.getBillDate().isAfter(end)&&!b.getBillDate().isBefore(end.minusDays(30)))jdbc.update("INSERT INTO energy_account_daily_bill(account_number,platform_account_id,source_bill_id,bill_date,start_time,end_time,amount,source_updated_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount),source_updated_at=VALUES(source_updated_at),fetched_at=NOW(3)",a.getAccountNumber(),a.getPlatformAccountId(),b.getSourceBillId(),b.getBillDate(),b.getStartTime(),b.getEndTime(),b.getAmount(),b.getSourceUpdatedAt());
    }
    private void analyze(PlatformAccount a,LocalDate date,LocalDate end){
        List<BigDecimal> amounts=jdbc.query("SELECT amount FROM energy_account_daily_bill WHERE account_number=? AND bill_date BETWEEN ? AND ? ORDER BY bill_date",(rs,n)->rs.getBigDecimal(1),a.getAccountNumber(),end.minusDays(6),end);
        Map<String,Object> rule=effectiveRule(a.getAccountNumber());BigDecimal threshold=decimal(rule.get("warningDays"),BigDecimal.valueOf(3));
        String status;BigDecimal sum=null,avg=null,days=null;int samples=amounts.size();
        if(!bool(rule.get("enabled"))||ignored(rule,date))status="IGNORED";
        else if(a.getBalance()==null)status="NO_BALANCE";
        else if(amounts.isEmpty())status="NO_DATA";
        else{sum=amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add);avg=sum.divide(BigDecimal.valueOf(samples),4,RoundingMode.HALF_UP);if(avg.compareTo(BigDecimal.ZERO)<=0)status="ZERO_CONSUMPTION";else{BigDecimal balance=a.getBalance()==null?BigDecimal.ZERO:a.getBalance();days=balance.max(BigDecimal.ZERO).divide(avg,2,RoundingMode.HALF_UP);status=days.compareTo(threshold)<0?"WARNING":"NORMAL";}}
        String name=!text(a.getUserName()).isBlank()?a.getUserName():!text(a.getAccountName()).isBlank()?a.getAccountName():a.getAccountNumber();
        jdbc.update("INSERT INTO energy_balance_warning_result(account_number,account_name,analysis_date,balance,seven_day_amount,daily_average,sample_days,available_days,warning_days,analysis_status) VALUES(?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE account_name=VALUES(account_name),balance=VALUES(balance),seven_day_amount=VALUES(seven_day_amount),daily_average=VALUES(daily_average),sample_days=VALUES(sample_days),available_days=VALUES(available_days),warning_days=VALUES(warning_days),analysis_status=VALUES(analysis_status),notified=IF(VALUES(analysis_status)='WARNING',notified,0)",a.getAccountNumber(),name,date,a.getBalance(),sum,avg,samples,days,threshold,status);
    }

    public Map<String,Object> overview(LocalDate date){LocalDate target=date==null?LocalDate.now():date;Map<String,Object> out=new LinkedHashMap<>();out.put("date",target);out.putAll(jdbc.queryForMap("SELECT COUNT(*) totalCount,SUM(analysis_status='WARNING') warningCount,SUM(analysis_status='NORMAL') normalCount,SUM(analysis_status='NO_DATA') noDataCount,SUM(analysis_status='NO_BALANCE') noBalanceCount,SUM(analysis_status='ZERO_CONSUMPTION') zeroConsumptionCount,MIN(CASE WHEN analysis_status='WARNING' THEN available_days END) minimumAvailableDays FROM energy_balance_warning_result WHERE analysis_date=?",target));out.put("running",running.get());return out;}
    public Map<String,Object> results(LocalDate date,String keyword,String status,int pageNum,int pageSize){LocalDate target=date==null?LocalDate.now():date;int size=Math.min(Math.max(pageSize,1),100),offset=(Math.max(pageNum,1)-1)*size;StringBuilder where=new StringBuilder(" WHERE analysis_date=?");List<Object> args=new ArrayList<>();args.add(target);if(keyword!=null&&!keyword.isBlank()){where.append(" AND (account_name LIKE ? OR account_number LIKE ?)");String k="%"+keyword.trim()+"%";args.add(k);args.add(k);}if(status!=null&&!status.isBlank()){where.append(" AND analysis_status=?");args.add(status);}Long total=jdbc.queryForObject("SELECT COUNT(*) FROM energy_balance_warning_result"+where,Long.class,args.toArray());List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(offset);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,account_number accountNumber,account_name accountName,analysis_date analysisDate,balance,seven_day_amount sevenDayAmount,daily_average dailyAverage,sample_days sampleDays,available_days availableDays,warning_days warningDays,analysis_status analysisStatus,notified FROM energy_balance_warning_result"+where+" ORDER BY CASE analysis_status WHEN 'WARNING' THEN 0 ELSE 1 END,available_days ASC LIMIT ? OFFSET ?",paged.toArray());return Map.of("records",rows,"total",total==null?0:total,"pageNum",Math.max(pageNum,1),"pageSize",size);}
    public List<Map<String,Object>> rules(String keyword){String filter=keyword==null||keyword.isBlank()?"":" AND (p.account_number LIKE ? OR p.account_name LIKE ? OR p.user_name LIKE ?)";List<Object> args=new ArrayList<>();if(!filter.isEmpty()){String k="%"+keyword.trim()+"%";args.add(k);args.add(k);args.add(k);}return jdbc.queryForList("SELECT p.account_number accountNumber,COALESCE(NULLIF(p.user_name,''),NULLIF(p.account_name,''),p.account_number) accountName,COALESCE(r.enabled,1) enabled,r.warning_days warningDays,COALESCE(r.webhook_enabled,1) webhookEnabled,r.ignore_start_date ignoreStartDate,r.ignore_end_date ignoreEndDate,r.remark FROM platform_account_current p LEFT JOIN energy_balance_warning_rule r ON r.account_number=p.account_number WHERE (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE'))"+filter+" ORDER BY accountName LIMIT 500",args.toArray());}
    public Map<String,Object> defaultRule(){return effectiveRule("__DEFAULT__");}
    public void saveRule(String number,Map<String,Object> body){if(number==null||number.isBlank())throw BizException.badRequest("账户不能为空");jdbc.update("INSERT INTO energy_balance_warning_rule(account_number,enabled,warning_days,webhook_enabled,ignore_start_date,ignore_end_date,remark) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),warning_days=VALUES(warning_days),webhook_enabled=VALUES(webhook_enabled),ignore_start_date=VALUES(ignore_start_date),ignore_end_date=VALUES(ignore_end_date),remark=VALUES(remark)",number,bool(body.get("enabled"))?1:0,nullableDecimal(body.get("warningDays")),boolDefault(body.get("webhookEnabled"),true)?1:0,nullableDate(body.get("ignoreStartDate")),nullableDate(body.get("ignoreEndDate")),shortText(text(body.get("remark")),255));}
    public List<Map<String,Object>> runs(){return jdbc.queryForList("SELECT id,trigger_type triggerType,status,analysis_date analysisDate,account_count accountCount,success_count successCount,failed_count failedCount,warning_count warningCount,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_balance_warning_run ORDER BY id DESC LIMIT 20");}
    private Map<String,Object> effectiveRule(String number){Map<String,Object> defaults=jdbc.queryForMap("SELECT enabled,warning_days warningDays,webhook_enabled webhookEnabled,ignore_start_date ignoreStartDate,ignore_end_date ignoreEndDate,remark FROM energy_balance_warning_rule WHERE account_number='__DEFAULT__'");if("__DEFAULT__".equals(number))return defaults;List<Map<String,Object>> rows=jdbc.queryForList("SELECT enabled,warning_days warningDays,webhook_enabled webhookEnabled,ignore_start_date ignoreStartDate,ignore_end_date ignoreEndDate,remark FROM energy_balance_warning_rule WHERE account_number=?",number);if(rows.isEmpty())return defaults;Map<String,Object> merged=new LinkedHashMap<>(defaults);rows.get(0).forEach((k,v)->{if(v!=null)merged.put(k,v);});return merged;}
    private boolean ignored(Map<String,Object> r,LocalDate date){LocalDate start=toDate(r.get("ignoreStartDate")),end=toDate(r.get("ignoreEndDate"));return start!=null&&end!=null&&!date.isBefore(start)&&!date.isAfter(end);}
    private String message(LocalDate date,List<Map<String,Object>> rows){StringBuilder b=new StringBuilder("### 🔋 计费账户余额预警\n\n**分析日期**：").append(date).append("\n**预计不足3天账户**：").append(rows.size()).append(" 个\n\n---\n");int max=Math.min(rows.size(),30);for(int i=0;i<max;i++){Map<String,Object> r=rows.get(i);b.append("\n**").append(i+1).append(". ").append(text(r.get("account_name"))).append("**\n- 当前余额：**¥").append(money(r.get("balance"))).append("**\n- 近7日平均：¥").append(money(r.get("daily_average"))).append("/天\n- 预计可用：**").append(decimal(r.get("available_days"),BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP)).append(" 天**\n");}if(rows.size()>max)b.append("\n> 其余 ").append(rows.size()-max).append(" 个账户请进入系统查看。\n");return b.toString().trim();}
    private Map<String,Object> run(Long id){return jdbc.queryForMap("SELECT id,trigger_type triggerType,status,analysis_date analysisDate,account_count accountCount,success_count successCount,failed_count failedCount,warning_count warningCount,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_balance_warning_run WHERE id=?",id);}
    private String money(Object v){return decimal(v,BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).toPlainString();}private String text(Object v){return v==null?"":String.valueOf(v).trim();}private boolean bool(Object v){return v!=null&&(v.equals(Boolean.TRUE)||"1".equals(v.toString())||"true".equalsIgnoreCase(v.toString()));}private boolean boolDefault(Object v,boolean d){return v==null?d:bool(v);}private BigDecimal decimal(Object v,BigDecimal d){try{return v==null?d:new BigDecimal(v.toString());}catch(Exception e){return d;}}private BigDecimal nullableDecimal(Object v){return v==null||text(v).isBlank()?null:new BigDecimal(v.toString());}private LocalDate nullableDate(Object v){return v==null||text(v).isBlank()?null:LocalDate.parse(v.toString());}private LocalDate toDate(Object v){if(v==null)return null;if(v instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(v.toString());}private String shortText(String v,int max){if(v==null)return null;return v.length()<=max?v:v.substring(0,max);}
}
