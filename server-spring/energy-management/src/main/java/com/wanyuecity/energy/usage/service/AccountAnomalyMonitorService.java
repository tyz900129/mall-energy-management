package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformAccountBill;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class AccountAnomalyMonitorService {
    public static final String EVENT = "ENERGY_ACCOUNT_ANOMALY";
    private final JdbcTemplate jdbc;
    private final PlatformConfigService configService;
    private final PlatformAdapter adapter;
    private final NotificationService notificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Map<String,Object> syncAndAnalyze(String trigger) {
        if (!running.compareAndSet(false,true)) throw BizException.badRequest("账户异动分析正在执行，请稍后再试");
        LocalDate target = LocalDate.now().minusDays(1);
        Long runId = null;
        try {
            jdbc.update("INSERT INTO energy_account_anomaly_run(trigger_type,status,target_date) VALUES(?,'RUNNING',?)",trigger,target);
            runId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            List<Map<String,Object>> accounts=jdbc.queryForList("SELECT platform_account_id,account_number,COALESCE(NULLIF(user_name,''),NULLIF(account_name,''),account_number) account_name FROM platform_account_current WHERE platform_account_id IS NOT NULL AND platform_account_id<>'' AND (platform_status IS NULL OR UPPER(platform_status) NOT IN ('0','DISABLED','INACTIVE')) ORDER BY account_number");
            PlatformConfigService.PlatformConnection c=configService.connect();
            int success=0,failed=0;
            for(Map<String,Object> account:accounts){
                String platformId=text(account.get("platform_account_id")),number=text(account.get("account_number"));
                try{
                    List<PlatformAccountBill> bills=adapter.fetchAccountHistoryBills(c.baseUrl(),c.token(),platformId);
                    bills.stream().filter(b->b.getBillDate()!=null&&b.getAmount()!=null).sorted(Comparator.comparing(PlatformAccountBill::getBillDate).reversed()).limit(31).forEach(b->upsertBill(number,platformId,b));
                    analyzeAccount(number,text(account.get("account_name")),target);
                    success++;
                }catch(Exception ex){failed++;log.warn("account anomaly bill fetch failed, account={}, platformId={}, error={}",number,platformId,ex.getMessage());}
            }
            List<Map<String,Object>> pending=jdbc.queryForList("SELECT id,account_name,actual_amount,baseline_average,sample_days,difference_amount,difference_pct,direction,severity FROM energy_account_anomaly_result WHERE bill_date=? AND analysis_status='ANOMALY' AND notified=0 AND account_number NOT IN (SELECT account_number FROM energy_account_anomaly_rule WHERE webhook_enabled=0)",target);
            if(!pending.isEmpty()){
                notificationService.notifyConfiguredEvent(EVENT,message(target,pending));
                pending.forEach(row->jdbc.update("UPDATE energy_account_anomaly_result SET notified=1 WHERE id=?",row.get("id")));
            }
            jdbc.update("UPDATE energy_account_anomaly_run SET status='SUCCESS',account_count=?,success_count=?,failed_count=?,anomaly_count=?,completed_at=NOW(3) WHERE id=?",accounts.size(),success,failed,pending.size(),runId);
            return run(runId);
        }catch(Exception ex){
            if(runId!=null)jdbc.update("UPDATE energy_account_anomaly_run SET status='FAILED',error_message=?,completed_at=NOW(3) WHERE id=?",shortText(ex.getMessage(),500),runId);
            throw ex instanceof BizException b?b:BizException.badRequest("异动分析失败："+shortText(ex.getMessage(),180));
        }finally{running.set(false);}
    }

    private void upsertBill(String accountNumber,String platformId,PlatformAccountBill b){
        jdbc.update("INSERT INTO energy_account_daily_bill(account_number,platform_account_id,source_bill_id,bill_date,start_time,end_time,amount,source_updated_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_bill_id=VALUES(source_bill_id),platform_account_id=VALUES(platform_account_id),start_time=VALUES(start_time),end_time=VALUES(end_time),amount=VALUES(amount),source_updated_at=VALUES(source_updated_at),fetched_at=NOW(3)",accountNumber,platformId,b.getSourceBillId(),b.getBillDate(),b.getStartTime(),b.getEndTime(),b.getAmount(),b.getSourceUpdatedAt());
    }

    private void analyzeAccount(String number,String name,LocalDate target){
        List<BigDecimal> actualRows=jdbc.query("SELECT amount FROM energy_account_daily_bill WHERE account_number=? AND bill_date=?",(rs,n)->rs.getBigDecimal(1),number,target);
        if(actualRows.isEmpty()){saveResult(number,name,target,null,null,0,null,null,null,null,"NO_DATA");return;}
        BigDecimal actual=actualRows.get(0);
        List<BigDecimal> history=jdbc.query("SELECT amount FROM energy_account_daily_bill WHERE account_number=? AND bill_date<? ORDER BY bill_date DESC LIMIT 30",(rs,n)->rs.getBigDecimal(1),number,target);
        int samples=history.size();
        if(samples<7){saveResult(number,name,target,actual,null,samples,null,null,null,null,"OBSERVING");return;}
        BigDecimal avg=history.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(samples),4,RoundingMode.HALF_UP);
        BigDecimal diff=actual.subtract(avg),pct=avg.compareTo(BigDecimal.ZERO)==0?null:diff.multiply(BigDecimal.valueOf(100)).divide(avg,2,RoundingMode.HALF_UP);
        Map<String,Object> rule=effectiveRule(number);
        if(!bool(rule.get("enabled"))||ignored(rule,target)){saveResult(number,name,target,actual,avg,samples,diff,pct,null,null,"IGNORED");return;}
        BigDecimal min=decimal(rule.get("minimumDifference"),BigDecimal.valueOf(20));
        BigDecimal rise=decimal(rule.get("riseThresholdPct"),BigDecimal.valueOf(50));
        BigDecimal fall=decimal(rule.get("fallThresholdPct"),BigDecimal.valueOf(50));
        if(samples<15){rise=rise.multiply(BigDecimal.valueOf(2));fall=fall.multiply(BigDecimal.valueOf(2));}
        String direction=null,severity=null,status="NORMAL";
        if(pct!=null&&diff.abs().compareTo(min)>=0){
            if(pct.compareTo(rise)>=0){direction="RISE";severity=pct.compareTo(rise.multiply(BigDecimal.valueOf(2)))>=0?"CRITICAL":"WARNING";status="ANOMALY";}
            else if(pct.compareTo(fall.negate())<=0){direction="FALL";severity=pct.abs().compareTo(fall.multiply(BigDecimal.valueOf(2)))>=0?"CRITICAL":"WARNING";status="ANOMALY";}
        }
        saveResult(number,name,target,actual,avg,samples,diff,pct,direction,severity,status);
    }

    private void saveResult(String number,String name,LocalDate date,BigDecimal actual,BigDecimal avg,int samples,BigDecimal diff,BigDecimal pct,String direction,String severity,String status){
        jdbc.update("INSERT INTO energy_account_anomaly_result(account_number,account_name,bill_date,actual_amount,baseline_average,sample_days,difference_amount,difference_pct,direction,severity,analysis_status) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE account_name=VALUES(account_name),actual_amount=VALUES(actual_amount),baseline_average=VALUES(baseline_average),sample_days=VALUES(sample_days),difference_amount=VALUES(difference_amount),difference_pct=VALUES(difference_pct),direction=VALUES(direction),severity=VALUES(severity),analysis_status=VALUES(analysis_status),notified=IF(VALUES(analysis_status)='ANOMALY',notified,0)",number,name,date,actual,avg,samples,diff,pct,direction,severity,status);
    }

    public Map<String,Object> overview(LocalDate date){LocalDate target=date==null?LocalDate.now().minusDays(1):date;Map<String,Object> out=new LinkedHashMap<>();out.put("date",target);out.putAll(jdbc.queryForMap("SELECT COUNT(*) totalCount,SUM(analysis_status='NORMAL') normalCount,SUM(analysis_status='ANOMALY') anomalyCount,SUM(direction='RISE') riseCount,SUM(direction='FALL') fallCount,SUM(analysis_status='OBSERVING') observingCount,SUM(analysis_status='NO_DATA') noDataCount,MAX(CASE WHEN direction='RISE' THEN difference_pct END) maxRisePct,MIN(CASE WHEN direction='FALL' THEN difference_pct END) maxFallPct FROM energy_account_anomaly_result WHERE bill_date=?",target));out.put("running",running.get());return out;}

    public Map<String,Object> results(LocalDate date,String keyword,String status,String direction,int pageNum,int pageSize){LocalDate target=date==null?LocalDate.now().minusDays(1):date;int size=Math.min(Math.max(pageSize,1),100),offset=(Math.max(pageNum,1)-1)*size;StringBuilder where=new StringBuilder(" WHERE bill_date=?");List<Object> args=new ArrayList<>();args.add(target);if(keyword!=null&&!keyword.isBlank()){where.append(" AND (account_name LIKE ? OR account_number LIKE ?)");String k="%"+keyword.trim()+"%";args.add(k);args.add(k);}if(status!=null&&!status.isBlank()){where.append(" AND analysis_status=?");args.add(status);}if(direction!=null&&!direction.isBlank()){where.append(" AND direction=?");args.add(direction);}Long total=jdbc.queryForObject("SELECT COUNT(*) FROM energy_account_anomaly_result"+where,Long.class,args.toArray());List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(offset);List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,account_number accountNumber,account_name accountName,bill_date billDate,actual_amount actualAmount,baseline_average baselineAverage,sample_days sampleDays,difference_amount differenceAmount,difference_pct differencePct,direction,severity,analysis_status analysisStatus,handle_status handleStatus,handle_remark handleRemark,notified FROM energy_account_anomaly_result"+where+" ORDER BY CASE analysis_status WHEN 'ANOMALY' THEN 0 ELSE 1 END,ABS(COALESCE(difference_pct,0)) DESC LIMIT ? OFFSET ?",paged.toArray());return Map.of("records",rows,"total",total==null?0:total,"pageNum",Math.max(pageNum,1),"pageSize",size);}

    public List<Map<String,Object>> accounts(String keyword){String k=keyword==null?"":keyword.trim();String like="%"+k+"%";return jdbc.queryForList("SELECT p.account_number accountNumber,COALESCE(NULLIF(p.user_name,''),NULLIF(p.account_name,''),p.account_number) accountName FROM platform_account_current p WHERE (?='' OR p.account_number LIKE ? OR p.account_name LIKE ? OR p.user_name LIKE ?) UNION SELECT r.account_number accountNumber,MAX(COALESCE(NULLIF(r.account_name,''),r.account_number)) accountName FROM energy_account_anomaly_result r WHERE (?='' OR r.account_number LIKE ? OR r.account_name LIKE ?) GROUP BY r.account_number ORDER BY accountName LIMIT 80",k,like,like,like,k,like,like);}

    public Map<String,Object> trend(String accountNumber,LocalDate startDate,LocalDate endDate){
        if(accountNumber==null||accountNumber.isBlank())throw BizException.badRequest("请选择商户账户");
        LocalDate end=endDate==null?LocalDate.now().minusDays(1):endDate;
        LocalDate start=startDate==null?end.minusDays(89):startDate;
        if(start.isAfter(end))throw BizException.badRequest("开始日期不能晚于结束日期");
        if(start.plusDays(366).isBefore(end))throw BizException.badRequest("单次最多查询367天");
        List<Map<String,Object>> points=jdbc.queryForList("SELECT bill_date billDate,actual_amount actualAmount,baseline_average baselineAverage,difference_amount differenceAmount,difference_pct differencePct,sample_days sampleDays,direction,severity,analysis_status analysisStatus FROM energy_account_anomaly_result WHERE account_number=? AND bill_date BETWEEN ? AND ? ORDER BY bill_date",accountNumber.trim(),start,end);
        List<BigDecimal> rates=points.stream().map(p->p.get("differencePct")).filter(Objects::nonNull).map(v->decimal(v,BigDecimal.ZERO)).toList();
        List<BigDecimal> rises=rates.stream().filter(v->v.signum()>0).sorted().toList();
        List<BigDecimal> falls=rates.stream().filter(v->v.signum()<0).map(BigDecimal::abs).sorted().toList();
        Map<String,Object> rule=effectiveRule(accountNumber.trim());
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("recordDays",points.size());summary.put("validDays",rates.size());
        summary.put("averageRate",rates.isEmpty()?null:rates.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(rates.size()),2,RoundingMode.HALF_UP));
        summary.put("maxRisePct",rises.isEmpty()?null:rises.get(rises.size()-1));summary.put("maxFallPct",falls.isEmpty()?null:falls.get(falls.size()-1));
        summary.put("suggestedRisePct",percentile(rises,0.90));summary.put("suggestedFallPct",percentile(falls,0.90));
        String name=accounts(accountNumber.trim()).stream().filter(a->accountNumber.trim().equals(text(a.get("accountNumber")))).map(a->text(a.get("accountName"))).findFirst().orElse(accountNumber.trim());
        return Map.of("accountNumber",accountNumber.trim(),"accountName",name,"startDate",start,"endDate",end,"points",points,"summary",summary,"rule",rule);
    }

    public List<Map<String,Object>> rules(String keyword){String filter=keyword==null||keyword.isBlank()?"":" AND (p.account_number LIKE ? OR p.account_name LIKE ? OR p.user_name LIKE ?)";List<Object> args=new ArrayList<>();if(!filter.isEmpty()){String k="%"+keyword.trim()+"%";args.add(k);args.add(k);args.add(k);}return jdbc.queryForList("SELECT p.account_number accountNumber,COALESCE(NULLIF(p.user_name,''),NULLIF(p.account_name,''),p.account_number) accountName,COALESCE(r.enabled,1) enabled,r.rise_threshold_pct riseThresholdPct,r.fall_threshold_pct fallThresholdPct,r.minimum_difference minimumDifference,COALESCE(r.webhook_enabled,1) webhookEnabled,r.ignore_start_date ignoreStartDate,r.ignore_end_date ignoreEndDate,r.remark FROM platform_account_current p LEFT JOIN energy_account_anomaly_rule r ON r.account_number=p.account_number WHERE (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE'))"+filter+" ORDER BY accountName LIMIT 500",args.toArray());}

    public Map<String,Object> defaultRule(){return effectiveRule("__DEFAULT__");}
    public void saveRule(String number,Map<String,Object> body){if(number==null||number.isBlank())throw BizException.badRequest("账户不能为空");jdbc.update("INSERT INTO energy_account_anomaly_rule(account_number,enabled,rise_threshold_pct,fall_threshold_pct,minimum_difference,webhook_enabled,ignore_start_date,ignore_end_date,remark) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),rise_threshold_pct=VALUES(rise_threshold_pct),fall_threshold_pct=VALUES(fall_threshold_pct),minimum_difference=VALUES(minimum_difference),webhook_enabled=VALUES(webhook_enabled),ignore_start_date=VALUES(ignore_start_date),ignore_end_date=VALUES(ignore_end_date),remark=VALUES(remark)",number,bool(body.get("enabled"))?1:0,nullableDecimal(body.get("riseThresholdPct")),nullableDecimal(body.get("fallThresholdPct")),nullableDecimal(body.get("minimumDifference")),boolDefault(body.get("webhookEnabled"),true)?1:0,nullableDate(body.get("ignoreStartDate")),nullableDate(body.get("ignoreEndDate")),shortText(text(body.get("remark")),255));}
    public void handle(Long id,String status,String remark){if(!Set.of("PENDING","CONFIRMED_ANOMALY","CONFIRMED_NORMAL","IGNORED","RECOVERED").contains(status))throw BizException.badRequest("处理状态无效");String user=SecurityContextHolder.getContext().getAuthentication().getName();jdbc.update("UPDATE energy_account_anomaly_result SET handle_status=?,handle_remark=?,handled_by=?,handled_at=NOW(3) WHERE id=?",status,shortText(remark,500),user,id);}
    public List<Map<String,Object>> runs(){return jdbc.queryForList("SELECT id,trigger_type triggerType,status,target_date targetDate,account_count accountCount,success_count successCount,failed_count failedCount,anomaly_count anomalyCount,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_account_anomaly_run ORDER BY id DESC LIMIT 20");}

    private Map<String,Object> effectiveRule(String number){Map<String,Object> defaults=jdbc.queryForMap("SELECT enabled,rise_threshold_pct riseThresholdPct,fall_threshold_pct fallThresholdPct,minimum_difference minimumDifference,webhook_enabled webhookEnabled,ignore_start_date ignoreStartDate,ignore_end_date ignoreEndDate,remark FROM energy_account_anomaly_rule WHERE account_number='__DEFAULT__'");if("__DEFAULT__".equals(number))return defaults;List<Map<String,Object>> rows=jdbc.queryForList("SELECT enabled,rise_threshold_pct riseThresholdPct,fall_threshold_pct fallThresholdPct,minimum_difference minimumDifference,webhook_enabled webhookEnabled,ignore_start_date ignoreStartDate,ignore_end_date ignoreEndDate,remark FROM energy_account_anomaly_rule WHERE account_number=?",number);if(rows.isEmpty())return defaults;Map<String,Object> merged=new LinkedHashMap<>(defaults);rows.get(0).forEach((key,value)->{if(value!=null)merged.put(key,value);});return merged;}
    private boolean ignored(Map<String,Object> r,LocalDate date){LocalDate start=toDate(r.get("ignoreStartDate")),end=toDate(r.get("ignoreEndDate"));return start!=null&&end!=null&&!date.isBefore(start)&&!date.isAfter(end);}
    private String message(LocalDate date,List<Map<String,Object>> rows){StringBuilder b=new StringBuilder("### ⚠️ 计费账户异动提醒\n\n**账单日期**：").append(date).append("\n**异动账户**：").append(rows.size()).append(" 个\n\n---\n");int max=Math.min(rows.size(),30);for(int i=0;i<max;i++){Map<String,Object> r=rows.get(i);b.append("\n**").append(i+1).append(". ").append(text(r.get("account_name"))).append("**\n").append("- 30日平均：¥").append(money(r.get("baseline_average"))).append("\n- 当日账单：¥").append(money(r.get("actual_amount"))).append("\n- 变化：").append(decimal(r.get("difference_pct"),BigDecimal.ZERO)).append("%\n- 等级：").append("CRITICAL".equals(r.get("severity"))?"严重":"警告").append("\n");}return b.toString().trim();}
    private Map<String,Object> run(Long id){return jdbc.queryForMap("SELECT id,trigger_type triggerType,status,target_date targetDate,account_count accountCount,success_count successCount,failed_count failedCount,anomaly_count anomalyCount,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_account_anomaly_run WHERE id=?",id);}
    private String money(Object v){return decimal(v,BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private String text(Object v){return v==null?"":String.valueOf(v).trim();}
    private boolean bool(Object v){return v!=null&&(v.equals(Boolean.TRUE)||"1".equals(v.toString())||"true".equalsIgnoreCase(v.toString()));}
    private boolean boolDefault(Object v,boolean d){return v==null?d:bool(v);}
    private BigDecimal decimal(Object v,BigDecimal d){try{return v==null?d:new BigDecimal(v.toString());}catch(Exception e){return d;}}
    private BigDecimal nullableDecimal(Object v){return v==null||text(v).isBlank()?null:new BigDecimal(v.toString());}
    private BigDecimal percentile(List<BigDecimal> sorted,double percentile){if(sorted.isEmpty())return null;int index=(int)Math.ceil(sorted.size()*percentile)-1;return sorted.get(Math.max(0,Math.min(index,sorted.size()-1))).setScale(2,RoundingMode.HALF_UP);}
    private LocalDate nullableDate(Object v){return v==null||text(v).isBlank()?null:LocalDate.parse(v.toString());}
    private LocalDate toDate(Object v){if(v==null)return null;if(v instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(v.toString());}
    private String shortText(String v,int max){if(v==null)return null;return v.length()<=max?v:v.substring(0,max);}
}
