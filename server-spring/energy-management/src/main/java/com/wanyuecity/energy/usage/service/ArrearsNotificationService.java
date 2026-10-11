package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class ArrearsNotificationService {
    public static final String EVENT = "ENERGY_ARREARS_NOTICE";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    private final PlatformConfigService configService;
    private final PlatformAdapter adapter;
    private final NotificationService notificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Map<String, Object> sync(String triggerType) {
        if (!running.compareAndSet(false, true)) throw BizException.badRequest("欠费账户同步正在执行，请稍后再试");
        Long runId = null;
        try {
            jdbc.update("INSERT INTO energy_arrears_sync_run(trigger_type,status) VALUES(?,'RUNNING')", triggerType);
            runId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            PlatformConfigService.PlatformConnection connection = configService.connect();
            List<PlatformAccount> accounts = adapter.fetchArrearsAccounts(connection.baseUrl(), connection.token(), connection.spaceId());
            accounts = accounts.stream()
                    .filter(account -> account.getAccountNumber() != null && !account.getAccountNumber().isBlank())
                    .toList();
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("UPDATE energy_arrears_account SET active=0");
            for (PlatformAccount account : accounts) upsert(account, now);
            List<Map<String,Object>> notified = jdbc.queryForList("SELECT account_number,account_name,user_name,mobile_phone,balance,arrears FROM energy_arrears_account WHERE active=1 AND excluded=0 ORDER BY arrears DESC,account_number");
            Integer excluded = jdbc.queryForObject("SELECT COUNT(*) FROM energy_arrears_account WHERE active=1 AND excluded=1", Integer.class);
            BigDecimal total = notified.stream().map(row -> decimal(row.get("arrears"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!notified.isEmpty()) notificationService.notifyConfiguredEvent(EVENT, message(notified, excluded == null ? 0 : excluded, total, now));
            jdbc.update("UPDATE energy_arrears_sync_run SET status='SUCCESS',fetched_count=?,excluded_count=?,notified_count=?,total_arrears=?,completed_at=NOW(3) WHERE id=?",
                    accounts.size(), excluded == null ? 0 : excluded, notified.size(), total, runId);
            return run(runId);
        } catch (Exception ex) {
            log.error("arrears account sync failed, trigger={}, error={}", triggerType, ex.getMessage(), ex);
            if (runId != null) jdbc.update("UPDATE energy_arrears_sync_run SET status='FAILED',error_message=?,completed_at=NOW(3) WHERE id=?", shortText(ex.getMessage(), 500), runId);
            throw ex instanceof BizException b ? b : BizException.badRequest("欠费账户同步失败：" + shortText(ex.getMessage(), 180));
        } finally {
            running.set(false);
        }
    }

    public Map<String,Object> accounts(String keyword, Boolean excluded, int pageNum, int pageSize) {
        int size=Math.min(Math.max(pageSize,1),100), offset=(Math.max(pageNum,1)-1)*size;
        StringBuilder where=new StringBuilder(" WHERE active=1");List<Object> args=new ArrayList<>();
        if(keyword!=null&&!keyword.isBlank()){where.append(" AND (account_number LIKE ? OR account_name LIKE ? OR user_name LIKE ? OR mobile_phone LIKE ?)");String k="%"+keyword.trim()+"%";Collections.addAll(args,k,k,k,k);}
        if(excluded!=null){where.append(" AND excluded=?");args.add(excluded?1:0);}
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM energy_arrears_account"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(size);pageArgs.add(offset);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT account_number accountNumber,platform_account_id platformAccountId,account_name accountName,user_name userName,mobile_phone mobilePhone,balance,arrears,excluded,exclusion_remark exclusionRemark,last_seen_at lastSeenAt,updated_at updatedAt FROM energy_arrears_account"+where+" ORDER BY arrears DESC,account_number LIMIT ? OFFSET ?",pageArgs.toArray());
        Map<String,Object> out=new LinkedHashMap<>();out.put("records",rows);out.put("total",total==null?0:total);out.put("pageNum",Math.max(pageNum,1));out.put("pageSize",size);out.put("running",running.get());return out;
    }

    public List<Map<String,Object>> runs(){return jdbc.queryForList("SELECT id,trigger_type triggerType,status,fetched_count fetchedCount,excluded_count excludedCount,notified_count notifiedCount,total_arrears totalArrears,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_arrears_sync_run ORDER BY id DESC LIMIT 20");}

    public void setExcluded(String accountNumber, boolean excluded, String remark) {
        int changed=jdbc.update("UPDATE energy_arrears_account SET excluded=?,exclusion_remark=? WHERE account_number=?",excluded?1:0,excluded?shortText(remark,255):null,accountNumber);
        if(changed==0)throw BizException.notFound("欠费账户不存在，请先同步");
    }

    private void upsert(PlatformAccount a, LocalDateTime now){
        jdbc.update("INSERT INTO energy_arrears_account(account_number,platform_account_id,account_name,user_name,mobile_phone,balance,arrears,active,last_seen_at) VALUES(?,?,?,?,?,?,?,1,?) ON DUPLICATE KEY UPDATE platform_account_id=VALUES(platform_account_id),account_name=VALUES(account_name),user_name=VALUES(user_name),mobile_phone=VALUES(mobile_phone),balance=VALUES(balance),arrears=VALUES(arrears),active=1,last_seen_at=VALUES(last_seen_at)",
                a.getAccountNumber(),a.getPlatformAccountId(),a.getAccountName(),a.getUserName(),a.getMobilePhone(),a.getBalance(),a.getArrears(),now);
    }

    private Map<String,Object> run(Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,trigger_type triggerType,status,fetched_count fetchedCount,excluded_count excludedCount,notified_count notifiedCount,total_arrears totalArrears,error_message errorMessage,started_at startedAt,completed_at completedAt FROM energy_arrears_sync_run WHERE id=?",id);return rows.isEmpty()?Map.of():rows.get(0);}
    private String message(List<Map<String,Object>> rows,int excluded,BigDecimal total,LocalDateTime now){StringBuilder b=new StringBuilder("### ⚠️ 能源欠费账户通知\n\n");b.append("**获取时间**：").append(now.format(TIME)).append("\n**欠费账户**：").append(rows.size()).append(" 个\n**欠费合计**：").append(total.stripTrailingZeros().toPlainString()).append(" 元\n");if(excluded>0)b.append("**已设置不推送**：").append(excluded).append(" 个\n");b.append("\n");int max=Math.min(rows.size(),30);for(int i=0;i<max;i++){Map<String,Object> r=rows.get(i);String name=text(r.get("user_name"));if(name.isBlank())name=text(r.get("account_name"));b.append(i+1).append(". **").append(name.isBlank()?r.get("account_number"):name).append("**\n   账户：").append(r.get("account_number")).append("　欠费：").append(decimal(r.get("arrears")).stripTrailingZeros().toPlainString()).append(" 元\n");}if(rows.size()>max)b.append("\n> 另有 ").append(rows.size()-max).append(" 个欠费账户，请进入系统查看。\n");return b.append("\n[👉 查看欠费通知](https://auto.apperload.com/energy/arrears)").toString();}
    private BigDecimal decimal(Object value){if(value==null)return BigDecimal.ZERO;return value instanceof BigDecimal b?b:new BigDecimal(value.toString());}
    private String text(Object value){return value==null?"":value.toString().trim();}
    private String shortText(String value,int max){if(value==null)return null;return value.length()<=max?value:value.substring(0,max);}
}
