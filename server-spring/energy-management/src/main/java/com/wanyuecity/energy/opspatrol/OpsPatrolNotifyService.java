package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OpsPatrolNotifyService {
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    public OpsPatrolNotifyService(NotificationService notificationService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    public void pushTask(Map<String, Object> task, int itemCount) {
        Long configId = configIdOf(task.get("dept_id"));
        if (configId == null) { log.warn("ops patrol task {} not pushed: dept has no webhook", task.get("id")); return; }
        String dutyName = text(task.get("assignee_name"));
        StringBuilder content = new StringBuilder();
        content.append("### 📋 营运巡场 · ").append(text(task.get("slotName"))).append("（").append(text(task.get("deptName"))).append("）\n\n");
        content.append("**时间**：").append(time(task.get("plan_start_at"))).append(" - ").append(time(task.get("plan_end_at"))).append("\n");
        content.append("**值班干部**：").append(dutyName.isBlank() ? "未指定（当班干部填报）" : dutyName).append("\n");
        content.append("**检查项**：").append(itemCount).append(" 项\n");
        content.append("截止 ").append(time(task.get("plan_end_at"))).append("，未提交将记为逾期\n\n");
        content.append("[👉 开始填报](").append(baseUrl()).append("/ops-patrol/entry?token=").append(task.get("entry_token")).append(")");
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_TASK", content.toString());
    }

    public void pushHandover(Map<String, Object> task, Map<String, Object> handover) {
        Long configId = configIdOf(task.get("dept_id"));
        if (configId == null) { log.warn("ops patrol handover {} not pushed: dept has no webhook", task.get("id")); return; }
        Object unclosed = handover.getOrDefault("unclosedCount", handover.get("unclosed_count"));
        StringBuilder content = new StringBuilder();
        content.append("### 🔁 班次交接（").append(text(task.get("deptName"))).append("）\n\n");
        content.append("**交接时段**：").append(time(task.get("plan_start_at"))).append(" - ").append(time(task.get("plan_end_at"))).append("\n");
        content.append("**未闭环问题**：").append(unclosed == null ? 0 : unclosed).append(" 条\n");
        content.append("**本班检查**：合格 ").append(handover.getOrDefault("pass_count", 0)).append(" 项 / 不合格 ").append(handover.getOrDefault("fail_count", 0)).append(" 项\n\n");
        content.append("[👉 查看并确认交接](").append(baseUrl()).append("/ops-patrol/entry?token=").append(task.get("entry_token")).append(")");
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_HANDOVER", content.toString());
    }

    public void pushUrge(Map<String, Object> task, int remaining) {
        Long configId = configIdOf(task.get("dept_id"));
        if (configId == null) return;
        String dutyName = text(task.get("assignee_name"));
        String content = "### ⏰ 巡场任务未开始（" + text(task.get("deptName")) + "）\n\n"
                + "**时段**：" + text(task.get("slotName")) + "（截止 " + time(task.get("plan_end_at")) + "）\n"
                + "**值班干部**：" + (dutyName.isBlank() ? "当班干部" : dutyName) + "\n"
                + "**剩余检查项**：" + remaining + " 项\n\n"
                + "[👉 立即填报](" + baseUrl() + "/ops-patrol/entry?token=" + task.get("entry_token") + ")";
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_URGE", content);
    }

    public void pushRemind(Map<String, Object> task, int remaining) {
        Long configId = configIdOf(task.get("dept_id"));
        if (configId == null) return;
        String content = "### ⏳ 巡场任务即将截止（" + text(task.get("deptName")) + "）\n\n"
                + "**时段**：" + text(task.get("slotName")) + "（截止 " + time(task.get("plan_end_at")) + "）\n"
                + "**未完成项**：" + remaining + " 项\n\n"
                + "[👉 继续填报](" + baseUrl() + "/ops-patrol/entry?token=" + task.get("entry_token") + ")";
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_REMIND", content);
    }

    public void pushTaskOverdue(Map<String, Object> task) {
        Long configId = configIdOf(task.get("dept_id"));
        if (configId == null) return;
        String content = "### 🚨 巡场任务已逾期（" + text(task.get("deptName")) + "）\n\n"
                + "**时段**：" + text(task.get("slotName")) + "（" + time(task.get("plan_start_at")) + " - " + time(task.get("plan_end_at")) + "）\n"
                + "**值班干部**：" + (text(task.get("assignee_name")).isBlank() ? "未指定" : text(task.get("assignee_name"))) + "\n\n"
                + "仍可补录，但将计入逾期统计。\n"
                + "[👉 补录填报](" + baseUrl() + "/ops-patrol/entry?token=" + task.get("entry_token") + ")";
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_OVERDUE", content);
    }

    public void pushIssue(Map<String, Object> issue, String itemContent) {
        Long configId = configIdOf(issue.get("dept_id"));
        if (configId == null) return;
        StringBuilder content = new StringBuilder();
        content.append("### ⚠️ 营运问题上报（").append(text(issue.get("deptName"))).append("）\n\n");
        content.append("**店铺**：").append(storeText(issue)).append("\n");
        content.append("**检查项**：").append(shortText(itemContent, 80)).append("\n");
        content.append("**问题**：").append(shortText(text(issue.get("description")), 200)).append("\n");
        content.append("**分类**：").append(text(issue.get("category"))).append("　**紧急程度**：").append("URGENT".equals(text(issue.get("urgency"))) ? "紧急" : "一般").append("\n");
        content.append("**上报**：").append(text(issue.get("submitter_name"))).append("\n");
        content.append("**时限**：").append(dueText(issue));
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_ISSUE", content.toString());
    }

    public void pushIssueOverdue(Map<String, Object> issue) {
        Long configId = configIdOf(issue.get("dept_id"));
        if (configId == null) return;
        String content = "### ⏰ 营运问题已逾期（" + text(issue.get("deptName")) + "）\n\n"
                + "**店铺**：" + storeText(issue) + "\n"
                + "**问题**：" + shortText(text(issue.get("description")), 120) + "\n"
                + "**跟进人**：" + text(issue.get("owner_name")) + "\n"
                + "**时限**：" + dueText(issue) + "\n\n"
                + "请尽快完成整改并提交复核。";
        notificationService.sendToConfigs(List.of(configId), "OPS_PATROL_ISSUE_OVERDUE", content);
    }

    private Long configIdOf(Object deptId) {
        if (!(deptId instanceof Number n)) return null;
        List<Long> ids = jdbc.queryForList("SELECT webhook_config_id FROM ops_patrol_dept WHERE id=? AND webhook_config_id IS NOT NULL", Long.class, n.longValue());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String storeText(Map<String, Object> issue) {
        String merchant = text(issue.get("merchant_name"));
        String stall = text(issue.get("stall_code"));
        String floor = text(issue.get("floor_name"));
        if (merchant.isBlank()) return "未关联店铺";
        StringBuilder sb = new StringBuilder(merchant);
        if (!stall.isBlank()) sb.append("（").append(stall).append("）");
        if (!floor.isBlank()) sb.append(" · ").append(floor);
        return sb.toString();
    }

    private String dueText(Map<String, Object> issue) {
        LocalDateTime due = toLocal(issue.get("due_at"));
        if (due == null) return "未设置";
        LocalDateTime now = LocalDateTime.now();
        if (due.isBefore(now)) {
            long hours = Math.max(1, java.time.Duration.between(due, now).toHours());
            return due.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "（已逾期 " + hours + " 小时）";
        }
        long minutes = java.time.Duration.between(now, due).toMinutes();
        return due.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) + "（剩余 " + (minutes / 60) + " 小时 " + (minutes % 60) + " 分）";
    }

    private String time(Object value) {
        LocalDateTime t = toLocal(value);
        return t == null ? "-" : t.format(MINUTE);
    }

    private LocalDateTime toLocal(Object value) {
        if (value instanceof LocalDateTime t) return t;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private String shortText(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private String baseUrl() { return publicBaseUrl.replaceAll("/$", ""); }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
}
