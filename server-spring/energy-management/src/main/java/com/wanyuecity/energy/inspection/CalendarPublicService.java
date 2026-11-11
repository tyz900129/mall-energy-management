package com.wanyuecity.energy.inspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.core.SimpleRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日历任务移动回执：token 只对应一条任务，对外字段做脱敏。
 * 不提供列表/枚举接口，避免通过 token 遍历任务。
 */
@Service
@RequiredArgsConstructor
public class CalendarPublicService {

    private static final int MAX_PHOTOS = 9;
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> CLOSED = Set.of("COMPLETED", "CLOSED", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final InspectionAttachmentService attachments;
    private final SimpleRateLimiter rateLimiter;

    public Map<String,Object> detail(String token, String ip) {
        rateLimiter.check("calendar-public:view:" + ip, 60, Duration.ofMinutes(10), "访问过于频繁，请稍后再试");
        return view(requireTask(token));
    }

    @Transactional
    public Map<String,Object> upload(String token, MultipartFile file, String uploader, String ip) {
        rateLimiter.check("calendar-public:upload:" + ip, 30, Duration.ofMinutes(10), "上传过于频繁，请稍后再试");
        Map<String,Object> task = requireTask(token);
        if (CLOSED.contains(String.valueOf(task.get("status")))) throw BizException.badRequest("该任务已结束，不能继续上传照片");
        if (photoRows(taskId(task)).size() >= MAX_PHOTOS) throw BizException.badRequest("最多上传" + MAX_PHOTOS + "张照片");
        attachments.upload(taskId(task), "RECEIPT", file, cleanName(uploader));
        return view(requireTask(token));
    }

    public InspectionAttachmentService.ResourceContent photo(String token, Long photoId, String ip) {
        rateLimiter.check("calendar-public:photo:" + ip, 120, Duration.ofMinutes(10), "访问过于频繁，请稍后再试");
        Map<String,Object> task = requireTask(token);
        return attachments.read(taskId(task), photoId);
    }

    @Transactional
    public Map<String,Object> submit(String token, Map<String,Object> body, String ip, String userAgent) {
        rateLimiter.check("calendar-public:submit:" + ip, 10, Duration.ofMinutes(10), "提交过于频繁，请稍后再试");
        Map<String,Object> task = requireTask(token);
        Long id = taskId(task);
        if (CLOSED.contains(String.valueOf(task.get("status")))) {
            if (task.get("submitted_at") != null) return view(task);
            throw BizException.badRequest("该任务已结束，不能提交回执");
        }

        String name = text(body, "name");
        if (name.isBlank()) throw BizException.badRequest("请填写回执人姓名");
        if (name.length() > 32) throw BizException.badRequest("回执人姓名过长");
        String phone = text(body, "phone");
        if (!phone.isBlank() && !PHONE.matcher(phone).matches()) throw BizException.badRequest("手机号格式不正确");
        if (intValue(task.get("requirePhoto"), 0) == 1 && photoRows(id).isEmpty())
            throw BizException.badRequest("该任务要求至少上传1张现场照片");

        List<Map<String,Object>> issues = issueList(body.get("issues"));
        int lateMinutes = 0;
        Object dueObj = task.get("due_at");
        if (dueObj instanceof Timestamp ts) lateMinutes = (int) Math.max(0, Duration.between(ts.toLocalDateTime(), LocalDateTime.now()).toMinutes());

        // 条件更新作为并发闸门：并发的重复提交只有一次能把状态从“未结束”改走
        int updated = jdbc.update("UPDATE inspection_task SET status=?,started_at=COALESCE(started_at,NOW(3)),submitted_at=NOW(3)," +
                        "result_json=?,result_summary=?,completed_by=?,claimed_by=COALESCE(claimed_by,?),claimed_at=COALESCE(claimed_at,NOW(3))," +
                        "submitter_name=?,submit_source='H5',receipt_name=?,receipt_phone=?,receipt_ip=?,receipt_user_agent=?,late_minutes=?," +
                        "assignee_status='ASSIGNED' WHERE id=? AND status NOT IN ('COMPLETED','CLOSED','CANCELLED')",
                issues.isEmpty() ? "COMPLETED" : "PENDING_RECTIFICATION",
                jsonText(body.get("result")), limit(text(body, "summary"), 1000), name,
                name, name, name, blankToNull(phone), cleanLimit(ip, 64), cleanLimit(userAgent, 255), lateMinutes, id);
        if (updated == 0) {
            Map<String,Object> latest = requireTask(token);
            if (latest.get("submitted_at") != null) return view(latest);
            throw BizException.badRequest("该任务已结束，不能提交回执");
        }
        jdbc.update("DELETE FROM inspection_issue WHERE task_id=? AND status='PENDING'", id);
        for (Map<String,Object> issue : issues) {
            jdbc.update("INSERT INTO inspection_issue(task_id,field_key,field_label,level,description,rectification_requirement,status) VALUES(?,?,?,?,?,?,'PENDING')",
                    id, cleanLimit(text(issue, "fieldKey"), 96), cleanLimit(text(issue, "fieldLabel"), 128),
                    defaultText(issue, "level", "WARNING"), limit(text(issue, "description"), 1000),
                    limit(text(issue, "rectificationRequirement"), 1000));
        }
        return view(requireTask(token));
    }

    private Map<String,Object> requireTask(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) throw BizException.notFound("回执链接无效");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT k.id,k.task_no taskNo,k.status,k.submitted_at," +
                "k.access_token_expire_at,k.scheduled_at,k.due_at,k.target_name targetName,p.name planName,p.category,p.form_mode formMode," +
                "p.require_photo requirePhoto,t.fields_json fieldsJson FROM inspection_task k " +
                "JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_template t ON t.id=k.template_id " +
                "WHERE k.access_token=?", token);
        if (rows.isEmpty()) throw BizException.notFound("回执链接无效或已被回收");
        Map<String,Object> row = new LinkedHashMap<>(rows.get(0));
        Object expire = row.get("access_token_expire_at");
        if (expire instanceof Timestamp ts && !ts.toLocalDateTime().isAfter(LocalDateTime.now()))
            throw BizException.notFound("回执链接已过期，请联系任务负责人");
        return row;
    }

    /** 只输出回执必需字段：不含归属链、指派人 ID、内部备注与历史结果。 */
    private Map<String,Object> view(Map<String,Object> task) {
        Long id = taskId(task);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("taskNo", task.get("taskNo"));
        out.put("planName", task.get("planName"));
        out.put("category", task.get("category"));
        out.put("targetName", task.get("targetName"));
        out.put("scheduledAt", task.get("scheduled_at"));
        out.put("dueAt", task.get("due_at"));
        out.put("status", task.get("status"));
        out.put("submittedAt", task.get("submitted_at"));
        out.put("formMode", task.get("formMode"));
        out.put("requirePhoto", intValue(task.get("requirePhoto"), 0) == 1);
        out.put("fields", fields(task.get("fieldsJson")));
        out.put("photos", photoRows(id));
        out.put("maxPhotos", MAX_PHOTOS);
        out.put("expiresAt", task.get("access_token_expire_at"));
        out.put("submittable", !CLOSED.contains(String.valueOf(task.get("status"))));
        return out;
    }

    private List<Map<String,Object>> photoRows(Long taskId) {
        return jdbc.queryForList("SELECT a.id,a.file_name fileName,a.uploaded_at uploadedAt FROM attachment a " +
                "WHERE a.business_id=? AND a.business_type IN ('CALENDAR_TASK','INSPECTION_TASK') ORDER BY a.uploaded_at,a.id", taskId);
    }

    private List<Object> fields(Object fieldsJson) {
        if (fieldsJson == null) return List.of();
        try {
            List<?> list = json.readValue(String.valueOf(fieldsJson), List.class);
            return new ArrayList<>(list);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String,Object>> issueList(Object raw) {
        List<Map<String,Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?,?> map)) continue;
            @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) map;
            if (text(m, "description").isBlank()) continue;
            out.add(m);
            if (out.size() >= 20) break;
        }
        return out;
    }

    private String jsonText(Object value) {
        if (value == null) return "[]";
        if (value instanceof String s) return s.isBlank() ? "[]" : s;
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.badRequest("回执内容格式错误");
        }
    }

    private Long taskId(Map<String,Object> task) { return ((Number) task.get("id")).longValue(); }
    private static String text(Map<?,?> m, String key) {
        Object value = m.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
    private static String defaultText(Map<?,?> m, String key, String fallback) {
        String value = text(m, key);
        return value.isBlank() ? fallback : value;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String cleanName(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) return "H5回执";
        return s.length() > 32 ? s.substring(0, 32) : s;
    }
    private static String cleanLimit(String value, int max) {
        String s = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static String limit(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
    private static int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return fallback; }
    }
}
