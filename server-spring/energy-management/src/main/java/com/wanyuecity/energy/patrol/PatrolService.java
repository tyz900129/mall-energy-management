package com.wanyuecity.energy.patrol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PatrolService {
    private static final List<String> DEFAULT_CATEGORIES = List.of("设施设备", "环境卫生", "消防安全", "秩序安全", "商户管理", "其他");
    private static final String CATEGORY_DICT = "PATROL_ISSUE_CATEGORY";
    private static final List<String> DEFAULT_POINT_TYPES = List.of("公共区域", "设备间", "消防通道", "停车场");
    private static final String POINT_TYPE_DICT = "PATROL_POINT_TYPE";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HEX = "0123456789abcdef";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PatrolAttachmentService attachmentService;
    private final PatrolNotifyService notifyService;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    public PatrolService(JdbcTemplate jdbc, ObjectMapper objectMapper, PatrolAttachmentService attachmentService, PatrolNotifyService notifyService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;
        this.notifyService = notifyService;
    }

    // ---------------------------------------------------------------- 点位
    public List<Map<String, Object>> points(boolean includeDisabled) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,name,area,floor,location_text AS locationText,point_type AS pointType,"
                + "check_items_json AS checkItemsJson,sort_order AS sortOrder,enabled FROM patrol_point "
                + (includeDisabled ? "" : "WHERE enabled=1 ") + "ORDER BY area,sort_order,id");
        for (Map<String, Object> row : rows) row.put("checkItems", checkItems(row.remove("checkItemsJson")));
        return rows;
    }

    public Map<String, Object> savePoint(Long id, Map<String, Object> body, String operator) {
        String code = text(body.get("code"));
        String name = text(body.get("name"));
        String area = text(body.get("area"));
        if (code.isBlank()) throw BizException.badRequest("请填写点位编码");
        if (name.isBlank()) throw BizException.badRequest("请填写点位名称");
        if (area.isBlank()) throw BizException.badRequest("请填写所属区域");
        String checkJson = writeCheckItems(body.get("checkItems"));
        Integer sortOrder = body.get("sortOrder") instanceof Number number ? number.intValue() : 0;
        int enabled = isTrue(body.get("enabled")) ? 1 : 0;
        List<String> types = pointTypes();
        String pointType = text(body.get("pointType"));
        if (pointType.isBlank()) pointType = types.get(0);
        else if (!types.contains(pointType)) throw BizException.badRequest("点位类型不在可选范围内，请刷新页面后重试");
        if (id == null) {
            Long duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_point WHERE code=?", Long.class, code);
            if (duplicate != null && duplicate > 0) throw BizException.badRequest("点位编码已存在");
            long newId = insert("INSERT INTO patrol_point(code,name,area,floor,location_text,point_type,check_items_json,sort_order,enabled) VALUES(?,?,?,?,?,?,?,?,?)",
                    code, name, area, text(body.get("floor")), text(body.get("locationText")),
                    pointType, checkJson, sortOrder, enabled);
            return pointRow(newId);
        }
        jdbc.update("UPDATE patrol_point SET code=?,name=?,area=?,floor=?,location_text=?,point_type=?,check_items_json=?,sort_order=?,enabled=? WHERE id=?",
                code, name, area, text(body.get("floor")), text(body.get("locationText")),
                pointType, checkJson, sortOrder, enabled, id);
        return pointRow(id);
    }

    public void deletePoint(Long id) {
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_submission WHERE point_id=?", Long.class, id);
        if (used != null && used > 0) throw BizException.badRequest("该点位已有巡场记录，不能删除，可停用");
        jdbc.update("DELETE FROM patrol_point WHERE id=?", id);
    }

    // ---------------------------------------------------------------- 部门
    public List<Map<String, Object>> webhooks() {
        return jdbc.queryForList("SELECT id,name,provider FROM webhook_config WHERE enabled=1 ORDER BY id");
    }

    public List<Map<String, Object>> departments() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT d.id,d.code,d.name,d.webhook_config_id AS webhookConfigId,w.name AS webhookName,"
                + "d.cc_webhook_ids AS ccWebhookIds,d.contact_name AS contactName,d.contact_phone AS contactPhone,d.due_time AS dueTime,"
                + "d.access_token AS accessToken,d.token_expires_at AS tokenExpiresAt,d.sort_order AS sortOrder,d.enabled,"
                + "(SELECT COUNT(*) FROM patrol_issue i WHERE i.department_id=d.id AND i.status IN ('ASSIGNED','RECTIFYING','REVIEWING')) AS pendingCount "
                + "FROM patrol_department d LEFT JOIN webhook_config w ON w.id=d.webhook_config_id ORDER BY d.sort_order,d.id");
        for (Map<String, Object> row : rows) {
            Object token = row.get("accessToken");
            row.put("accessUrl", token == null ? null : baseUrl() + "/patrol/d/" + token);
            row.put("available", row.get("webhookConfigId") != null);
            // TIME 列统一转为 HH:mm:ss 字符串，避免前端拿到时间戳格式
            row.put("dueTime", parseTime(row.get("dueTime")).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
        return rows;
    }

    public Map<String, Object> updateDepartment(Long id, Map<String, Object> body) {
        Long configId = body.get("webhookConfigId") instanceof Number number && number.longValue() > 0 ? number.longValue() : null;
        if (configId != null) {
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM webhook_config WHERE id=?", Long.class, configId);
            if (exists == null || exists == 0) throw BizException.badRequest("所选群组不存在");
        }
        String dueTime = normalizeTime(body.get("dueTime"));
        int enabled = isTrue(body.get("enabled")) ? 1 : 0;
        jdbc.update("UPDATE patrol_department SET webhook_config_id=?,cc_webhook_ids=?,contact_name=?,contact_phone=?,due_time=?,enabled=? WHERE id=?",
                configId, text(body.get("ccWebhookIds")), text(body.get("contactName")), text(body.get("contactPhone")), dueTime, enabled, id);
        return departments().stream().filter(row -> id.equals(((Number) row.get("id")).longValue())).findFirst()
                .orElseThrow(() -> BizException.notFound("部门不存在"));
    }

    public Map<String, Object> resetDepartmentToken(Long id) {
        String token = randomToken();
        jdbc.update("UPDATE patrol_department SET access_token=?,token_expires_at=? WHERE id=?", token, Date.valueOf(LocalDate.now().plusDays(90)), id);
        return departments().stream().filter(row -> id.equals(((Number) row.get("id")).longValue())).findFirst()
                .orElseThrow(() -> BizException.notFound("部门不存在"));
    }

    // ---------------------------------------------------------------- 排班
    public Map<String, Object> duties(String month) {
        String value = month == null || month.isBlank() ? LocalDate.now().format(MONTH) : month.trim();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,duty_date AS dutyDate,user_id AS userId,user_name AS userName,remark "
                + "FROM patrol_duty WHERE DATE_FORMAT(duty_date,'%Y-%m')=? ORDER BY duty_date,id", value);
        Map<Long, List<String>> backupMap = new java.util.HashMap<>();
        jdbc.query("SELECT b.duty_id AS dutyId,b.user_name AS userName FROM patrol_duty_backup b "
                        + "JOIN patrol_duty d ON d.id=b.duty_id WHERE DATE_FORMAT(d.duty_date,'%Y-%m')=? ORDER BY b.sort_order,b.id",
                rs -> {
                    backupMap.computeIfAbsent(rs.getLong("dutyId"), key -> new ArrayList<>()).add(rs.getString("userName"));
                }, value);
        for (Map<String, Object> row : rows) {
            Long dutyId = ((Number) row.get("id")).longValue();
            List<String> names = backupMap.getOrDefault(dutyId, List.of());
            row.put("backupNames", String.join("、", names));
            row.put("backupUsers", names);
        }
        List<Map<String, Object>> users = jdbc.queryForList("SELECT DISTINCT u.id,u.username,COALESCE(NULLIF(u.display_name,''),u.username) AS displayName "
                + "FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id "
                + "WHERE r.code='PROPERTY_MANAGER' AND u.status=1 ORDER BY u.id");
        if (users.isEmpty()) {
            users = jdbc.queryForList("SELECT u.id,u.username,COALESCE(NULLIF(u.display_name,''),u.username) AS displayName "
                    + "FROM sys_user u WHERE u.status=1 ORDER BY u.id");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", value);
        result.put("rows", rows);
        result.put("users", users);
        return result;
    }

    public void saveDuty(Map<String, Object> body) {
        String dutyDate = text(body.get("dutyDate"));
        String userName = text(body.get("userName"));
        if (dutyDate.isBlank()) throw BizException.badRequest("请选择值班日期");
        if (userName.isBlank()) throw BizException.badRequest("请选择值班人员");
        LocalDate date;
        try { date = LocalDate.parse(dutyDate); } catch (Exception e) { throw BizException.badRequest("值班日期格式不正确"); }
        Long userId = body.get("userId") instanceof Number number ? number.longValue() : null;
        List<Map<String, Object>> backups = backupUsers(body);
        backups.removeIf(row -> userName.equals(row.get("name")));
        Long backupUserId = backups.isEmpty() ? null : toLong(backups.get(0).get("id"));
        String backupUserName = backups.isEmpty() ? null : text(backups.get(0).get("name"));
        jdbc.update("INSERT INTO patrol_duty(duty_date,user_id,user_name,backup_user_id,backup_user_name,remark) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),backup_user_id=VALUES(backup_user_id),backup_user_name=VALUES(backup_user_name),remark=VALUES(remark)",
                Date.valueOf(date), userId, userName, backupUserId, backupUserName, text(body.get("remark")));
        Long dutyId = jdbc.queryForObject("SELECT id FROM patrol_duty WHERE duty_date=? AND user_name=?", Long.class, Date.valueOf(date), userName);
        if (dutyId == null) return;
        jdbc.update("DELETE FROM patrol_duty_backup WHERE duty_id=?", dutyId);
        for (int i = 0; i < backups.size(); i++) {
            Map<String, Object> backup = backups.get(i);
            jdbc.update("INSERT INTO patrol_duty_backup(duty_id,user_id,user_name,sort_order) VALUES(?,?,?,?)",
                    dutyId, toLong(backup.get("id")), text(backup.get("name")), i);
        }
    }

    private List<Map<String, Object>> backupUsers(Map<String, Object> body) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = body.get("backupUsers");
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            String name;
            Long id = null;
            if (item instanceof Map<?, ?> map) {
                name = text(map.get("name"));
                id = toLong(map.get("id"));
            } else {
                name = text(item);
            }
            if (name.isBlank()) continue;
            if (result.stream().anyMatch(row -> name.equals(row.get("name")))) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("name", name);
            result.add(row);
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Long.parseLong(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    public void deleteDuty(Long id) {
        jdbc.update("DELETE FROM patrol_duty_backup WHERE duty_id=?", id);
        jdbc.update("DELETE FROM patrol_duty WHERE id=?", id);
    }

    // ---------------------------------------------------------------- 轮次
    public Map<String, Object> todayRound() { return ensureRound(LocalDate.now()); }

    public Map<String, Object> ensureRound(LocalDate date) {
        List<Map<String, Object>> rows = roundRows(date);
        if (rows.isEmpty()) {
            jdbc.update("INSERT IGNORE INTO patrol_round(round_date,entry_token) VALUES(?,?)", Date.valueOf(date), randomToken());
            rows = roundRows(date);
        }
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private List<Map<String, Object>> roundRows(LocalDate date) {
        return jdbc.queryForList("SELECT id,round_date AS roundDate,entry_token AS entryToken,pushed_at AS pushedAt,status FROM patrol_round WHERE round_date=?", Date.valueOf(date));
    }

    public Map<String, Object> pushToday() {
        LocalDate today = LocalDate.now();
        Map<String, Object> round = ensureRound(today);
        List<Map<String, Object>> points = points(false);
        notifyService.pushDailyEntry(today, dutyName(today), points, text(round.get("entryToken")));
        jdbc.update("UPDATE patrol_round SET pushed_at=? WHERE id=?", LocalDateTime.now(), round.get("id"));
        Map<String, Object> result = new LinkedHashMap<>(round);
        result.put("pointCount", points.size());
        result.put("pushedAt", LocalDateTime.now());
        return result;
    }

    private String dutyName(LocalDate date) {
        List<String> names = jdbc.queryForList("SELECT user_name FROM patrol_duty WHERE duty_date=? ORDER BY id", String.class, Date.valueOf(date));
        if (names.isEmpty()) return null;
        List<String> backups = jdbc.queryForList("SELECT b.user_name FROM patrol_duty_backup b JOIN patrol_duty d ON d.id=b.duty_id "
                + "WHERE d.duty_date=? ORDER BY b.sort_order,b.id", String.class, Date.valueOf(date));
        String primary = String.join("、", names);
        return backups.isEmpty() ? primary : primary + "（备班：" + String.join("、", backups) + "）";
    }

    // ---------------------------------------------------------------- 字典（问题分类 / 点位类型）
    public List<String> categories() {
        return dictLabels(CATEGORY_DICT, DEFAULT_CATEGORIES);
    }

    public List<Map<String, Object>> categoryList() {
        return dictList(CATEGORY_DICT);
    }

    public Map<String, Object> saveCategory(Long id, Map<String, Object> body) {
        return saveDictItem(CATEGORY_DICT, "问题分类", id, body);
    }

    public void deleteCategory(Long id) {
        deleteDictItem(CATEGORY_DICT, "问题分类", id);
    }

    public List<String> pointTypes() {
        return dictLabels(POINT_TYPE_DICT, DEFAULT_POINT_TYPES);
    }

    public List<Map<String, Object>> pointTypeList() {
        return dictList(POINT_TYPE_DICT);
    }

    public Map<String, Object> savePointType(Long id, Map<String, Object> body) {
        return saveDictItem(POINT_TYPE_DICT, "点位类型", id, body);
    }

    public void deletePointType(Long id) {
        deleteDictItem(POINT_TYPE_DICT, "点位类型", id);
    }

    private List<String> dictLabels(String dictType, List<String> defaults) {
        List<String> labels = jdbc.queryForList("SELECT item_label FROM business_dictionary "
                + "WHERE dict_type=? AND status=1 ORDER BY sort_order,id", String.class, dictType);
        return labels.isEmpty() ? defaults : labels;
    }

    private List<Map<String, Object>> dictList(String dictType) {
        return jdbc.queryForList("SELECT id,item_value AS itemValue,item_label AS itemLabel,sort_order AS sortOrder,status,remark "
                + "FROM business_dictionary WHERE dict_type=? ORDER BY sort_order,id", dictType);
    }

    private Map<String, Object> saveDictItem(String dictType, String typeName, Long id, Map<String, Object> body) {
        String label = text(body.get("itemLabel"));
        if (label.isBlank()) throw BizException.badRequest("请输入" + typeName + "名称");
        if (label.length() > 32) throw BizException.badRequest(typeName + "名称不能超过 32 个字");
        int sortOrder = body.get("sortOrder") instanceof Number number ? number.intValue() : 0;
        int status = body.get("status") instanceof Number number ? (number.intValue() == 0 ? 0 : 1) : 1;
        String remark = text(body.get("remark"));
        List<Long> duplicated = id == null
                ? jdbc.queryForList("SELECT id FROM business_dictionary WHERE dict_type=? AND item_label=?", Long.class, dictType, label)
                : jdbc.queryForList("SELECT id FROM business_dictionary WHERE dict_type=? AND item_label=? AND id<>?", Long.class, dictType, label, id);
        if (!duplicated.isEmpty()) throw BizException.badRequest("该" + typeName + "已存在");
        if (id == null) {
            jdbc.update("INSERT INTO business_dictionary(dict_type,item_value,item_label,sort_order,status,remark) VALUES(?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE item_label=VALUES(item_label),sort_order=VALUES(sort_order),status=VALUES(status),remark=VALUES(remark)",
                    dictType, label, label, sortOrder, status, remark);
        } else {
            jdbc.update("UPDATE business_dictionary SET item_value=?,item_label=?,sort_order=?,status=?,remark=? WHERE id=? AND dict_type=?",
                    label, label, sortOrder, status, remark, id, dictType);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("items", dictList(dictType));
        return result;
    }

    private void deleteDictItem(String dictType, String typeName, Long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM business_dictionary WHERE dict_type=?", Integer.class, dictType);
        if (count != null && count <= 1) throw BizException.badRequest("至少需要保留一个" + typeName);
        jdbc.update("DELETE FROM business_dictionary WHERE id=? AND dict_type=?", id, dictType);
    }

    // ---------------------------------------------------------------- 移动端
    public Map<String, Object> entry(String token) {
        Map<String, Object> round = roundByToken(token);
        LocalDate date = ((Date) round.get("roundDate")).toLocalDate();
        List<Map<String, Object>> points = points(false);
        Map<Long, Map<String, Object>> states = latestStates(((Number) round.get("id")).longValue());
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        int completed = 0;
        for (Map<String, Object> point : points) {
            Long pointId = ((Number) point.get("id")).longValue();
            Map<String, Object> state = states.get(pointId);
            Map<String, Object> item = new LinkedHashMap<>(point);
            if (state == null) {
                item.put("state", "IDLE");
                item.put("result", null);
            } else {
                String result = text(state.get("result"));
                item.put("state", "ABNORMAL".equals(result) ? "ABNORMAL" : "NORMAL");
                item.put("result", result);
                item.put("submittedAt", state.get("submittedAt"));
                item.put("issueStatus", state.get("issueStatus"));
                item.put("departmentName", state.get("departmentName"));
                item.put("issueId", state.get("issueId"));
                completed++;
            }
            String area = text(point.get("area"));
            grouped.computeIfAbsent(area, key -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("area", e.getKey());
            group.put("points", e.getValue());
            groups.add(group);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roundId", round.get("id"));
        result.put("roundDate", date.toString());
        result.put("dutyName", dutyName(date));
        result.put("total", points.size());
        result.put("completed", completed);
        result.put("groups", groups);
        return result;
    }

    private Map<Long, Map<String, Object>> latestStates(long roundId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.point_id AS pointId,s.result,s.submitted_at AS submittedAt,i.id AS issueId,"
                + "i.status AS issueStatus,i.department_id AS departmentId,d.name AS departmentName "
                + "FROM patrol_submission s LEFT JOIN patrol_issue i ON i.submission_id=s.id LEFT JOIN patrol_department d ON d.id=i.department_id "
                + "WHERE s.round_id=? AND s.id IN (SELECT MAX(id) FROM patrol_submission WHERE round_id=? GROUP BY point_id)", roundId, roundId);
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) result.put(((Number) row.get("pointId")).longValue(), row);
        return result;
    }

    public Map<String, Object> pointForm(Long pointId, String token, String username) {
        roundByToken(token);
        Map<String, Object> point = pointRow(pointId);
        if (point == null) throw BizException.notFound("点位不存在");
        Map<String, Object> user = userInfo(username);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("point", point);
        result.put("submitter", user);
        result.put("specialArea", specialArea(point));
        result.put("categories", categories());
        result.put("urgencies", List.of(Map.of("value", "NORMAL", "label", "一般"), Map.of("value", "URGENT", "label", "紧急")));
        result.put("departments", departments());
        return result;
    }

    public Map<String, Object> submit(String token, String username, Map<String, Object> body, MultipartFile[] photos, String clientIp) {
        Map<String, Object> round = roundByToken(token);
        LocalDate date = ((Date) round.get("roundDate")).toLocalDate();
        Long pointId = body.get("pointId") instanceof Number number ? number.longValue() : null;
        if (pointId == null) throw BizException.badRequest("缺少点位信息");
        Map<String, Object> point = pointRow(pointId);
        if (point == null) throw BizException.notFound("点位不存在");
        if (!isTrue(point.get("enabled"))) throw BizException.badRequest("该点位已停用");
        String resultValue = text(body.get("result")).toUpperCase();
        if (!"NORMAL".equals(resultValue) && !"ABNORMAL".equals(resultValue)) throw BizException.badRequest("请选择巡检结果");
        String note = text(body.get("note"));
        String category = text(body.get("category"));
        // 物业巡场已取消紧急程度，统一按「一般」处理（保留字段以兼容历史数据）
        String urgency = "NORMAL";
        Long departmentId = body.get("departmentId") instanceof Number number && number.longValue() > 0 ? number.longValue() : null;
        long photoCount = photos == null ? 0 : java.util.Arrays.stream(photos).filter(f -> f != null && !f.isEmpty()).count();
        boolean special = specialArea(point);
        // 特殊区域无论正常还是异常都必须拍照留档；异常才进入整改流程
        if ("ABNORMAL".equals(resultValue) || special) {
            if (photoCount < 1) throw BizException.badRequest(special ? "特殊区域巡检需上传现场照片" : "请至少上传 1 张现场照片");
            if (photoCount > 6) throw BizException.badRequest("现场照片最多 6 张");
        }
        if ("ABNORMAL".equals(resultValue)) {
            if (category.isBlank()) throw BizException.badRequest("请选择问题分类");
            if (!categories().contains(category)) throw BizException.badRequest("问题分类不在可选范围内，请刷新页面后重试");
        }
        Map<String, Object> department = null;
        if (departmentId != null) department = departmentRow(departmentId);
        if (departmentId != null && department == null) throw BizException.badRequest("整改部门不存在");

        Map<String, Object> user = userInfo(username);
        String submitterName = text(user.get("displayName"));
        // 免登录 H5 表单：记录现场填写的填报人姓名
        if (user.get("id") == null) {
            String reportedName = text(body.get("submitterName"));
            if (reportedName.isBlank()) throw BizException.badRequest("请填写填报人姓名");
            if (reportedName.length() > 20) throw BizException.badRequest("填报人姓名不能超过 20 个字");
            submitterName = reportedName;
        }
        LocalDateTime submittedAt = LocalDateTime.now();
        String photoToken = randomToken();
        long submissionId = insert("INSERT INTO patrol_submission(round_id,point_id,submitter_id,submitter_name,photo_token,result,note,check_results_json,client_ip,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                round.get("id"), pointId, user.get("id"), submitterName, photoToken, resultValue, note, toJson(body.get("checkResults")), clientIp, submittedAt);
        attachmentService.saveAll("SUBMISSION", submissionId, photos);

        // 特殊区域正常巡检：不生成问题、不需要整改，但把留档照片推送到巡场群
        if (special && "NORMAL".equals(resultValue)) {
            try {
                notifyService.pushSpecialNormal(point, submitterName, submissionId, photoToken, submittedAt);
            } catch (Exception ex) {
                log.warn("patrol special normal notify failed: submission={}", submissionId, ex);
            }
        }

        Map<String, Object> issueResult = null;
        if ("ABNORMAL".equals(resultValue)) {
            LocalDateTime now = LocalDateTime.now();
            String issueNo = nextIssueNo(date);
            String feedbackToken = randomToken();
            String status = department == null ? "UNASSIGNED" : "ASSIGNED";
            LocalDateTime dueAt = department == null ? null : nextDueAt(department, now);
            long issueId;
            for (int attempt = 1; ; attempt++) {
                try {
                    issueId = insert("INSERT INTO patrol_issue(issue_no,submission_id,round_id,point_id,point_name,area,floor,department_id,category,urgency,description,status,due_at,assigned_by,assigned_at,feedback_token,submitter_name,submitted_at) "
                                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            issueNo, submissionId, round.get("id"), pointId, point.get("name"), point.get("area"), point.get("floor"),
                            departmentId, category, urgency, note, status, dueAt, department == null ? null : submitterName,
                            department == null ? null : now, feedbackToken, submitterName, now);
                    break;
                } catch (DuplicateKeyException ex) {
                    // 并发提交时可能取到相同流水号，重新取最大号重试
                    if (attempt >= 5) throw ex;
                    issueNo = nextIssueNo(date);
                }
            }
            logIssue(issueId, "CREATE", null, departmentId, null, status, note, submitterName);
            Map<String, Object> issueRow = issueRow(issueId);
            // 异常上报推送：独立事件类型，配置了订阅的机器人都会收到（含现场照片）
            try {
                notifyService.pushIssueSubmitted(issueRow, department);
            } catch (Exception ex) {
                log.warn("patrol issue {} submit-notify failed: {}", issueNo, ex.getMessage());
            }
            boolean notified = false;
            if (department != null) {
                try {
                    notifyService.pushIssue(issueRow, department, false, null);
                    notified = true;
                } catch (Exception ex) {
                    log.warn("patrol issue {} dispatch failed: {}", issueNo, ex.getMessage());
                }
            }
            issueResult = new LinkedHashMap<>();
            issueResult.put("id", issueId);
            issueResult.put("issueNo", issueNo);
            issueResult.put("status", status);
            issueResult.put("departmentName", department == null ? null : department.get("name"));
            issueResult.put("notified", notified);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submissionId", submissionId);
        response.put("pointId", pointId);
        response.put("result", resultValue);
        response.put("submittedAt", submittedAt);
        response.put("specialArea", special);
        response.put("pointState", "ABNORMAL".equals(resultValue) ? "ABNORMAL" : "NORMAL");
        response.put("issue", issueResult);
        return response;
    }

    // ---------------------------------------------------------------- 台账
    public Map<String, Object> issuePage(Map<String, Object> query) {
        int page = intParam(query.get("page"), 1, 1, 100000);
        int size = intParam(query.get("size"), 20, 1, 200);
        List<Object> args = new ArrayList<>();
        String where = issueWhere(query, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue i " + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT i.id,i.issue_no AS issueNo,i.point_name AS pointName,i.area,i.floor,"
                + "i.department_id AS departmentId,d.name AS departmentName,i.category,i.urgency,i.description,i.status,i.due_at AS dueAt,"
                + "i.assigned_by AS assignedBy,i.assigned_at AS assignedAt,i.submitter_name AS submitterName,i.submitted_at AS submittedAt,"
                + "i.rectified_at AS rectifiedAt,i.closed_at AS closedAt,i.overdue_flag AS overdueFlag,i.feedback_token AS feedbackToken,"
                + "(SELECT COUNT(*) FROM patrol_attachment a WHERE a.business_type='SUBMISSION' AND a.business_id=i.submission_id) AS photoCount "
                + "FROM patrol_issue i LEFT JOIN patrol_department d ON d.id=i.department_id " + where
                + " ORDER BY CASE WHEN i.status='UNASSIGNED' THEN 0 ELSE 1 END, i.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private String issueWhere(Map<String, Object> query, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        String status = text(query.get("status"));
        if (!status.isBlank()) { where.append("AND i.status=? "); args.add(status); }
        if (query.get("departmentId") instanceof Number number && number.longValue() > 0) {
            where.append("AND i.department_id=? "); args.add(number.longValue());
        }
        String keyword = text(query.get("keyword"));
        if (!keyword.isBlank()) {
            where.append("AND (i.issue_no LIKE ? OR i.point_name LIKE ? OR i.description LIKE ?) ");
            String like = "%" + keyword + "%";
            args.add(like); args.add(like); args.add(like);
        }
        String dateFrom = text(query.get("dateFrom"));
        if (!dateFrom.isBlank()) { where.append("AND DATE(i.created_at)>=? "); args.add(dateFrom); }
        String dateTo = text(query.get("dateTo"));
        if (!dateTo.isBlank()) { where.append("AND DATE(i.created_at)<=? "); args.add(dateTo); }
        return where.toString();
    }

    public Map<String, Object> issueDetail(Long id) {
        Map<String, Object> issue = issueRow(id);
        if (issue == null) throw BizException.notFound("问题不存在");
        List<Map<String, Object>> attachments = attachmentService.list("SUBMISSION", ((Number) issue.get("submissionId")).longValue());
        attachments.addAll(attachmentService.list("RECTIFY", id));
        issue.put("attachments", attachments);
        issue.put("logs", jdbc.queryForList("SELECT action,from_department_id AS fromDepartmentId,to_department_id AS toDepartmentId,from_status AS fromStatus,"
                + "to_status AS toStatus,note,operator,created_at AS createdAt FROM patrol_issue_log WHERE issue_id=? ORDER BY id", id));
        return issue;
    }

    public Map<String, Object> assign(Long id, Long departmentId, String note, String operator) {
        Map<String, Object> issue = issueRow(id);
        if (issue == null) throw BizException.notFound("问题不存在");
        if ("CLOSED".equals(text(issue.get("status")))) throw BizException.badRequest("已闭环的问题不能重新分派");
        Map<String, Object> department = departmentRow(departmentId);
        if (department == null) throw BizException.badRequest("整改部门不存在");
        if (!isTrue(department.get("enabled"))) throw BizException.badRequest("整改部门已停用");
        Object oldDepartmentId = issue.get("departmentId");
        String oldDepartmentName = text(issue.get("departmentName"));
        boolean transfer = oldDepartmentId instanceof Number number && number.longValue() != departmentId;
        String oldStatus = text(issue.get("status"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = nextDueAt(department, now);
        jdbc.update("UPDATE patrol_issue SET department_id=?,status='ASSIGNED',due_at=?,assigned_by=?,assigned_at=?,overdue_flag=0 WHERE id=?",
                departmentId, dueAt, operator, now, id);
        logIssue(id, transfer ? "TRANSFER" : "ASSIGN", oldDepartmentId instanceof Number n ? n.longValue() : null, departmentId, oldStatus, "ASSIGNED", note, operator);
        Map<String, Object> refreshed = issueRow(id);
        if (transfer) {
            Map<String, Object> oldDepartment = departmentRow(((Number) oldDepartmentId).longValue());
            if (oldDepartment != null) {
                try { notifyService.pushIssue(refreshed, oldDepartment, true, text(department.get("name"))); }
                catch (Exception ex) { log.warn("patrol transfer notify failed: {}", ex.getMessage()); }
            }
        }
        try { notifyService.pushIssue(refreshed, department, transfer, oldDepartmentName); }
        catch (Exception ex) { log.warn("patrol dispatch notify failed: {}", ex.getMessage()); }
        refreshed.put("notified", true);
        return refreshed;
    }

    public Map<String, Object> review(Long id, boolean approved, String note, String operator) {
        Map<String, Object> issue = issueRow(id);
        if (issue == null) throw BizException.notFound("问题不存在");
        String status = text(issue.get("status"));
        if (!"REVIEWING".equals(status) && !"RECTIFYING".equals(status)) throw BizException.badRequest("当前状态下不能复核");
        LocalDateTime now = LocalDateTime.now();
        if (approved) {
            jdbc.update("UPDATE patrol_issue SET status='CLOSED',reviewed_by=?,reviewed_at=?,review_note=?,closed_at=?,overdue_flag=0 WHERE id=?",
                    operator, now, text(note), now, id);
            logIssue(id, "REVIEW_PASS", null, null, status, "CLOSED", note, operator);
        } else {
            jdbc.update("UPDATE patrol_issue SET status='ASSIGNED',reviewed_by=?,reviewed_at=?,review_note=?,overdue_flag=0 WHERE id=?",
                    operator, now, text(note), id);
            logIssue(id, "REVIEW_REJECT", null, null, status, "ASSIGNED", note, operator);
        }
        return issueRow(id);
    }

    public Map<String, Object> submissionPage(Map<String, Object> query) {
        int page = intParam(query.get("page"), 1, 1, 100000);
        int size = intParam(query.get("size"), 20, 1, 200);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        String date = text(query.get("date"));
        if (!date.isBlank()) { where.append("AND DATE(s.submitted_at)=? "); args.add(date); }
        if (query.get("pointId") instanceof Number number && number.longValue() > 0) { where.append("AND s.point_id=? "); args.add(number.longValue()); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_submission s " + where, Long.class, args.toArray());
        args.add(size); args.add((page - 1) * size);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT s.id,s.point_id AS pointId,p.name AS pointName,p.area,p.floor,s.result,s.note,"
                + "s.submitter_name AS submitterName,s.submitted_at AS submittedAt,i.id AS issueId,i.status AS issueStatus "
                + "FROM patrol_submission s JOIN patrol_point p ON p.id=s.point_id LEFT JOIN patrol_issue i ON i.submission_id=s.id "
                + where + " ORDER BY s.submitted_at DESC LIMIT ? OFFSET ?", args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 超级管理员删除测试数据：提交记录连同其问题单、照片、流转日志一并物理删除。 */
    @Transactional
    public void deleteSubmission(Long submissionId) {
        List<Long> issueIds = jdbc.queryForList("SELECT id FROM patrol_issue WHERE submission_id=?", Long.class, submissionId);
        for (Long issueId : issueIds) deleteIssueData(issueId);
        jdbc.update("DELETE FROM patrol_issue WHERE submission_id=?", submissionId);
        attachmentService.deleteByBusiness("SUBMISSION", submissionId);
        jdbc.update("DELETE FROM patrol_submission WHERE id=?", submissionId);
    }

    /** 超级管理员删除测试数据：仅删问题单（保留原提交记录），照片与日志一并物理删除。 */
    @Transactional
    public void deleteIssue(Long issueId) {
        if (issueRow(issueId) == null) throw BizException.notFound("问题单不存在");
        deleteIssueData(issueId);
        jdbc.update("DELETE FROM patrol_issue WHERE id=?", issueId);
    }

    private void deleteIssueData(Long issueId) {
        attachmentService.deleteByBusiness("RECTIFY", issueId);
        jdbc.update("DELETE FROM patrol_issue_log WHERE issue_id=?", issueId);
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_point WHERE enabled=1", Long.class));
        result.put("todayCompleted", jdbc.queryForObject("SELECT COUNT(DISTINCT point_id) FROM patrol_submission WHERE DATE(submitted_at)=CURDATE()", Long.class));
        result.put("todayAbnormal", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_submission WHERE DATE(submitted_at)=CURDATE() AND result='ABNORMAL'", Long.class));
        result.put("openIssues", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE status IN ('UNASSIGNED','ASSIGNED','RECTIFYING','REVIEWING')", Long.class));
        result.put("unassigned", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE status='UNASSIGNED'", Long.class));
        result.put("overdue", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE status IN ('ASSIGNED','RECTIFYING','REVIEWING') AND due_at IS NOT NULL AND due_at<NOW()", Long.class));
        result.put("closedToday", jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE DATE(closed_at)=CURDATE()", Long.class));
        result.put("byDepartment", jdbc.queryForList("SELECT d.id,d.name,"
                + "SUM(CASE WHEN i.status IN ('ASSIGNED','RECTIFYING','REVIEWING') THEN 1 ELSE 0 END) AS openCount,"
                + "SUM(CASE WHEN i.status IN ('ASSIGNED','RECTIFYING','REVIEWING') AND i.due_at<NOW() THEN 1 ELSE 0 END) AS overdueCount,"
                + "SUM(CASE WHEN DATE(i.closed_at)=CURDATE() THEN 1 ELSE 0 END) AS closedToday "
                + "FROM patrol_department d LEFT JOIN patrol_issue i ON i.department_id=d.id GROUP BY d.id,d.name,d.sort_order ORDER BY d.sort_order"));
        return result;
    }

    // ---------------------------------------------------------------- 公开端
    public Map<String, Object> publicIssue(String token) {
        Map<String, Object> issue = issueByToken(token);
        if (issue == null) throw BizException.notFound("整改链接无效或已失效");
        List<Map<String, Object>> attachments = publicAttachments(token, attachmentService.list("SUBMISSION", ((Number) issue.get("submissionId")).longValue()));
        attachments.addAll(publicAttachments(token, attachmentService.list("RECTIFY", ((Number) issue.get("id")).longValue())));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", issue);
        result.put("attachments", attachments);
        String status = text(issue.get("status"));
        result.put("canFeedback", !"CLOSED".equals(status));
        result.put("categories", categories());
        return result;
    }

    /** 提交后补传现场照片：逐张上传，避免一次 multipart 体过大导致超时 */
    public List<Map<String, Object>> publicAppendPhotos(String token, Long submissionId, MultipartFile[] photos) {
        Map<String, Object> round = roundByToken(token);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,round_id FROM patrol_submission WHERE id=?", submissionId);
        if (rows.isEmpty()) throw BizException.notFound("提交记录不存在");
        long roundId = ((Number) rows.get(0).get("round_id")).longValue();
        if (roundId != ((Number) round.get("id")).longValue()) throw BizException.badRequest("无权访问该提交记录");
        long existing = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_attachment WHERE business_type='SUBMISSION' AND business_id=?", Long.class, submissionId);
        long incoming = photos == null ? 0 : java.util.Arrays.stream(photos).filter(f -> f != null && !f.isEmpty()).count();
        if (existing + incoming > 6) throw BizException.badRequest("现场照片最多 6 张");
        return attachmentService.saveAll("SUBMISSION", submissionId, photos);
    }

    public Map<String, Object> publicFeedback(String token, String note, MultipartFile[] photos, String clientIp) {
        Map<String, Object> issue = issueByToken(token);
        if (issue == null) throw BizException.notFound("整改链接无效或已失效");
        String status = text(issue.get("status"));
        if ("CLOSED".equals(status)) throw BizException.badRequest("该问题已闭环，无需再反馈");
        // 未分派部门的问题同样允许通过通知中的「立即整改」链接直接反馈闭环，
        // 否则通知里给出的整改链接会变成死链（提交时才被拦截）。
        if (!List.of("UNASSIGNED", "ASSIGNED", "RECTIFYING", "REVIEWING").contains(status))
            throw BizException.badRequest("该问题当前状态不支持整改反馈");
        String cleanNote = text(note);
        if (cleanNote.length() < 2) throw BizException.badRequest("请填写整改措施");
        if (cleanNote.length() > 500) throw BizException.badRequest("整改措施不能超过 500 字");
        Long issueId = ((Number) issue.get("id")).longValue();
        attachmentService.saveAll("RECTIFY", issueId, photos);
        LocalDateTime now = LocalDateTime.now();
        // 整改提交即闭环，取消管理员复核步骤
        jdbc.update("UPDATE patrol_issue SET status='CLOSED',rectification_note=?,rectified_at=?,rectified_by=?,closed_at=?,overdue_flag=0 WHERE id=?",
                cleanNote, now, "部门反馈(" + clientIp + ")", now, issueId);
        logIssue(issueId, "FEEDBACK_CLOSE", null, null, status, "CLOSED", cleanNote, "部门反馈");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("status", "CLOSED");
        return result;
    }

    public Map<String, Object> publicDepartment(String token, String range) {
        Map<String, Object> department = departmentByToken(token);
        if (department == null) throw BizException.notFound("部门工作台链接无效或已过期");
        String value = text(range).isBlank() ? "open" : text(range);
        String dateFilter = switch (value) {
            case "today" -> "AND DATE(i.created_at)=CURDATE() ";
            case "3d" -> "AND i.created_at>=DATE_SUB(NOW(),INTERVAL 3 DAY) ";
            case "7d" -> "AND i.created_at>=DATE_SUB(NOW(),INTERVAL 7 DAY) ";
            default -> "";
        };
        String statusFilter = "open".equals(value) || !List.of("today", "3d", "7d").contains(value)
                ? "AND i.status IN ('ASSIGNED','RECTIFYING','REVIEWING') " : "AND i.status<>'UNASSIGNED' ";
        List<Map<String, Object>> issues = jdbc.queryForList("SELECT i.id,i.issue_no AS issueNo,i.point_name AS pointName,i.area,i.floor,i.category,i.urgency,"
                + "i.description,i.status,i.due_at AS dueAt,i.submitted_at AS submittedAt,i.feedback_token AS feedbackToken,"
                + "CASE WHEN i.due_at IS NOT NULL AND i.due_at<NOW() THEN 1 ELSE 0 END AS overdue "
                + "FROM patrol_issue i WHERE i.department_id=? " + statusFilter + dateFilter + "ORDER BY overdue DESC,i.created_at ASC", department.get("id"));
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE department_id=? AND status IN ('ASSIGNED','RECTIFYING','REVIEWING')", Long.class, department.get("id"));
        Long overdue = jdbc.queryForObject("SELECT COUNT(*) FROM patrol_issue WHERE department_id=? AND status IN ('ASSIGNED','RECTIFYING','REVIEWING') AND due_at<NOW()", Long.class, department.get("id"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("department", Map.of("name", text(department.get("name")), "contactName", text(department.get("contactName")), "contactPhone", text(department.get("contactPhone"))));
        result.put("pending", pending == null ? 0 : pending);
        result.put("overdue", overdue == null ? 0 : overdue);
        result.put("range", value);
        result.put("issues", issues);
        return result;
    }

    public org.springframework.core.io.Resource publicAttachment(String token, Long attachmentId) {
        Map<String, Object> issue = issueByToken(token);
        Long allowed = null;
        if (issue != null) {
            allowed = attachmentService.findIdInIssue(attachmentId, ((Number) issue.get("id")).longValue(), ((Number) issue.get("submissionId")).longValue());
        } else {
            // 特殊区域正常巡检没有整改单，用提交记录上的照片令牌校验
            List<Long> ids = jdbc.queryForList("SELECT a.id FROM patrol_attachment a JOIN patrol_submission s ON s.id=a.business_id "
                    + "WHERE a.business_type='SUBMISSION' AND s.photo_token=? AND a.id=?", Long.class, token, attachmentId);
            if (!ids.isEmpty()) allowed = ids.get(0);
        }
        if (allowed == null) {
            Map<String, Object> department = departmentByToken(token);
            if (department != null) {
                List<Long> ids = jdbc.queryForList("SELECT a.id FROM patrol_attachment a JOIN patrol_issue i ON "
                        + "((a.business_type='SUBMISSION' AND a.business_id=i.submission_id) OR (a.business_type='RECTIFY' AND a.business_id=i.id)) "
                        + "WHERE i.department_id=? AND a.id=?", Long.class, department.get("id"), attachmentId);
                if (!ids.isEmpty()) allowed = ids.get(0);
            }
        }
        if (allowed == null) throw BizException.notFound("照片不存在");
        return attachmentService.load(attachmentId);
    }

    // ---------------------------------------------------------------- 定时任务支撑
    public void pushDailyEntry() {
        Map<String, Object> round = ensureRound(LocalDate.now());
        List<Map<String, Object>> points = points(false);
        notifyService.pushDailyEntry(LocalDate.now(), dutyName(LocalDate.now()), points, text(round.get("entryToken")));
        jdbc.update("UPDATE patrol_round SET pushed_at=? WHERE id=?", LocalDateTime.now(), round.get("id"));
    }

    public void remindUnassigned() {
        List<Map<String, Object>> issues = jdbc.queryForList("SELECT i.id,i.issue_no AS issueNo,i.point_name AS pointName,i.area,i.description,i.feedback_token AS feedbackToken "
                + "FROM patrol_issue i WHERE i.status='UNASSIGNED' AND DATE(i.created_at)=CURDATE() ORDER BY i.id");
        if (issues.isEmpty()) return;
        jdbc.update("INSERT INTO patrol_issue_log(issue_id,action,note,operator) SELECT i.id,'UNASSIGNED_REMIND','17:00 待分派汇总提醒','system' "
                + "FROM patrol_issue i WHERE i.status='UNASSIGNED' AND DATE(i.created_at)=CURDATE() "
                + "AND NOT EXISTS(SELECT 1 FROM patrol_issue_log l WHERE l.issue_id=i.id AND l.action='UNASSIGNED_REMIND')");
        notifyService.pushUnassignedReminder(LocalDate.now(), issues);
    }

    public void scanOverdue() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.id,i.issue_no AS issueNo,i.point_name AS pointName,i.area,i.floor,i.description,"
                + "i.urgency,i.due_at AS dueAt,i.submitted_at AS submittedAt,i.submitter_name AS submitterName,i.feedback_token AS feedbackToken,"
                + "i.submission_id AS submissionId,i.department_id AS departmentId "
                + "FROM patrol_issue i WHERE i.status IN ('ASSIGNED','RECTIFYING','REVIEWING') AND i.due_at IS NOT NULL AND i.due_at<NOW() "
                + "AND NOT EXISTS(SELECT 1 FROM patrol_issue_log l WHERE l.issue_id=i.id AND l.action='OVERDUE_NOTICE') LIMIT 50");
        for (Map<String, Object> issue : rows) {
            jdbc.update("UPDATE patrol_issue SET overdue_flag=1 WHERE id=?", issue.get("id"));
            logIssue(((Number) issue.get("id")).longValue(), "OVERDUE_NOTICE", null, null, null, null, "逾期提醒", "system");
            Map<String, Object> department = departmentRow(((Number) issue.get("departmentId")).longValue());
            if (department != null) {
                try { notifyService.pushOverdue(issue, department); }
                catch (Exception ex) { log.warn("patrol overdue notify failed: {}", ex.getMessage()); }
            }
        }
    }

    // ---------------------------------------------------------------- 内部工具
    public Map<String, Object> userInfo(String username) {
        if (username != null && !username.isBlank()) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) AS displayName,phone "
                    + "FROM sys_user WHERE username=? LIMIT 1", username);
            if (!rows.isEmpty()) return rows.get(0);
        }
        // 免登录 H5 填报（链接携带 entry_token，无登录态）
        Map<String, Object> guest = new LinkedHashMap<>();
        guest.put("id", null);
        guest.put("username", "anonymous");
        guest.put("displayName", "现场填报");
        guest.put("phone", null);
        return guest;
    }

    private Map<String, Object> roundByToken(String token) {
        if (token == null || token.isBlank()) throw BizException.badRequest("缺少巡场入口令牌");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,round_date AS roundDate,entry_token AS entryToken,pushed_at AS pushedAt,status "
                + "FROM patrol_round WHERE entry_token=?", token.trim());
        if (rows.isEmpty()) throw BizException.badRequest("巡场入口链接已失效");
        Map<String, Object> round = rows.get(0);
        LocalDate date = ((Date) round.get("roundDate")).toLocalDate();
        if (!date.equals(LocalDate.now())) throw BizException.badRequest("巡场入口链接已过期，请使用今日推送的链接");
        return round;
    }

    private boolean specialArea(Map<String, Object> point) {
        return text(point.get("area")).contains("特殊区域");
    }

    private Map<String, Object> pointRow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,name,area,floor,location_text AS locationText,point_type AS pointType,"
                + "check_items_json AS checkItemsJson,sort_order AS sortOrder,enabled FROM patrol_point WHERE id=?", id);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        row.put("checkItems", checkItems(row.remove("checkItemsJson")));
        return row;
    }

    private Map<String, Object> departmentRow(Long id) {
        if (id == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,name,webhook_config_id AS webhookConfigId,cc_webhook_ids AS ccWebhookIds,"
                + "contact_name AS contactName,contact_phone AS contactPhone,due_time AS dueTime,access_token AS accessToken,token_expires_at AS tokenExpiresAt,enabled "
                + "FROM patrol_department WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> departmentByToken(String token) {
        if (token == null || token.isBlank()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,name,webhook_config_id AS webhookConfigId,cc_webhook_ids AS ccWebhookIds,"
                + "contact_name AS contactName,contact_phone AS contactPhone,due_time AS dueTime,access_token AS accessToken,token_expires_at AS tokenExpiresAt,enabled "
                + "FROM patrol_department WHERE access_token=?", token.trim());
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        Object expires = row.get("tokenExpiresAt");
        LocalDateTime expiresAt = expires instanceof LocalDateTime local ? local
                : expires instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : null;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return null;
        return row;
    }

    private Map<String, Object> issueRow(Long id) {
        List<Map<String, Object>> rows = issueRows("WHERE i.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> issueByToken(String token) {
        if (token == null || token.isBlank()) return null;
        List<Map<String, Object>> rows = issueRows("WHERE i.feedback_token=?", token.trim());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> issueRows(String where, Object... args) {
        return jdbc.queryForList("SELECT i.id,i.issue_no AS issueNo,i.submission_id AS submissionId,i.round_id AS roundId,i.point_id AS pointId,"
                + "i.point_name AS pointName,i.area,i.floor,i.department_id AS departmentId,d.name AS departmentName,d.contact_name AS departmentContact,"
                + "i.category,i.urgency,i.description,i.status,i.due_at AS dueAt,i.assigned_by AS assignedBy,i.assigned_at AS assignedAt,"
                + "i.feedback_token AS feedbackToken,i.submitter_name AS submitterName,i.submitted_at AS submittedAt,i.rectified_by AS rectifiedBy,"
                + "i.rectified_at AS rectifiedAt,i.rectification_note AS rectificationNote,i.reviewed_by AS reviewedBy,i.reviewed_at AS reviewedAt,"
                + "i.review_note AS reviewNote,i.closed_at AS closedAt,i.overdue_flag AS overdueFlag,i.created_at AS createdAt "
                + "FROM patrol_issue i LEFT JOIN patrol_department d ON d.id=i.department_id " + where, args);
    }

    private List<Map<String, Object>> publicAttachments(String token, List<Map<String, Object>> attachments) {
        for (Map<String, Object> row : attachments) {
            row.put("url", "/api/v1/patrol-public/attachments/" + token + "/" + row.get("id"));
        }
        return attachments;
    }

    private void logIssue(Long issueId, String action, Long fromDepartmentId, Long toDepartmentId, String fromStatus, String toStatus, String note, String operator) {
        jdbc.update("INSERT INTO patrol_issue_log(issue_id,action,from_department_id,to_department_id,from_status,to_status,note,operator) VALUES(?,?,?,?,?,?,?,?)",
                issueId, action, fromDepartmentId, toDepartmentId, fromStatus, toStatus, note == null ? null : (note.length() > 500 ? note.substring(0, 500) : note), operator);
    }

    /**
     * 整改截止时间按部门配置的每日固定时间点计算：当天该时间点之前完成分派/提交则当天截止，
     * 超过该时间点自动顺延到次日同一时间。
     */
    private LocalDateTime nextDueAt(Map<String, Object> department, LocalDateTime now) {
        LocalTime dueTime = parseTime(department.get("dueTime"));
        LocalDateTime due = LocalDateTime.of(now.toLocalDate(), dueTime);
        return due.isAfter(now) ? due : due.plusDays(1);
    }

    private LocalTime parseTime(Object raw) {
        if (raw instanceof java.sql.Time time) return time.toLocalTime();
        if (raw instanceof LocalTime time) return time;
        String value = text(raw);
        if (value.isBlank()) return LocalTime.of(20, 0);
        if (value.length() == 5) value = value + ":00";
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            return LocalTime.of(20, 0);
        }
    }

    private String normalizeTime(Object raw) {
        String value = text(raw);
        if (value.isBlank()) return "20:00:00";
        if (value.length() == 5) value = value + ":00";
        try {
            return LocalTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e) {
            throw BizException.badRequest("整改截止时间格式不正确，请使用 HH:mm");
        }
    }

    private String nextIssueNo(LocalDate date) {
        String prefix = "XC" + date.format(BASIC_DATE);
        // 以已存在的最大流水号为准，避免删除问题单后 COUNT 回退造成编号冲突
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(issue_no, ?) AS UNSIGNED)), 0) FROM patrol_issue WHERE issue_no LIKE ?",
                Long.class, prefix.length() + 1, prefix + "%");
        return prefix + String.format("%04d", (max == null ? 0 : max) + 1);
    }

    private String writeCheckItems(Object raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String label = text(map.get("label"));
                    if (!label.isBlank()) items.add(Map.of("label", label));
                } else if (item != null && !item.toString().isBlank()) {
                    items.add(Map.of("label", item.toString().trim()));
                }
            }
        }
        return toJson(items);
    }

    private List<Map<String, Object>> checkItems(Object json) {
        if (json == null || json.toString().isBlank()) return List.of();
        try {
            return objectMapper.readValue(json.toString(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try { return value == null ? "[]" : objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    private static String randomToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(HEX.charAt(RANDOM.nextInt(HEX.length())));
        return sb.toString();
    }

    private int intParam(Object value, int fallback, int min, int max) {
        int result = fallback;
        if (value instanceof Number number) result = number.intValue();
        else if (value != null && !value.toString().isBlank()) {
            try { result = Integer.parseInt(value.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        return Math.max(min, Math.min(max, result));
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() == 1;
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    private String baseUrl() { return publicBaseUrl.replaceAll("/$", ""); }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
}
