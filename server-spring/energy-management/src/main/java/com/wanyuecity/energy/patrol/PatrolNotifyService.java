package com.wanyuecity.energy.patrol;

import com.wanyuecity.energy.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class PatrolNotifyService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM月dd日");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    public PatrolNotifyService(NotificationService notificationService, JdbcTemplate jdbc) {
        this.notificationService = notificationService;
        this.jdbc = jdbc;
    }

    public void pushDailyEntry(LocalDate date, String dutyName, List<Map<String, Object>> points, String entryToken) {
        List<Long> configIds = patrolGroupConfigIds();
        if (configIds.isEmpty()) {
            log.warn("patrol daily entry skipped: no PATROL_DAILY_ENTRY webhook configured");
            return;
        }
        StringBuilder content = new StringBuilder("### 🗒 今日巡场 · ").append(date.format(DAY)).append("\n\n");
        content.append("**值班干部**：").append(dutyName == null || dutyName.isBlank() ? "物业管理员" : dutyName).append("\n");
        content.append("**巡检点位**：").append(points.size()).append(" 个\n");
        if (!points.isEmpty()) {
            content.append("**区域**：").append(areaSummary(points)).append("\n");
        }
        content.append("请在今日 20:00 前完成巡查\n\n");
        content.append("[👉 点击进入巡场](").append(baseUrl()).append("/patrol/entry?token=").append(entryToken).append(")");
        notificationService.sendToConfigs(configIds, "PATROL_DAILY_ENTRY", content.toString());
    }

    /** 异常上报即推送（含现场照片），与部门分派推送是两个独立事件 */
    public void pushIssueSubmitted(Map<String, Object> issue, Map<String, Object> department) {
        List<Long> configIds = jdbc.queryForList(
                "SELECT id FROM webhook_config WHERE enabled=1 AND event_types LIKE '%PATROL_ISSUE_SUBMIT%' ORDER BY id", Long.class);
        if (configIds.isEmpty()) return;
        StringBuilder content = new StringBuilder("### 🚨 物业巡场异常上报\n\n");
        content.append("> 问题编号：").append(text(issue.get("issueNo"))).append("\n\n");
        content.append("**区域**：").append(areaText(issue)).append("\n");
        content.append("**点位**：").append(text(issue.get("pointName"))).append("\n");
        content.append("**分类**：").append(text(issue.get("category"))).append("\n");
        content.append("**问题**：").append(descriptionText(issue)).append("\n");
        content.append("**上报**：").append(text(issue.get("submitterName"))).append("　").append(format(issue.get("submittedAt"))).append("\n");
        content.append("**整改部门**：").append(department == null ? "待分派" : text(department.get("name"))).append("\n");

        List<Long> attachmentIds = photoIds(issue.get("submissionId"), 6);
        List<String> imageUrls = attachmentUrls(issue.get("feedbackToken"), attachmentIds);
        if (!attachmentIds.isEmpty()) {
            content.append("**现场照片**：").append(attachmentIds.size()).append(" 张\n");
        }
        content.append("\n[👉 分配整改部门](").append(baseUrl()).append("/patrol/issues?assign=").append(issue.get("id")).append(")\n");
        content.append("[👉 立即整改](").append(baseUrl()).append("/patrol/i/").append(issue.get("feedbackToken")).append(")");
        String body = content.toString();
        // 异步推送，避免阻塞 H5 提交响应
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendToConfigs(configIds, "PATROL_ISSUE_SUBMIT", body, imageUrls);
            } catch (Exception ex) {
                log.warn("patrol issue {} submit-notify failed: {}", issue.get("issueNo"), ex.getMessage());
            }
        });
    }

    /** 特殊区域巡检正常：无需整改，仅向巡场群推送留档照片 */
    public void pushSpecialNormal(Map<String, Object> point, String submitterName, Long submissionId, String photoToken, LocalDateTime submittedAt) {
        List<Long> configIds = specialNormalConfigIds();
        if (configIds.isEmpty()) {
            log.warn("patrol special normal skipped: no PATROL_SPECIAL_NORMAL/PATROL_DAILY_ENTRY webhook configured");
            return;
        }
        StringBuilder content = new StringBuilder("### ✅ 特殊区域巡检正常\n\n");
        content.append("**区域**：").append(text(point.get("area"))).append("\n");
        content.append("**点位**：").append(text(point.get("name"))).append("\n");
        String floor = text(point.get("floor"));
        if (!floor.isBlank()) content.append("**楼层**：").append(floor).append("\n");
        content.append("**结果**：正常 · 无需整改\n");
        content.append("**上报**：").append(text(submitterName)).append("　").append(format(submittedAt)).append("\n");
        List<Long> attachmentIds = photoIds(submissionId, 6);
        List<String> imageUrls = attachmentUrls(photoToken, attachmentIds);
        if (!attachmentIds.isEmpty()) content.append("**现场照片**：").append(attachmentIds.size()).append(" 张");
        String body = content.toString();
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendToConfigs(configIds, "PATROL_SPECIAL_NORMAL", body, imageUrls);
            } catch (Exception ex) {
                log.warn("patrol special normal notify failed: {}", ex.getMessage());
            }
        });
    }

    public void pushIssue(Map<String, Object> issue, Map<String, Object> department, boolean transfer, String fromDepartmentName) {
        Collection<Long> configIds = configIdsOf(department);
        if (configIds.isEmpty()) {
            log.warn("patrol issue {} not pushed: department {} has no webhook", issue.get("issueNo"), department.get("name"));
            return;
        }
        StringBuilder content = new StringBuilder();
        if (transfer) {
            content.append("### 🔁 巡场异常 · 已转派（").append(department.get("name")).append("）\n\n");
            content.append("> 原整改部门：").append(fromDepartmentName == null ? "-" : fromDepartmentName).append("\n\n");
        } else {
            content.append("### 🚨 巡场异常 · 待整改（").append(department.get("name")).append("）\n\n");
        }
        content.append("**区域**：").append(areaText(issue)).append("\n");
        content.append("**点位**：").append(text(issue.get("pointName"))).append("\n");
        content.append("**问题**：").append(shortText(descriptionText(issue), 200)).append("\n");
        content.append("**分类**：").append(text(issue.get("category"))).append("\n");
        content.append("**上报**：").append(text(issue.get("submitterName"))).append("　").append(format(issue.get("submittedAt"))).append("\n");
        content.append("**时限**：").append(dueText(issue)).append("\n");

        List<Long> attachmentIds = photoIds(issue.get("submissionId"));
        List<String> imageUrls = attachmentUrls(issue.get("feedbackToken"), attachmentIds);
        if (!attachmentIds.isEmpty()) {
            content.append("**现场照片**：").append(attachmentIds.size()).append(" 张\n");
        }
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE department_id=? AND status IN ('ASSIGNED','RECTIFYING','REVIEWING')",
                Long.class, department.get("id"));
        content.append("\n[👉 立即整改](").append(baseUrl()).append("/patrol/i/").append(issue.get("feedbackToken")).append(")\n");
        content.append("[👉 本部门今日待处理 ").append(pending == null ? 0 : pending).append(" 条](").append(baseUrl()).append("/patrol/d/")
                .append(department.get("accessToken")).append(")");
        notificationService.sendToConfigs(configIds, transfer ? "PATROL_ISSUE_TRANSFER" : "PATROL_ISSUE_DISPATCH", content.toString(), imageUrls);
    }

    public void pushUnassignedReminder(LocalDate date, List<Map<String, Object>> issues) {
        if (issues.isEmpty()) return;
        List<Long> configIds = patrolGroupConfigIds();
        if (configIds.isEmpty()) return;
        StringBuilder content = new StringBuilder("### ⏰ 待分派提醒 · ").append(date.format(DAY)).append("\n\n");
        content.append("> 共有 **").append(issues.size()).append("** 条巡场异常尚未指定整改部门，请物业管理员尽快分派。\n\n");
        int max = Math.min(issues.size(), 10);
        for (int i = 0; i < max; i++) {
            Map<String, Object> issue = issues.get(i);
            content.append(i + 1).append(". **").append(text(issue.get("pointName"))).append("**（").append(text(issue.get("area")))
                    .append("）　").append(shortText(text(issue.get("description")), 60)).append("\n");
        }
        if (issues.size() > max) content.append("\n> 另有 ").append(issues.size() - max).append(" 条，请进入问题台账处理。\n");
        content.append("\n[👉 打开巡场问题台账](").append(baseUrl()).append("/patrol/issues?status=UNASSIGNED)");
        notificationService.sendToConfigs(configIds, "PATROL_ISSUE_UNASSIGNED", content.toString());
    }

    public void pushOverdue(Map<String, Object> issue, Map<String, Object> department) {
        Collection<Long> configIds = configIdsOf(department);
        if (configIds.isEmpty()) return;
        String content = "### ⏰ 巡场异常已逾期（" + text(department.get("name")) + "）\n\n" +
                "**区域**：" + areaText(issue) + "\n" +
                "**点位**：" + text(issue.get("pointName")) + "\n" +
                "**问题**：" + shortText(text(issue.get("description")), 120) + "\n" +
                "**时限**：" + dueText(issue) + "\n\n" +
                "[👉 立即整改](" + baseUrl() + "/patrol/i/" + issue.get("feedbackToken") + ")\n" +
                "[👉 本部门今日待处理](" + baseUrl() + "/patrol/d/" + department.get("accessToken") + ")";
        notificationService.sendToConfigs(configIds, "PATROL_ISSUE_OVERDUE", content);
    }

    private List<Long> patrolGroupConfigIds() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM webhook_config WHERE enabled=1 AND event_types LIKE '%PATROL_DAILY_ENTRY%' ORDER BY id", Long.class);
        if (!ids.isEmpty()) return ids;
        return jdbc.queryForList("SELECT id FROM webhook_config WHERE enabled=1 AND event_types LIKE '%PATROL_ISSUE_DISPATCH%' ORDER BY id", Long.class);
    }

    /**
     * 特殊区域正常留档：订阅 PATROL_SPECIAL_NORMAL 的群（飞书/企微/钉钉均可）。
     * 兼容历史配置——早期该事件不可勾选，只能靠 PATROL_DAILY_ENTRY 命中，故做并集。
     */
    private List<Long> specialNormalConfigIds() {
        return jdbc.queryForList("SELECT id FROM webhook_config WHERE enabled=1 "
                + "AND (event_types LIKE '%PATROL_SPECIAL_NORMAL%' OR event_types LIKE '%PATROL_DAILY_ENTRY%') ORDER BY id", Long.class);
    }

    private Collection<Long> configIdsOf(Map<String, Object> department) {
        List<Long> ids = new ArrayList<>();
        Object primary = department.get("webhookConfigId");
        if (primary instanceof Number number && number.longValue() > 0) ids.add(number.longValue());
        Object cc = department.get("ccWebhookIds");
        if (cc != null) {
            for (String part : cc.toString().split(",")) {
                try {
                    if (!part.isBlank()) ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) { }
            }
        }
        return ids;
    }

    private List<Long> photoIds(Object submissionId) {
        return photoIds(submissionId, 6);
    }

    /** 现场照片公网地址（供企业微信发图 / 飞书按钮使用） */
    private List<String> attachmentUrls(Object feedbackToken, List<Long> attachmentIds) {
        List<String> urls = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            urls.add(baseUrl() + "/api/v1/patrol-public/attachments/" + feedbackToken + "/" + attachmentId);
        }
        return urls;
    }

    private List<Long> photoIds(Object submissionId, int limit) {
        if (!(submissionId instanceof Number number)) return List.of();
        return jdbc.queryForList("SELECT id FROM patrol_attachment WHERE business_type='SUBMISSION' AND business_id=? ORDER BY id LIMIT " + limit,
                Long.class, number.longValue());
    }

    private String areaSummary(List<Map<String, Object>> points) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> row : points) {
            String area = text(row.get("area"));
            boolean exists = parts.stream().anyMatch(p -> p.startsWith(area + " "));
            if (!exists) {
                long count = points.stream().filter(p -> area.equals(text(p.get("area")))).count();
                parts.add(area + " " + count);
            }
        }
        return String.join(" / ", parts);
    }

    private String areaText(Map<String, Object> issue) {
        String area = text(issue.get("area"));
        String floor = text(issue.get("floor"));
        return floor.isBlank() ? area : area + " · " + floor;
    }

    private String descriptionText(Map<String, Object> issue) {
        String description = text(issue.get("description"));
        return description.isBlank() ? "（未填写问题描述）" : description;
    }

    private String dueText(Map<String, Object> issue) {
        LocalDateTime dueTime = toLocal(issue.get("dueAt"));
        if (dueTime == null) return "未设置";
        LocalDateTime now = LocalDateTime.now();
        if (dueTime.isBefore(now)) {
            long hours = Math.max(1, java.time.Duration.between(dueTime, now).toHours());
            return dueTime.format(MINUTE) + "（已逾期 " + hours + " 小时）";
        }
        long minutes = java.time.Duration.between(now, dueTime).toMinutes();
        return dueTime.format(MINUTE) + "（剩余 " + (minutes / 60) + " 小时 " + (minutes % 60) + " 分）";
    }

    private String format(Object value) {
        LocalDateTime time = toLocal(value);
        return time == null ? "-" : time.format(MINUTE);
    }

    private LocalDateTime toLocal(Object value) {
        if (value instanceof LocalDateTime local) return local;
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
