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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class ExceptionAccountService {
    public static final String EVENT = "ENERGY_ACCOUNT_EXCEPTION";
    private static final java.time.format.DateTimeFormatter MESSAGE_TIME = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final PlatformConfigService configService;
    private final PlatformAdapter adapter;
    private final NotificationService notificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Map<String, Object> sync(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw BizException.badRequest("异常账户同步正在执行，请稍后再试");
        }
        Long runId = null;
        try {
            Map<String, Object> setting = getSetting();
            if (!bool(setting.get("enabled"))) {
                return Map.of("skipped", true, "reason", "disabled");
            }
            if ("SCHEDULED".equals(triggerType) && !due(setting)) {
                return Map.of("skipped", true, "reason", "not_due");
            }

            jdbc.update("INSERT INTO energy_exception_sync_run(trigger_type,status) VALUES(?,'RUNNING')", triggerType);
            runId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            PlatformConfigService.PlatformConnection connection = configService.connect();
            List<PlatformAccount> accounts = adapter.fetchExceptionAccounts(connection.baseUrl(), connection.token(), connection.spaceId());
            accounts = accounts.stream()
                    .filter(a -> a.getAccountNumber() != null && !a.getAccountNumber().isBlank())
                    .toList();

            LocalDateTime now = LocalDateTime.now();
            boolean quiet = inQuietHours(setting, now.toLocalTime());

            jdbc.update("UPDATE energy_exception_account SET active=0");
            for (PlatformAccount a : accounts) upsert(a, now);

            List<Map<String, Object>> active = jdbc.queryForList(
                    "SELECT account_number, account_name, user_name, mobile_phone, balance, exception_type, notified, first_seen_at " +
                            "FROM energy_exception_account WHERE active=1 AND excluded=0 ORDER BY first_seen_at, account_number");
            Integer excluded = jdbc.queryForObject("SELECT COUNT(*) FROM energy_exception_account WHERE active=1 AND excluded=1", Integer.class);

            // 标记需要通知的（未通知过的）
            List<Map<String, Object>> toNotify = active.stream()
                    .filter(r -> !"1".equals(String.valueOf(r.get("notified"))))
                    .toList();
            if (!toNotify.isEmpty()) {
                List<String> nums = toNotify.stream().map(r -> String.valueOf(r.get("account_number"))).toList();
                if (!nums.isEmpty()) {
                    String placeholders = String.join(",", Collections.nCopies(nums.size(), "?"));
                    jdbc.update("UPDATE energy_exception_account SET notified=1 WHERE account_number IN (" + placeholders + ")", nums.toArray());
                }
                if (!quiet) {
                    notificationService.notifyConfiguredEvent(EVENT, message(toNotify, now));
                }
            }

            // 恢复：之前异常但本次未拉到的账户
            jdbc.update("UPDATE energy_exception_account SET recovered_at=? WHERE active=0 AND recovered_at IS NULL", now);

            jdbc.update("UPDATE energy_exception_sync_run SET status='SUCCESS',fetched_count=?,excluded_count=?,notified_count=?,completed_at=NOW(3) WHERE id=?",
                    accounts.size(), excluded == null ? 0 : excluded, toNotify.size(), runId);
            return runRow(runId);
        } catch (Exception ex) {
            log.error("exception account sync failed, trigger={}, error={}", triggerType, ex.getMessage(), ex);
            if (runId != null) {
                jdbc.update("UPDATE energy_exception_sync_run SET status='FAILED',error_message=?,completed_at=NOW(3) WHERE id=?",
                        shortText(ex.getMessage(), 500), runId);
            }
            throw ex instanceof BizException b ? b : BizException.badRequest("异常账户同步失败：" + shortText(ex.getMessage(), 180));
        } finally {
            running.set(false);
        }
    }

    public Map<String, Object> accounts(String keyword, Boolean excluded, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100), offset = (Math.max(pageNum, 1) - 1) * size;
        StringBuilder where = new StringBuilder(" WHERE active=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (account_number LIKE ? OR account_name LIKE ? OR user_name LIKE ? OR mobile_phone LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            Collections.addAll(args, k, k, k, k);
        }
        if (excluded != null) { where.append(" AND excluded=?"); args.add(excluded ? 1 : 0); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM energy_exception_account" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT account_number accountNumber, platform_account_id platformAccountId, account_name accountName, " +
                        "user_name userName, mobile_phone mobilePhone, balance, exception_type exceptionType, " +
                        "excluded, exclusion_remark exclusionRemark, first_seen_at firstSeenAt, last_seen_at lastSeenAt, " +
                        "recovered_at recoveredAt, updated_at updatedAt " +
                        "FROM energy_exception_account" + where + " ORDER BY first_seen_at DESC, account_number LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", rows);
        out.put("total", total == null ? 0 : total);
        out.put("pageNum", Math.max(pageNum, 1));
        out.put("pageSize", size);
        out.put("running", running.get());
        return out;
    }

    public List<Map<String, Object>> runs() {
        return jdbc.queryForList("SELECT id, trigger_type triggerType, status, fetched_count fetchedCount, " +
                "excluded_count excludedCount, notified_count notifiedCount, error_message errorMessage, " +
                "started_at startedAt, completed_at completedAt FROM energy_exception_sync_run ORDER BY id DESC LIMIT 20");
    }

    public void setExcluded(String accountNumber, boolean excluded, String remark) {
        int changed = jdbc.update("UPDATE energy_exception_account SET excluded=?,exclusion_remark=? WHERE account_number=?",
                excluded ? 1 : 0, excluded ? shortText(remark, 255) : null, accountNumber);
        if (changed == 0) throw BizException.notFound("异常账户不存在，请先同步");
    }

    public Map<String, Object> getSetting() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled, check_interval_minutes checkIntervalMinutes, quiet_start quietStart, quiet_end quietEnd " +
                        "FROM energy_exception_monitor_setting WHERE id=1");
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO energy_exception_monitor_setting(id) VALUES(1) ON DUPLICATE KEY UPDATE id=id");
            return getSetting();
        }
        return rows.get(0);
    }

    public void updateSetting(Map<String, Object> body) {
        jdbc.update("UPDATE energy_exception_monitor_setting SET enabled=?, check_interval_minutes=?, quiet_start=?, quiet_end=? WHERE id=1",
                bool(body.get("enabled")) ? 1 : 0,
                num(body.get("checkIntervalMinutes"), 120),
                body.get("quietStart"), body.get("quietEnd"));
    }

    private void upsert(PlatformAccount a, LocalDateTime now) {
        jdbc.update("INSERT INTO energy_exception_account(account_number,platform_account_id,account_name,user_name,mobile_phone,balance,exception_type,active,last_seen_at) " +
                        "VALUES(?,?,?,?,?,?,?,1,?) ON DUPLICATE KEY UPDATE platform_account_id=VALUES(platform_account_id),account_name=VALUES(account_name)," +
                        "user_name=VALUES(user_name),mobile_phone=VALUES(mobile_phone),balance=VALUES(balance),exception_type=VALUES(exception_type)," +
                        "active=1,last_seen_at=VALUES(last_seen_at),recovered_at=NULL",
                a.getAccountNumber(), a.getPlatformAccountId(), a.getAccountName(), a.getUserName(),
                a.getMobilePhone(), a.getBalance(), a.getExceptionType(), now);
    }

    private boolean due(Map<String, Object> setting) {
        int interval = num(setting.get("check_interval_minutes"), 120);
        List<Map<String, Object>> last = jdbc.queryForList(
                "SELECT started_at startedAt FROM energy_exception_sync_run WHERE status='SUCCESS' ORDER BY id DESC LIMIT 1");
        if (last.isEmpty()) return true;
        Object started = last.get(0).get("startedAt");
        if (!(started instanceof LocalDateTime t)) return true;
        return t.plusMinutes(interval).isBefore(LocalDateTime.now());
    }

    private boolean inQuietHours(Map<String, Object> setting, LocalTime now) {
        LocalTime start = toLocalTime(setting.get("quietStart"));
        LocalTime end = toLocalTime(setting.get("quietEnd"));
        if (start == null || end == null) return false;
        if (start.equals(end)) return false;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end);
    }

    private LocalTime toLocalTime(Object v) {
        if (v instanceof LocalTime t) return t;
        if (v instanceof java.sql.Time t) return t.toLocalTime();
        if (v instanceof String s && !s.isBlank()) {
            try { return LocalTime.parse(s.length() == 5 ? s + ":00" : s); } catch (Exception ignored) {}
        }
        return null;
    }

    private String message(List<Map<String, Object>> rows, LocalDateTime now) {
        StringBuilder b = new StringBuilder("### ⚠️ 水电费账户异常通知\n\n");
        b.append("**通知时间**：").append(now == null ? "-" : now.format(MESSAGE_TIME)).append("\n");
        b.append("**异常账户**：").append(rows.size()).append(" 个\n\n---\n");
        int max = Math.min(rows.size(), 30);
        for (int i = 0; i < max; i++) {
            Map<String, Object> r = rows.get(i);
            String name = text(r.get("user_name"));
            if (name.isBlank()) name = text(r.get("account_name"));
            if (name.isBlank()) name = "未命名账户";
            b.append("**").append(i + 1).append(". ").append(name).append("**\n");
            b.append("- 账户号：").append(text(r.get("account_number"))).append("\n");
            Object et = r.get("exception_type");
            if (et != null && !text(et).isBlank()) b.append("- 异常类型：").append(text(et)).append("\n");
            Object bal = r.get("balance");
            if (bal != null) b.append("- 余额：¥").append(bal).append("\n");
        }
        if (rows.size() > max) b.append("\n> 其余 ").append(rows.size() - max).append(" 个异常账户未展开。\n");
        return b.toString().trim();
    }

    private Map<String, Object> runRow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, trigger_type triggerType, status, fetched_count fetchedCount, excluded_count excludedCount, " +
                        "notified_count notifiedCount, error_message errorMessage, started_at startedAt, completed_at completedAt " +
                        "FROM energy_exception_sync_run WHERE id=?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }
    private int num(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }
    private String text(Object v) { return v == null ? "" : v.toString().trim(); }
    private String shortText(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
