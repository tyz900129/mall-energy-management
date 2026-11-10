package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.patrol.PatrolAttachmentService;
import com.wanyuecity.energy.platform.service.MerchantNameParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OpsPatrolService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern ACCOUNT_STATUS_SUFFIX = Pattern.compile("\\s*[（(](?:停用|注销|失效|已停用|已注销)[）)]\\s*$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HEX = "0123456789abcdef";

    private final JdbcTemplate jdbc;
    private final PatrolAttachmentService attachmentService;
    private final OpsPatrolNotifyService notifyService;

    public OpsPatrolService(JdbcTemplate jdbc, PatrolAttachmentService attachmentService, OpsPatrolNotifyService notifyService) {
        this.jdbc = jdbc;
        this.attachmentService = attachmentService;
        this.notifyService = notifyService;
    }

    // ================================================================ 处室配置
    public List<Map<String, Object>> depts() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.id,d.code,d.name,d.form_type AS formType,d.webhook_config_id AS webhookConfigId,w.name AS webhookName,"
                        + "d.leader_name AS leaderName,d.close_time AS closeTime,d.sort_order AS sortOrder,d.enabled "
                        + "FROM ops_patrol_dept d LEFT JOIN webhook_config w ON w.id=d.webhook_config_id ORDER BY d.sort_order,d.id");
        for (Map<String, Object> row : rows) {
            row.put("floors", jdbc.queryForList("SELECT floor_code AS floorCode,floor_name AS floorName FROM ops_patrol_dept_floor WHERE dept_id=? ORDER BY sort_order,id", row.get("id")));
        }
        return rows;
    }

    public Map<String, Object> saveDept(Long id, Map<String, Object> body) {
        String leaderName = text(body.get("leaderName"));
        String closeTime = text(body.get("closeTime"));
        if (closeTime.isBlank()) closeTime = "21:30";
        try { LocalTime.parse(closeTime); } catch (Exception ex) { throw BizException.badRequest("闭店时间格式应为 HH:mm"); }
        Long configId = body.get("webhookConfigId") instanceof Number n && n.longValue() > 0 ? n.longValue() : null;
        if (configId != null) {
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM webhook_config WHERE id=?", Long.class, configId);
            if (exists == null || exists == 0) throw BizException.badRequest("所选群机器人不存在");
        }
        int enabled = isTrue(body.get("enabled")) ? 1 : 0;
        jdbc.update("UPDATE ops_patrol_dept SET webhook_config_id=?,leader_name=?,close_time=?,enabled=? WHERE id=?",
                configId, leaderName, closeTime, enabled, id);
        jdbc.update("DELETE FROM ops_patrol_dept_floor WHERE dept_id=?", id);
        Object floors = body.get("floors");
        if (floors instanceof List<?> list) {
            int sort = 0;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) continue;
                String code = text(row.get("floorCode"));
                String name = text(row.get("floorName"));
                if (code.isBlank() || name.isBlank()) continue;
                jdbc.update("INSERT IGNORE INTO ops_patrol_dept_floor(dept_id,floor_code,floor_name,sort_order) VALUES(?,?,?,?)",
                        id, code, name, sort++);
            }
        }
        return depts().stream().filter(r -> id.equals(((Number) r.get("id")).longValue())).findFirst()
                .orElseThrow(() -> BizException.notFound("处室不存在"));
    }

    public List<Map<String, Object>> webhooks() {
        return jdbc.queryForList("SELECT id,name,provider FROM webhook_config WHERE enabled=1 ORDER BY id");
    }

    public List<Map<String, Object>> floorOptions() {
        return jdbc.queryForList("SELECT DISTINCT floor_code AS floorCode,floor_name AS floorName FROM stall WHERE floor_code IS NOT NULL AND floor_code<>'' ORDER BY floor_code");
    }

    // ================================================================ 时段配置
    public List<Map<String, Object>> slots() {
        return jdbc.queryForList("SELECT id,code,name,relative_to_close AS relativeToClose,start_time AS startTime,end_time AS endTime,"
                + "offset_start_min AS offsetStartMin,offset_end_min AS offsetEndMin,is_handover AS isHandover,"
                + "push_advance_minutes AS pushAdvanceMinutes,urge_after_minutes AS urgeAfterMinutes,"
                + "remind_before_end_minutes AS remindBeforeEndMinutes,overdue_after_minutes AS overdueAfterMinutes,"
                + "sort_order AS sortOrder,enabled FROM ops_patrol_slot ORDER BY sort_order,id");
    }

    public Map<String, Object> updateSlot(Long id, Map<String, Object> body) {
        int pushAdvance = num(body.get("pushAdvanceMinutes"), 15);
        int urgeAfter = num(body.get("urgeAfterMinutes"), 10);
        int remindBefore = num(body.get("remindBeforeEndMinutes"), 10);
        int overdueAfter = num(body.get("overdueAfterMinutes"), 30);
        int enabled = isTrue(body.get("enabled")) ? 1 : 0;
        String startTime = text(body.get("startTime"));
        String endTime = text(body.get("endTime"));
        Map<String, Object> slot = slotRow(id);
        if (num(slot.get("relativeToClose"), 0) == 0) {
            try { LocalTime.parse(startTime); LocalTime.parse(endTime); } catch (Exception ex) { throw BizException.badRequest("固定时段需填写 HH:mm 起止时间"); }
            jdbc.update("UPDATE ops_patrol_slot SET start_time=?,end_time=?,push_advance_minutes=?,urge_after_minutes=?,remind_before_end_minutes=?,overdue_after_minutes=?,enabled=? WHERE id=?",
                    startTime, endTime, pushAdvance, urgeAfter, remindBefore, overdueAfter, enabled, id);
        } else {
            int offsetStart = num(body.get("offsetStartMin"), 0);
            int offsetEnd = num(body.get("offsetEndMin"), 90);
            jdbc.update("UPDATE ops_patrol_slot SET offset_start_min=?,offset_end_min=?,push_advance_minutes=?,urge_after_minutes=?,remind_before_end_minutes=?,overdue_after_minutes=?,enabled=? WHERE id=?",
                    offsetStart, offsetEnd, pushAdvance, urgeAfter, remindBefore, overdueAfter, enabled, id);
        }
        return slotRow(id);
    }

    private Map<String, Object> slotRow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,code,name,relative_to_close AS relativeToClose,start_time AS startTime,end_time AS endTime,"
                + "offset_start_min AS offsetStartMin,offset_end_min AS offsetEndMin,is_handover AS isHandover,"
                + "push_advance_minutes AS pushAdvanceMinutes,urge_after_minutes AS urgeAfterMinutes,"
                + "remind_before_end_minutes AS remindBeforeEndMinutes,overdue_after_minutes AS overdueAfterMinutes,enabled "
                + "FROM ops_patrol_slot WHERE id=?", id);
        if (rows.isEmpty()) throw BizException.notFound("时段不存在");
        return rows.get(0);
    }

    // ================================================================ 检查项
    public List<Map<String, Object>> checkItems(String formType) {
        String sql = "SELECT id,code,form_type AS formType,group_name AS groupName,content,photo_required AS photoRequired,"
                + "category,due_hours AS dueHours,urgency,sort_order AS sortOrder,enabled FROM ops_patrol_check_item ";
        List<Map<String, Object>> rows = (formType == null || formType.isBlank())
                ? jdbc.queryForList(sql + "ORDER BY form_type,sort_order,id")
                : jdbc.queryForList(sql + "WHERE form_type=? ORDER BY sort_order,id", formType);
        for (Map<String, Object> row : rows) {
            row.put("slotIds", jdbc.queryForList("SELECT slot_id FROM ops_patrol_item_slot WHERE item_id=?", Long.class, row.get("id")));
        }
        return rows;
    }

    public Map<String, Object> saveCheckItem(Long id, Map<String, Object> body) {
        String formType = text(body.get("formType"));
        if (!formType.equals("RETAIL") && !formType.equals("FOOD")) throw BizException.badRequest("表单类型应为 RETAIL 或 FOOD");
        String groupName = text(body.get("groupName"));
        String content = text(body.get("content"));
        if (groupName.isBlank()) throw BizException.badRequest("请填写分组");
        if (content.isBlank()) throw BizException.badRequest("请填写检查项内容");
        String category = text(body.get("category"));
        if (category.isBlank()) category = "商户管理";
        int dueHours = Math.max(1, Math.min(720, num(body.get("dueHours"), 24)));
        String urgency = "URGENT".equals(text(body.get("urgency"))) ? "URGENT" : "NORMAL";
        int photoRequired = isTrue(body.get("photoRequired")) ? 1 : 0;
        int sortOrder = num(body.get("sortOrder"), 0);
        int enabled = isTrue(body.get("enabled")) ? 1 : 0;
        long itemId;
        if (id == null) {
            String code = text(body.get("code"));
            if (code.isBlank()) code = nextItemCode(formType);
            Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_check_item WHERE code=?", Long.class, code);
            if (dup != null && dup > 0) throw BizException.badRequest("检查项编码已存在");
            itemId = insert("INSERT INTO ops_patrol_check_item(code,form_type,group_name,content,photo_required,category,due_hours,urgency,sort_order,enabled) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    code, formType, groupName, content, photoRequired, category, dueHours, urgency, sortOrder, enabled);
        } else {
            itemId = id;
            jdbc.update("UPDATE ops_patrol_check_item SET form_type=?,group_name=?,content=?,photo_required=?,category=?,due_hours=?,urgency=?,sort_order=?,enabled=? WHERE id=?",
                    formType, groupName, content, photoRequired, category, dueHours, urgency, sortOrder, enabled, id);
        }
        jdbc.update("DELETE FROM ops_patrol_item_slot WHERE item_id=?", itemId);
        Object slotIds = body.get("slotIds");
        if (slotIds instanceof List<?> list) {
            for (Object sid : list) {
                if (sid instanceof Number n) jdbc.update("INSERT IGNORE INTO ops_patrol_item_slot(item_id,slot_id) VALUES(?,?)", itemId, n.longValue());
            }
        }
        return checkItems(null).stream().filter(r -> itemId == ((Number) r.get("id")).longValue()).findFirst().orElseThrow();
    }

    public void deleteCheckItem(Long id) {
        jdbc.update("DELETE FROM ops_patrol_item_slot WHERE item_id=?", id);
        jdbc.update("DELETE FROM ops_patrol_check_item WHERE id=?", id);
    }

    private String nextItemCode(String formType) {
        String prefix = "FOOD".equals(formType) ? "F" : "R";
        List<String> codes = jdbc.queryForList("SELECT code FROM ops_patrol_check_item WHERE code REGEXP ? ORDER BY code DESC LIMIT 1",
                String.class, "^" + prefix + "[0-9]+$");
        int next = 1;
        if (!codes.isEmpty()) next = Integer.parseInt(codes.get(0).substring(1)) + 1;
        return prefix + String.format("%02d", next);
    }

    // ================================================================ 排班
    public Map<String, Object> duties(String month) {
        String value = month == null || month.isBlank() ? LocalDate.now().format(MONTH) : month.trim();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.id,d.duty_date AS dutyDate,d.dept_id AS deptId,p.name AS deptName,d.user_id AS userId,d.user_name AS userName,"
                        + "d.backup_users_json AS backupUsersJson,d.remark FROM ops_patrol_duty d JOIN ops_patrol_dept p ON p.id=d.dept_id "
                        + "WHERE DATE_FORMAT(d.duty_date,'%Y-%m')=? ORDER BY d.duty_date,d.dept_id", value);
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT DISTINCT u.id,COALESCE(NULLIF(u.display_name,''),u.username) AS displayName "
                        + "FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE r.code IN ('APPLICANT','OPS_PATROL_MANAGER') AND u.status=1 ORDER BY u.id");
        if (users.isEmpty()) {
            users = jdbc.queryForList("SELECT u.id,COALESCE(NULLIF(u.display_name,''),u.username) AS displayName FROM sys_user u WHERE u.status=1 ORDER BY u.id");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", value);
        result.put("rows", rows);
        result.put("users", users);
        result.put("depts", depts());
        return result;
    }

    public Map<String, Object> saveDuty(Map<String, Object> body) {
        String dutyDate = text(body.get("dutyDate"));
        Long deptId = body.get("deptId") instanceof Number n ? n.longValue() : null;
        if (dutyDate.isBlank() || deptId == null) throw BizException.badRequest("请选择日期与处室");
        Long userId = body.get("userId") instanceof Number n && n.longValue() > 0 ? n.longValue() : null;
        String userName = text(body.get("userName"));
        String backupJson = body.get("backupUsers") == null ? null : body.get("backupUsers").toString();
        if (body.get("backupUsers") instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    if (!first) sb.append(',');
                    sb.append("{\"id\":").append(m.get("id")).append(",\"name\":\"").append(text(m.get("name")).replace("\"", "")).append("\"}");
                    first = false;
                }
            }
            backupJson = sb.append("]").toString();
        }
        jdbc.update("INSERT INTO ops_patrol_duty(duty_date,dept_id,user_id,user_name,backup_users_json,remark) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),user_name=VALUES(user_name),backup_users_json=VALUES(backup_users_json),remark=VALUES(remark)",
                dutyDate, deptId, userId, userName.isBlank() ? null : userName, backupJson, text(body.get("remark")));
        return Map.of("ok", true);
    }

    public void deleteDuty(Long id) {
        jdbc.update("DELETE FROM ops_patrol_duty WHERE id=?", id);
    }

    // ================================================================ 店铺联想
    // 以「商户账户管理」的商户名称为准：user_name 形如 3066-000001-沪上阿姨，
    // 复用 MerchantNameParser 去掉 3066 前缀、6 位商户编码与旧铺位前缀，并剥离“（停用）”等状态后缀。
    public List<Map<String, Object>> stores(String keyword, Long deptId) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isBlank()) return List.of();
        String probe = kw.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> stalls = jdbc.queryForList(
                "SELECT id,stall_code AS stallCode,stall_name AS stallName,floor_code AS floorCode,floor_name AS floorName FROM stall");
        List<String> deptFloors = deptId == null ? List.of()
                : jdbc.queryForList("SELECT floor_code FROM ops_patrol_dept_floor WHERE dept_id=?", String.class, deptId);
        List<Map<String, Object>> accounts = jdbc.queryForList(
                "SELECT id,account_number AS accountNumber,account_name AS accountName,user_name AS userName,"
                        + "merchant_code AS merchantCode,platform_status AS platformStatus FROM platform_account_current");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> account : accounts) {
            String accountNumber = text(account.get("accountNumber"));
            String merchantCode = text(account.get("merchantCode"));
            String accountName = text(account.get("accountName"));
            String rawName = text(account.get("userName")).isBlank() ? accountName : text(account.get("userName"));
            String merchantName = merchantDisplayName(rawName, accountName, merchantCode);
            boolean disabled = "0".equals(text(account.get("platformStatus")))
                    || ACCOUNT_STATUS_SUFFIX.matcher(rawName).find()
                    || rawName.contains("停用");
            boolean hit = containsIgnoreCase(merchantName, probe)
                    || containsIgnoreCase(merchantCode, probe)
                    || containsIgnoreCase(accountNumber, probe);
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Map<String, Object> stall : stalls) {
                if (containsStallCode(accountName, text(stall.get("stallCode")))) matched.add(stall);
            }
            if (matched.isEmpty()) {
                if (hit) rows.add(storeRow(account, merchantName, disabled, null, deptFloors));
                continue;
            }
            for (Map<String, Object> stall : matched) {
                if (hit || containsIgnoreCase(text(stall.get("stallCode")), probe)
                        || containsIgnoreCase(text(stall.get("floorName")), probe)) {
                    rows.add(storeRow(account, merchantName, disabled, stall, deptFloors));
                }
            }
        }
        rows.sort((a, b) -> {
            int ai = deptFloors.contains(text(a.get("floorCode"))) ? 0 : 1;
            int bi = deptFloors.contains(text(b.get("floorCode"))) ? 0 : 1;
            if (ai != bi) return ai - bi;
            int cmp = text(a.get("floorCode")).compareTo(text(b.get("floorCode")));
            if (cmp != 0) return cmp;
            return text(a.get("stallCode")).compareTo(text(b.get("stallCode")));
        });
        return rows.size() > 20 ? rows.subList(0, 20) : rows;
    }

    private Map<String, Object> storeRow(Map<String, Object> account, String merchantName, boolean disabled,
                                         Map<String, Object> stall, List<String> deptFloors) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", account.get("id"));
        row.put("accountNumber", text(account.get("accountNumber")));
        row.put("merchantCode", text(account.get("merchantCode")));
        row.put("merchantName", merchantName);
        row.put("accountNameRaw", text(account.get("userName")).isBlank() ? text(account.get("accountName")) : text(account.get("userName")));
        row.put("disabled", disabled);
        row.put("stallId", stall == null ? null : stall.get("id"));
        row.put("stallCode", stall == null ? "" : text(stall.get("stallCode")));
        row.put("floorCode", stall == null ? "" : text(stall.get("floorCode")));
        row.put("floorName", stall == null ? "" : text(stall.get("floorName")));
        row.put("deptFloor", stall != null && deptFloors.contains(text(stall.get("floorCode"))));
        row.put("displayName", stall == null ? merchantName
                : merchantName + "（" + text(stall.get("stallCode")) + "）");
        return row;
    }

    private String merchantDisplayName(String rawName, String accountName, String merchantCode) {
        String name = MerchantNameParser.extract(rawName, merchantCode);
        if (name == null || name.isBlank()) name = MerchantNameParser.extract(accountName, merchantCode);
        if (name == null) name = "";
        name = ACCOUNT_STATUS_SUFFIX.matcher(name.trim()).replaceAll("").trim();
        return name.isBlank() ? accountName : name;
    }

    /** 账户旧字段 account_name 的前段常带铺位号（L404-、L521,L522-），按边界匹配本地铺位。 */
    private boolean containsStallCode(String accountName, String stallCode) {
        if (accountName == null || accountName.isBlank() || stallCode == null || stallCode.isBlank()) return false;
        String text = accountName.toUpperCase(Locale.ROOT);
        String code = stallCode.trim().toUpperCase(Locale.ROOT);
        int from = 0;
        while ((from = text.indexOf(code, from)) >= 0) {
            int end = from + code.length();
            boolean left = from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1));
            boolean right = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            from++;
        }
        return false;
    }

    private boolean containsIgnoreCase(String value, String probe) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(probe);
    }

    // ================================================================ 任务生成
    public void generateTasks(LocalDate date) {
        List<Map<String, Object>> depts = jdbc.queryForList("SELECT * FROM ops_patrol_dept WHERE enabled=1 ORDER BY sort_order,id");
        List<Map<String, Object>> slots = jdbc.queryForList("SELECT * FROM ops_patrol_slot WHERE enabled=1 ORDER BY sort_order,id");
        for (Map<String, Object> dept : depts) {
            Long deptId = ((Number) dept.get("id")).longValue();
            LocalTime closeTime = LocalTime.parse(text(dept.get("close_time")).isBlank() ? "21:30" : text(dept.get("close_time")));
            Map<String, Object> duty = findDuty(date, deptId);
            for (Map<String, Object> slot : slots) {
                Long slotId = ((Number) slot.get("id")).longValue();
                Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_task WHERE task_date=? AND dept_id=? AND slot_id=?",
                        Long.class, date, deptId, slotId);
                if (dup != null && dup > 0) continue;
                LocalDateTime[] range = slotRange(date, slot, closeTime);
                int pushAdvance = num(slot.get("push_advance_minutes"), 15);
                LocalDateTime pushAt = range[0].minusMinutes(pushAdvance);
                int isHandover = num(slot.get("is_handover"), 0);
                long taskId = insert("INSERT INTO ops_patrol_task(task_date,dept_id,slot_id,is_handover,assignee_user_id,assignee_name,entry_token,push_at,plan_start_at,plan_end_at,status) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING')",
                        date, deptId, slotId, isHandover,
                        duty == null ? null : duty.get("user_id"), duty == null ? null : duty.get("user_name"),
                        randomToken(), pushAt, range[0], range[1]);
                if (isHandover == 1) {
                    createHandover(date, deptId, taskId, duty);
                } else {
                    snapshotItems(taskId, text(dept.get("form_type")), slotId);
                }
            }
        }
    }

    private Map<String, Object> findDuty(LocalDate date, Long deptId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT user_id,user_name FROM ops_patrol_duty WHERE duty_date=? AND dept_id=?", date, deptId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LocalDateTime[] slotRange(LocalDate date, Map<String, Object> slot, LocalTime closeTime) {
        if (num(slot.get("relative_to_close"), 0) == 1) {
            LocalDateTime closeAt = date.atTime(closeTime);
            return new LocalDateTime[]{closeAt.plusMinutes(num(slot.get("offset_start_min"), 0)), closeAt.plusMinutes(num(slot.get("offset_end_min"), 90))};
        }
        return new LocalDateTime[]{date.atTime(LocalTime.parse(text(slot.get("start_time")))), date.atTime(LocalTime.parse(text(slot.get("end_time"))))};
    }

    private void snapshotItems(long taskId, String formType, Long slotId) {
        jdbc.update("INSERT INTO ops_patrol_task_item(task_id,item_id,item_code,item_content,group_name,photo_required,category,due_hours,urgency,sort_order) "
                        + "SELECT ?,i.id,i.code,i.content,i.group_name,i.photo_required,i.category,i.due_hours,i.urgency,i.sort_order "
                        + "FROM ops_patrol_check_item i JOIN ops_patrol_item_slot r ON r.item_id=i.id "
                        + "WHERE i.form_type=? AND r.slot_id=? AND i.enabled=1 ORDER BY i.sort_order,i.id",
                taskId, formType, slotId);
    }

    private void createHandover(LocalDate date, Long deptId, long taskId, Map<String, Object> duty) {
        Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_handover WHERE handover_date=? AND dept_id=?", Long.class, date, deptId);
        if (dup != null && dup > 0) return;
        jdbc.update("INSERT INTO ops_patrol_handover(handover_date,dept_id,task_id,from_user_id,from_user_name) VALUES(?,?,?,?,?)",
                date, deptId, taskId, duty == null ? null : duty.get("user_id"), duty == null ? null : duty.get("user_name"));
        refreshHandover(date, deptId);
    }

    private void refreshHandover(LocalDate date, Long deptId) {
        List<Map<String, Object>> issues = jdbc.queryForList(
                "SELECT id FROM ops_patrol_issue WHERE dept_id=? AND status<>'CLOSED' AND submitted_at<=?", deptId, Timestamp.valueOf(date.atTime(14, 30)));
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : issues) ids.add(String.valueOf(row.get("id")));
        Map<String, Object> stats = jdbc.queryForMap(
                "SELECT COALESCE(SUM(pass_count),0) AS passCount,COALESCE(SUM(fail_count),0) AS failCount FROM ops_patrol_task "
                        + "WHERE task_date=? AND dept_id=? AND is_handover=0 AND status='DONE'", date, deptId);
        jdbc.update("UPDATE ops_patrol_handover SET unclosed_issue_ids=?,unclosed_count=?,pass_count=?,fail_count=? WHERE handover_date=? AND dept_id=? AND confirmed=0",
                String.join(",", ids), ids.size(), stats.get("passCount"), stats.get("failCount"), date, deptId);
    }

    // ================================================================ 定时扫描
    public void pushDueTasks() {
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.*,d.name AS deptName,d.form_type AS formType,d.close_time AS closeTime,s.name AS slotName,s.code AS slotCode,s.is_handover AS isHandover "
                        + "FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id "
                        + "WHERE t.status='PENDING' AND t.pushed_at IS NULL AND t.push_at<=NOW() AND d.enabled=1 ORDER BY t.push_at LIMIT 50");
        for (Map<String, Object> task : tasks) {
            try {
                if (num(task.get("isHandover"), 0) == 1) {
                    refreshHandover(toDate(task.get("task_date")), ((Number) task.get("dept_id")).longValue());
                    notifyService.pushHandover(task, handoverOf(task));
                } else {
                    int itemCount = countItems(((Number) task.get("id")).longValue());
                    notifyService.pushTask(task, itemCount);
                }
                jdbc.update("UPDATE ops_patrol_task SET pushed_at=NOW() WHERE id=?", task.get("id"));
            } catch (Exception ex) {
                log.error("ops patrol push task {} failed: {}", task.get("id"), ex.getMessage(), ex);
            }
        }
    }

    public void urgeDueTasks() {
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.*,d.name AS deptName,s.name AS slotName,s.urge_after_minutes AS urgeAfter "
                        + "FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id "
                        + "WHERE t.status IN ('PENDING','IN_PROGRESS') AND t.urge_sent=0 AND t.finished_at IS NULL "
                        + "AND DATE_ADD(t.plan_start_at, INTERVAL s.urge_after_minutes MINUTE)<=NOW() AND t.plan_end_at>NOW() LIMIT 50");
        for (Map<String, Object> task : tasks) {
            try {
                notifyService.pushUrge(task, countRemaining(((Number) task.get("id")).longValue()));
                jdbc.update("UPDATE ops_patrol_task SET urge_sent=1 WHERE id=?", task.get("id"));
            } catch (Exception ex) {
                log.error("ops patrol urge task {} failed: {}", task.get("id"), ex.getMessage(), ex);
            }
        }
    }

    public void remindDueTasks() {
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.*,d.name AS deptName,s.name AS slotName "
                        + "FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id "
                        + "WHERE t.status IN ('PENDING','IN_PROGRESS') AND t.remind_sent=0 AND t.finished_at IS NULL "
                        + "AND DATE_SUB(t.plan_end_at, INTERVAL s.remind_before_end_minutes MINUTE)<=NOW() AND t.plan_end_at>NOW() LIMIT 50");
        for (Map<String, Object> task : tasks) {
            try {
                notifyService.pushRemind(task, countRemaining(((Number) task.get("id")).longValue()));
                jdbc.update("UPDATE ops_patrol_task SET remind_sent=1 WHERE id=?", task.get("id"));
            } catch (Exception ex) {
                log.error("ops patrol remind task {} failed: {}", task.get("id"), ex.getMessage(), ex);
            }
        }
    }

    public void markOverdueTasks() {
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.*,d.name AS deptName,s.name AS slotName "
                        + "FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id "
                        + "WHERE t.status IN ('PENDING','IN_PROGRESS') AND t.finished_at IS NULL "
                        + "AND DATE_ADD(t.plan_end_at, INTERVAL s.overdue_after_minutes MINUTE)<=NOW() LIMIT 50");
        for (Map<String, Object> task : tasks) {
            jdbc.update("UPDATE ops_patrol_task SET status='OVERDUE' WHERE id=?", task.get("id"));
            try {
                notifyService.pushTaskOverdue(task);
            } catch (Exception ex) {
                log.error("ops patrol overdue notify task {} failed: {}", task.get("id"), ex.getMessage(), ex);
            }
        }
    }

    public void markOverdueIssues() {
        List<Map<String, Object>> issues = jdbc.queryForList(
                "SELECT i.*,d.name AS deptName FROM ops_patrol_issue i JOIN ops_patrol_dept d ON d.id=i.dept_id "
                        + "WHERE i.status IN ('PENDING','RECTIFYING') AND i.overdue_flag=0 AND i.due_at IS NOT NULL AND i.due_at<NOW() LIMIT 50");
        for (Map<String, Object> issue : issues) {
            jdbc.update("UPDATE ops_patrol_issue SET overdue_flag=1 WHERE id=?", issue.get("id"));
            try {
                notifyService.pushIssueOverdue(issue);
            } catch (Exception ex) {
                log.error("ops patrol issue overdue notify {} failed: {}", issue.get("id"), ex.getMessage(), ex);
            }
        }
    }

    private int countItems(long taskId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_task_item WHERE task_id=?", Long.class, taskId);
        return count == null ? 0 : count.intValue();
    }

    private int countRemaining(long taskId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_task_item WHERE task_id=? AND result IS NULL", Long.class, taskId);
        return count == null ? 0 : count.intValue();
    }

    private Map<String, Object> handoverOf(Map<String, Object> task) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT h.*, (SELECT COUNT(*) FROM ops_patrol_issue i WHERE i.dept_id=h.dept_id AND i.status<>'CLOSED') AS unclosedCount "
                        + "FROM ops_patrol_handover h WHERE h.task_id=?", task.get("id"));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // ================================================================ 任务看板（管理端）
    public Map<String, Object> taskBoard(String date) {
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.id,t.task_date AS taskDate,t.dept_id AS deptId,d.name AS deptName,d.form_type AS formType,"
                        + "t.slot_id AS slotId,s.code AS slotCode,s.name AS slotName,t.is_handover AS isHandover,"
                        + "t.assignee_name AS assigneeName,t.plan_start_at AS planStartAt,t.plan_end_at AS planEndAt,t.pushed_at AS pushedAt,"
                        + "t.status,t.started_at AS startedAt,t.finished_at AS finishedAt,t.late_minutes AS lateMinutes,"
                        + "t.pass_count AS passCount,t.fail_count AS failCount,t.skip_count AS skipCount,t.submitter_name AS submitterName,"
                        + "t.exempt_reason AS exemptReason,t.entry_token AS entryToken "
                        + "FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id "
                        + "WHERE t.task_date=? ORDER BY d.sort_order,s.sort_order", day);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day.toString());
        result.put("depts", depts());
        result.put("slots", slots());
        result.put("tasks", tasks);
        return result;
    }

    public Map<String, Object> taskDetail(Long id) {
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT t.*,d.name AS deptName,d.form_type AS formType,s.name AS slotName FROM ops_patrol_task t "
                        + "JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id WHERE t.id=?", id);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT i.*,iss.issue_no AS issueNo,iss.merchant_name AS merchantName,iss.status AS issueStatus "
                        + "FROM ops_patrol_task_item i LEFT JOIN ops_patrol_issue iss ON iss.id=i.issue_id WHERE i.task_id=? ORDER BY i.sort_order,i.id", id);
        attachFailures(id, items);
        task.put("items", items);
        return task;
    }

    /** 给检查项挂上该任务内所有问题单（一项可对应多家商户）。 */
    private void attachFailures(Long taskId, List<Map<String, Object>> items) {
        List<Map<String, Object>> issueRows = jdbc.queryForList(
                "SELECT id,task_item_id AS taskItemId,issue_no AS issueNo,account_number AS accountNumber,merchant_code AS merchantCode,"
                        + "merchant_name AS merchantName,stall_code AS stallCode,floor_name AS floorName,description,status "
                        + "FROM ops_patrol_issue WHERE task_id=? ORDER BY id", taskId);
        Map<Long, List<Map<String, Object>>> failuresByItem = new LinkedHashMap<>();
        for (Map<String, Object> issue : issueRows) {
            if (!(issue.get("taskItemId") instanceof Number n)) continue;
            failuresByItem.computeIfAbsent(n.longValue(), k -> new ArrayList<>()).add(issue);
        }
        for (Map<String, Object> item : items) {
            if (item.get("id") instanceof Number n) {
                item.put("failures", failuresByItem.getOrDefault(n.longValue(), List.of()));
            }
        }
    }

    public void pushTaskNow(Long id) {
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT t.*,d.name AS deptName,s.name AS slotName,s.is_handover AS isHandover FROM ops_patrol_task t "
                        + "JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id WHERE t.id=?", id);
        if (num(task.get("is_handover"), 0) == 1) {
            refreshHandover(toDate(task.get("task_date")), ((Number) task.get("dept_id")).longValue());
            notifyService.pushHandover(task, handoverOf(task));
        } else {
            notifyService.pushTask(task, countItems(id));
        }
        jdbc.update("UPDATE ops_patrol_task SET pushed_at=NOW() WHERE id=?", id);
    }

    public void exemptTask(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw BizException.badRequest("请填写豁免原因");
        jdbc.update("UPDATE ops_patrol_task SET status='EXEMPT',exempt_reason=?,finished_at=NOW() WHERE id=? AND status IN ('PENDING','IN_PROGRESS','OVERDUE')", reason.trim(), id);
    }

    // ================================================================ 测试数据删除（仅超级管理员）
    /** 超级管理员删除测试数据：任务连同检查项快照、问题单、问题照片与流转日志一并物理删除。 */
    @Transactional
    public void deleteTask(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ops_patrol_task WHERE id=?", taskId);
        if (rows.isEmpty()) throw BizException.notFound("任务不存在");
        List<Long> issueIds = jdbc.queryForList("SELECT id FROM ops_patrol_issue WHERE task_id=?", Long.class, taskId);
        for (Long issueId : issueIds) deleteIssueData(issueId);
        jdbc.update("DELETE FROM ops_patrol_issue WHERE task_id=?", taskId);
        jdbc.update("UPDATE ops_patrol_handover SET task_id=NULL WHERE task_id=?", taskId);
        jdbc.update("DELETE FROM ops_patrol_task_item WHERE task_id=?", taskId);
        jdbc.update("DELETE FROM ops_patrol_task WHERE id=?", taskId);
    }

    /** 超级管理员删除测试数据：仅删问题单（保留任务与检查项结果）。 */
    @Transactional
    public void deleteIssue(Long issueId) {
        issueRow(issueId);
        deleteIssueData(issueId);
        jdbc.update("UPDATE ops_patrol_task_item SET issue_id=NULL WHERE issue_id=?", issueId);
        jdbc.update("DELETE FROM ops_patrol_issue WHERE id=?", issueId);
    }

    private void deleteIssueData(Long issueId) {
        attachmentService.deleteByBusiness("OPS_ISSUE", issueId);
        jdbc.update("DELETE FROM ops_patrol_issue_log WHERE issue_id=?", issueId);
    }

    // ================================================================ H5 填报（token 免登录）
    public Map<String, Object> publicEntry(String token) {
        Map<String, Object> ctx = tokenContext(token);
        Long deptId = ((Number) ctx.get("deptId")).longValue();
        LocalDate date = (LocalDate) ctx.get("date");
        Map<String, Object> dept = jdbc.queryForMap("SELECT id,code,name,form_type AS formType,close_time AS closeTime FROM ops_patrol_dept WHERE id=?", deptId);
        dept.put("floors", jdbc.queryForList("SELECT floor_code AS floorCode,floor_name AS floorName FROM ops_patrol_dept_floor WHERE dept_id=? ORDER BY sort_order,id", deptId));
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT t.id,t.slot_id AS slotId,s.code AS slotCode,s.name AS slotName,t.is_handover AS isHandover,"
                        + "t.plan_start_at AS planStartAt,t.plan_end_at AS planEndAt,t.status,t.assignee_name AS assigneeName,"
                        + "t.pass_count AS passCount,t.fail_count AS failCount,t.skip_count AS skipCount,t.submitter_name AS submitterName "
                        + "FROM ops_patrol_task t JOIN ops_patrol_slot s ON s.id=t.slot_id WHERE t.dept_id=? AND t.task_date=? ORDER BY s.sort_order",
                deptId, date);
        for (Map<String, Object> task : tasks) {
            long taskId = ((Number) task.get("id")).longValue();
            task.put("itemCount", countItems(taskId));
            task.put("doneCount", countItems(taskId) - countRemaining(taskId));
            if (num(task.get("isHandover"), 0) == 1) {
                List<Map<String, Object>> handovers = jdbc.queryForList("SELECT id,unclosed_count AS unclosedCount,confirmed FROM ops_patrol_handover WHERE task_id=?", taskId);
                task.put("handover", handovers.isEmpty() ? null : handovers.get(0));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dept", dept);
        result.put("date", date.toString());
        result.put("tasks", tasks);
        result.put("now", LocalDateTime.now().toString());
        return result;
    }

    public Map<String, Object> publicTask(String token, Long taskId) {
        Map<String, Object> ctx = tokenContext(token);
        Map<String, Object> task = publicTaskRow(taskId);
        checkTaskScope(ctx, task);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT i.id,i.item_code AS itemCode,i.item_content AS itemContent,i.group_name AS groupName,i.photo_required AS photoRequired,"
                        + "i.result,i.note,i.issue_id AS issueId,iss.merchant_id AS merchantId,iss.merchant_name AS merchantName,"
                        + "iss.stall_id AS stallId,iss.stall_code AS stallCode,iss.floor_name AS floorName,iss.description AS issueDescription "
                        + "FROM ops_patrol_task_item i LEFT JOIN ops_patrol_issue iss ON iss.id=i.issue_id WHERE i.task_id=? ORDER BY i.sort_order,i.id", taskId);
        attachFailures(taskId, items);
        task.put("items", items);
        if (num(task.get("isHandover"), 0) == 1) {
            Map<String, Object> handover = handoverOf(task);
            if (!handover.isEmpty()) {
                List<String> ids = csv(text(handover.get("unclosed_issue_ids")));
                List<Map<String, Object>> issues = ids.isEmpty() ? List.of() : jdbc.queryForList(
                        "SELECT id,issue_no AS issueNo,merchant_name AS merchantName,merchant_code AS merchantCode,stall_code AS stallCode,category,description,status,due_at AS dueAt FROM ops_patrol_issue WHERE id IN (" + String.join(",", ids) + ")");
                handover.put("issues", issues);
            }
            task.put("handover", handover);
        }
        return task;
    }

    public Map<String, Object> publicSaveItems(String token, Long taskId, Map<String, Object> body, String ip) {
        Map<String, Object> ctx = tokenContext(token);
        Map<String, Object> task = publicTaskRow(taskId);
        checkTaskScope(ctx, task);
        String status = text(task.get("status"));
        if ("DONE".equals(status) || "EXEMPT".equals(status)) throw BizException.badRequest("任务已结束，无法继续填报");
        String submitterName = text(body.get("submitterName"));
        if (submitterName.isBlank()) throw BizException.badRequest("请填写填报人姓名");
        if ("PENDING".equals(status)) {
            jdbc.update("UPDATE ops_patrol_task SET status='IN_PROGRESS',started_at=NOW(),submitter_name=?,submitter_id=?,submit_ip=? WHERE id=?",
                    submitterName, body.get("submitterId") instanceof Number n ? n.longValue() : null, ip, taskId);
        } else {
            jdbc.update("UPDATE ops_patrol_task SET submitter_name=?,submitter_id=?,submit_ip=? WHERE id=?",
                    submitterName, body.get("submitterId") instanceof Number n ? n.longValue() : null, ip, taskId);
        }
        Object items = body.get("items");
        if (!(items instanceof List<?> list) || list.isEmpty()) throw BizException.badRequest("没有需要保存的检查结果");
        Long deptId = ((Number) task.get("dept_id")).longValue();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) continue;
            String itemCode = text(row.get("itemCode"));
            String result = text(row.get("result"));
            if (!result.equals("PASS") && !result.equals("FAIL") && !result.equals("NA")) continue;
            List<Map<String, Object>> found = jdbc.queryForList("SELECT * FROM ops_patrol_task_item WHERE task_id=? AND item_code=?", taskId, itemCode);
            if (found.isEmpty()) continue;
            Map<String, Object> item = found.get(0);
            String note = text(row.get("note"));
            Long issueId = item.get("issue_id") instanceof Number n ? n.longValue() : null;
            if ("FAIL".equals(result)) {
                String description = text(row.get("description"));
                List<Map<String, Object>> failures = failuresOf(row, description);
                if (failures.isEmpty()) throw BizException.badRequest("不合格项【" + text(item.get("item_content")) + "】需填写问题描述");
                Long firstIssueId = issueId;
                for (Map<String, Object> failure : failures) {
                    String desc = text(failure.get("description"));
                    if (desc.isBlank()) throw BizException.badRequest("不合格项【" + text(item.get("item_content")) + "】的每个异常商户都需填写问题描述");
                    Long existing = findPendingIssue(item.get("id"), failure);
                    if (existing == null) {
                        Long created = createIssue(task, item, failure, desc, submitterName,
                                body.get("submitterId") instanceof Number n2 ? n2.longValue() : null);
                        if (firstIssueId == null) firstIssueId = created;
                    } else {
                        updateIssueStore(existing, failure, desc);
                    }
                }
                if (firstIssueId != null) {
                    jdbc.update("UPDATE ops_patrol_task_item SET issue_id=? WHERE id=?", firstIssueId, item.get("id"));
                }
            }
            jdbc.update("UPDATE ops_patrol_task_item SET result=?,note=? WHERE id=?", result, note, item.get("id"));
        }
        refreshTaskCounts(taskId);
        return publicTask(token, taskId);
    }

    public Map<String, Object> publicSubmit(String token, Long taskId, Map<String, Object> body, String ip) {
        Map<String, Object> ctx = tokenContext(token);
        Map<String, Object> task = publicTaskRow(taskId);
        checkTaskScope(ctx, task);
        String status = text(task.get("status"));
        if ("DONE".equals(status) || "EXEMPT".equals(status)) throw BizException.badRequest("任务已提交");
        if (num(task.get("is_handover"), 0) == 1) throw BizException.badRequest("交接时段请在交接确认中提交");
        String submitterName = text(body.get("submitterName"));
        if (submitterName.isBlank()) submitterName = text(task.get("submitter_name"));
        int remaining = countRemaining(taskId);
        boolean force = isTrue(body.get("force"));
        if (remaining > 0 && !force) throw BizException.badRequest("还有 " + remaining + " 项未检查，确认提交将记为漏检");
        if (remaining > 0) {
            jdbc.update("UPDATE ops_patrol_task_item SET result='NA',note=COALESCE(NULLIF(note,''),'漏检') WHERE task_id=? AND result IS NULL", taskId);
        }
        refreshTaskCounts(taskId);
        LocalDateTime planEnd = toLocal(task.get("plan_end_at"));
        long late = planEnd == null ? 0 : Math.max(0, java.time.Duration.between(planEnd, LocalDateTime.now()).toMinutes());
        jdbc.update("UPDATE ops_patrol_task SET status='DONE',finished_at=NOW(),late_minutes=?,submitter_name=?,submit_ip=? WHERE id=?",
                late, submitterName, ip, taskId);
        return publicEntry(token);
    }

    public Map<String, Object> publicConfirmHandover(String token, Long taskId, Map<String, Object> body, String ip) {
        Map<String, Object> ctx = tokenContext(token);
        Map<String, Object> task = publicTaskRow(taskId);
        checkTaskScope(ctx, task);
        if (num(task.get("is_handover"), 0) != 1) throw BizException.badRequest("该任务不是交接任务");
        String confirmer = text(body.get("submitterName"));
        if (confirmer.isBlank()) throw BizException.badRequest("请填写确认人姓名");
        Map<String, Object> handover = handoverOf(task);
        if (handover.isEmpty()) throw BizException.notFound("交接单不存在");
        jdbc.update("UPDATE ops_patrol_handover SET confirmed=1,confirmed_at=NOW(),confirmed_by=?,remark=? WHERE id=?",
                confirmer, text(body.get("remark")), handover.get("id"));
        jdbc.update("UPDATE ops_patrol_task SET status='DONE',finished_at=NOW(),submitter_name=?,submit_ip=? WHERE id=?", confirmer, ip, taskId);
        return publicEntry(token);
    }

    public List<Map<String, Object>> publicStores(String token, String keyword) {
        Map<String, Object> ctx = tokenContext(token);
        return stores(keyword, ((Number) ctx.get("deptId")).longValue());
    }

    public List<Map<String, Object>> publicUploadPhotos(String token, Long issueId, MultipartFile[] photos) {
        Map<String, Object> ctx = tokenContext(token);
        List<Map<String, Object>> issues = jdbc.queryForList("SELECT id,dept_id FROM ops_patrol_issue WHERE id=?", issueId);
        if (issues.isEmpty()) throw BizException.notFound("问题不存在");
        Long deptId = ((Number) issues.get(0).get("dept_id")).longValue();
        if (!deptId.equals(((Number) ctx.get("deptId")).longValue())) throw BizException.badRequest("无权访问该问题");
        return attachmentService.saveAll("OPS_ISSUE", issueId, photos);
    }

    public Resource publicAttachment(String token, Long attachmentId) {
        Map<String, Object> ctx = tokenContext(token);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id FROM patrol_attachment a JOIN ops_patrol_issue i ON i.id=a.business_id "
                        + "WHERE a.id=? AND a.business_type='OPS_ISSUE' AND i.dept_id=?", attachmentId, ctx.get("deptId"));
        if (rows.isEmpty()) throw BizException.notFound("照片不存在");
        return attachmentService.load(attachmentId);
    }

    public String attachmentMime(Long attachmentId) {
        return attachmentService.mimeType(attachmentId);
    }

    private Map<String, Object> tokenContext(String token) {
        if (token == null || token.isBlank()) throw BizException.badRequest("链接无效");
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT dept_id,task_date FROM ops_patrol_task WHERE entry_token=?", token.trim());
        if (tasks.isEmpty()) throw BizException.badRequest("链接无效或已过期");
        Map<String, Object> task = tasks.get(0);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("deptId", task.get("dept_id"));
        ctx.put("date", toDate(task.get("task_date")));
        return ctx;
    }

    private Map<String, Object> publicTaskRow(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id,t.dept_id,t.task_date,t.is_handover,t.status,t.submitter_name,t.plan_start_at,t.plan_end_at,s.name AS slotName,d.name AS deptName "
                        + "FROM ops_patrol_task t JOIN ops_patrol_slot s ON s.id=t.slot_id JOIN ops_patrol_dept d ON d.id=t.dept_id WHERE t.id=?", taskId);
        if (rows.isEmpty()) throw BizException.notFound("任务不存在");
        return rows.get(0);
    }

    private void checkTaskScope(Map<String, Object> ctx, Map<String, Object> task) {
        long deptId = ((Number) task.get("dept_id")).longValue();
        long ctxDeptId = ((Number) ctx.get("deptId")).longValue();
        if (deptId != ctxDeptId || !toDate(task.get("task_date")).equals(ctx.get("date"))) {
            throw BizException.badRequest("链接无权访问该任务");
        }
    }

    /** 一项检查可登记多家商户异常；兼容旧的单条 description + merchant 字段写法。 */
    private List<Map<String, Object>> failuresOf(Map<?, ?> row, String description) {
        Object raw = row.get("failures");
        List<Map<String, Object>> failures = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("accountId", m.get("accountId"));
                failure.put("accountNumber", text(m.get("accountNumber")));
                failure.put("merchantCode", text(m.get("merchantCode")));
                failure.put("accountNameRaw", text(m.get("accountNameRaw")));
                failure.put("merchantName", text(m.get("merchantName")));
                failure.put("stallId", m.get("stallId"));
                failure.put("stallCode", text(m.get("stallCode")));
                failure.put("floorName", text(m.get("floorName")));
                failure.put("description", text(m.get("description")));
                failures.add(failure);
            }
            return failures;
        }
        if (description.isBlank()) return failures;
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("accountId", null);
        fallback.put("accountNumber", "");
        fallback.put("merchantCode", "");
        fallback.put("accountNameRaw", "");
        fallback.put("merchantName", text(row.get("merchantName")));
        fallback.put("stallId", row.get("stallId"));
        fallback.put("stallCode", text(row.get("stallCode")));
        fallback.put("floorName", text(row.get("floorName")));
        fallback.put("description", description);
        failures.add(fallback);
        return failures;
    }

    private Long findPendingIssue(Object taskItemId, Map<String, Object> failure) {
        String accountNumber = text(failure.get("accountNumber"));
        String stallCode = text(failure.get("stallCode"));
        if (!accountNumber.isBlank() || !stallCode.isBlank()) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM ops_patrol_issue WHERE task_item_id=? AND IFNULL(account_number,'')=? AND IFNULL(stall_code,'')=? "
                            + "AND status='PENDING' ORDER BY id LIMIT 1",
                    Long.class, taskItemId, accountNumber, stallCode);
            if (!ids.isEmpty()) return ids.get(0);
            if (!stallCode.isBlank()) {
                ids = jdbc.queryForList(
                        "SELECT id FROM ops_patrol_issue WHERE task_item_id=? AND IFNULL(stall_code,'')=? AND status='PENDING' ORDER BY id LIMIT 1",
                        Long.class, taskItemId, stallCode);
                if (!ids.isEmpty()) return ids.get(0);
            }
            return null;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM ops_patrol_issue WHERE task_item_id=? AND status='PENDING' ORDER BY id LIMIT 1",
                Long.class, taskItemId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long createIssue(Map<String, Object> task, Map<String, Object> item, Map<String, Object> failure,
                             String description, String submitterName, Long submitterId) {
        Long deptId = ((Number) task.get("dept_id")).longValue();
        String category = text(item.get("category"));
        String urgency = text(item.get("urgency"));
        int dueHours = num(item.get("due_hours"), 24);
        LocalDateTime dueAt = LocalDateTime.now().plusHours(dueHours);
        Long merchantId = failure.get("accountId") instanceof Number n ? n.longValue() : null;
        String accountNumber = text(failure.get("accountNumber"));
        String merchantName = text(failure.get("merchantName"));
        String merchantCode = text(failure.get("merchantCode"));
        String accountNameRaw = text(failure.get("accountNameRaw"));
        Long stallId = failure.get("stallId") instanceof Number n ? n.longValue() : null;
        String stallCode = text(failure.get("stallCode"));
        String floorName = text(failure.get("floorName"));
        String issueNo = nextIssueNo();
        int repeat = 0;
        String repeatKey = !accountNumber.isBlank() ? accountNumber : merchantName;
        if (!repeatKey.isBlank()) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ops_patrol_issue i JOIN ops_patrol_task_item ti ON ti.id=i.task_item_id "
                            + "WHERE (i.account_number=? OR i.merchant_name=?) AND ti.item_code=? AND i.submitted_at>DATE_SUB(NOW(), INTERVAL 7 DAY)",
                    Long.class, accountNumber, repeatKey, item.get("item_code"));
            repeat = count != null && count > 0 ? 1 : 0;
        }
        long issueId;
        for (int attempt = 1; ; attempt++) {
            try {
                issueId = insert("INSERT INTO ops_patrol_issue(issue_no,task_id,task_item_id,dept_id,merchant_id,account_number,"
                                + "merchant_name,merchant_code,account_name_raw,stall_id,stall_code,floor_name,"
                                + "category,urgency,description,status,due_at,owner_user_id,owner_name,repeat_flag,submitter_id,submitter_name) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?)",
                        issueNo, task.get("id"), item.get("id"), deptId, merchantId, accountNumber.isBlank() ? null : accountNumber,
                        merchantName.isBlank() ? null : merchantName, merchantCode.isBlank() ? null : merchantCode,
                        accountNameRaw.isBlank() ? null : accountNameRaw,
                        stallId, stallCode.isBlank() ? null : stallCode, floorName.isBlank() ? null : floorName,
                        category, urgency.isBlank() ? "NORMAL" : urgency, description, dueAt,
                        submitterId, submitterName, repeat, submitterId, submitterName);
                break;
            } catch (DuplicateKeyException ex) {
                // 并发提交时可能取到相同流水号，重新取最大号重试
                if (attempt >= 5) throw ex;
                issueNo = nextIssueNo();
            }
        }
        jdbc.update("INSERT INTO ops_patrol_issue_log(issue_id,action,to_status,operator,note) VALUES(?,?,?,?,?)",
                issueId, "CREATE", "PENDING", submitterName, "检查项不合格：" + text(item.get("item_content")));
        try {
            notifyService.pushIssue(issueRow(issueId), text(item.get("item_content")));
        } catch (Exception ex) {
            log.error("ops patrol issue notify {} failed: {}", issueId, ex.getMessage(), ex);
        }
        return issueId;
    }

    private void updateIssueStore(Long issueId, Map<String, Object> failure, String description) {
        jdbc.update("UPDATE ops_patrol_issue SET merchant_id=?,account_number=?,merchant_name=?,merchant_code=?,account_name_raw=?,"
                        + "stall_id=?,stall_code=?,floor_name=?,description=? WHERE id=? AND status='PENDING'",
                failure.get("accountId") instanceof Number n ? n.longValue() : null,
                text(failure.get("accountNumber")), text(failure.get("merchantName")), text(failure.get("merchantCode")),
                text(failure.get("accountNameRaw")),
                failure.get("stallId") instanceof Number n ? n.longValue() : null, text(failure.get("stallCode")),
                text(failure.get("floorName")), description, issueId);
    }

    private void refreshTaskCounts(Long taskId) {
        jdbc.update("UPDATE ops_patrol_task SET "
                + "pass_count=(SELECT COUNT(*) FROM ops_patrol_task_item WHERE task_id=? AND result='PASS'),"
                + "fail_count=(SELECT COUNT(*) FROM ops_patrol_task_item WHERE task_id=? AND result='FAIL'),"
                + "skip_count=(SELECT COUNT(*) FROM ops_patrol_task_item WHERE task_id=? AND result='NA') WHERE id=?",
                taskId, taskId, taskId, taskId);
    }

    // ================================================================ 问题（管理端）
    public Map<String, Object> issues(String deptId, String status, String floor, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (deptId != null && !deptId.isBlank()) { where.append(" AND i.dept_id=?"); params.add(Long.parseLong(deptId)); }
        if (status != null && !status.isBlank()) { where.append(" AND i.status=?"); params.add(status); }
        if (floor != null && !floor.isBlank()) { where.append(" AND i.floor_name=?"); params.add(floor); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (i.merchant_name LIKE ? OR i.merchant_code LIKE ? OR i.account_number LIKE ? OR i.issue_no LIKE ? OR i.description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like); params.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ops_patrol_issue i" + where, Long.class, params.toArray());
        params.add((page - 1) * size);
        params.add(size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT i.id,i.issue_no AS issueNo,i.dept_id AS deptId,d.name AS deptName,i.merchant_name AS merchantName,i.stall_code AS stallCode,"
                        + "i.account_number AS accountNumber,i.merchant_code AS merchantCode,"
                        + "i.floor_name AS floorName,i.category,i.urgency,i.description,i.status,i.due_at AS dueAt,i.owner_name AS ownerName,"
                        + "i.overdue_flag AS overdueFlag,i.repeat_flag AS repeatFlag,i.submitter_name AS submitterName,i.submitted_at AS submittedAt,"
                        + "i.rectified_at AS rectifiedAt,i.closed_at AS closedAt "
                        + "FROM ops_patrol_issue i JOIN ops_patrol_dept d ON d.id=i.dept_id" + where + " ORDER BY i.id DESC LIMIT ?,?", params.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("rows", rows);
        return result;
    }

    public Map<String, Object> issueDetail(Long id) {
        Map<String, Object> issue = issueRow(id);
        List<Map<String, Object>> attachments = attachmentService.list("OPS_ISSUE", id);
        for (Map<String, Object> row : attachments) row.put("url", "/api/v1/ops-patrol/issues/" + id + "/attachments/" + row.get("id"));
        issue.put("attachments", attachments);
        issue.put("logs", jdbc.queryForList("SELECT action,from_status AS fromStatus,to_status AS toStatus,operator,note,created_at AS createdAt FROM ops_patrol_issue_log WHERE issue_id=? ORDER BY id", id));
        return issue;
    }

    public Resource issueAttachment(Long issueId, Long attachmentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM patrol_attachment WHERE id=? AND business_type='OPS_ISSUE' AND business_id=?", attachmentId, issueId);
        if (rows.isEmpty()) throw BizException.notFound("照片不存在");
        return attachmentService.load(attachmentId);
    }

    public Map<String, Object> assignIssue(Long id, Map<String, Object> body, String operator) {
        String ownerName = text(body.get("ownerName"));
        if (ownerName.isBlank()) throw BizException.badRequest("请填写跟进人");
        Long ownerId = body.get("ownerId") instanceof Number n ? n.longValue() : null;
        jdbc.update("UPDATE ops_patrol_issue SET owner_user_id=?,owner_name=?,status=IF(status='PENDING','RECTIFYING',status) WHERE id=?", ownerId, ownerName, id);
        log(id, "ASSIGN", "PENDING", "RECTIFYING", operator, "指定跟进人：" + ownerName);
        return issueDetail(id);
    }

    public Map<String, Object> rectifyIssue(Long id, String note, MultipartFile[] photos, String operator) {
        Map<String, Object> issue = issueRow(id);
        String status = text(issue.get("status"));
        if (!status.equals("PENDING") && !status.equals("RECTIFYING")) throw BizException.badRequest("当前状态不能提交整改");
        if (note == null || note.isBlank()) throw BizException.badRequest("请填写整改说明");
        jdbc.update("UPDATE ops_patrol_issue SET status='REVIEWING',rectified_note=?,rectified_at=NOW() WHERE id=?", note.trim(), id);
        attachmentService.saveAll("OPS_ISSUE", id, photos);
        log(id, "RECTIFY", status, "REVIEWING", operator, note.trim());
        return issueDetail(id);
    }

    public Map<String, Object> reviewIssue(Long id, boolean pass, String note, String operator) {
        Map<String, Object> issue = issueRow(id);
        if (!"REVIEWING".equals(text(issue.get("status")))) throw BizException.badRequest("问题不在待复核状态");
        if (pass) {
            jdbc.update("UPDATE ops_patrol_issue SET status='CLOSED',closed_at=NOW(),reviewed_by=?,reviewed_at=NOW(),review_note=? WHERE id=?", operator, note, id);
            log(id, "REVIEW_PASS", "REVIEWING", "CLOSED", operator, note);
        } else {
            if (note == null || note.isBlank()) throw BizException.badRequest("驳回需填写原因");
            jdbc.update("UPDATE ops_patrol_issue SET status='RECTIFYING',reviewed_by=?,reviewed_at=NOW(),review_note=? WHERE id=?", operator, note, id);
            log(id, "REVIEW_REJECT", "REVIEWING", "RECTIFYING", operator, note);
        }
        return issueDetail(id);
    }

    private void log(Long issueId, String action, String from, String to, String operator, String note) {
        jdbc.update("INSERT INTO ops_patrol_issue_log(issue_id,action,from_status,to_status,operator,note) VALUES(?,?,?,?,?,?)",
                issueId, action, from, to, operator, note);
    }

    private Map<String, Object> issueRow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT i.*,d.name AS deptName FROM ops_patrol_issue i JOIN ops_patrol_dept d ON d.id=i.dept_id WHERE i.id=?", id);
        if (rows.isEmpty()) throw BizException.notFound("问题不存在");
        return rows.get(0);
    }

    String nextIssueNo() {
        String prefix = "OP" + LocalDate.now().format(DAY);
        // 以已存在的最大流水号为准，避免删除问题单后 COUNT 回退造成编号冲突
        Long max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(issue_no, ?) AS UNSIGNED)), 0) FROM ops_patrol_issue WHERE issue_no LIKE ?",
                Long.class, prefix.length() + 1, prefix + "%");
        return prefix + String.format("%03d", (max == null ? 0 : max) + 1);
    }

    // ================================================================ 交接记录（管理端）
    public List<Map<String, Object>> handovers(String date) {
        String day = (date == null || date.isBlank()) ? LocalDate.now().toString() : date.trim();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT h.id,h.handover_date AS handoverDate,h.dept_id AS deptId,d.name AS deptName,h.from_user_name AS fromUserName,"
                        + "h.unclosed_count AS unclosedCount,h.pass_count AS passCount,h.fail_count AS failCount,"
                        + "h.confirmed,h.confirmed_at AS confirmedAt,h.confirmed_by AS confirmedBy,h.remark "
                        + "FROM ops_patrol_handover h JOIN ops_patrol_dept d ON d.id=h.dept_id WHERE h.handover_date=? ORDER BY d.sort_order", day);
        for (Map<String, Object> row : rows) {
            List<String> ids = csv(text(jdbc.queryForMap("SELECT unclosed_issue_ids AS ids FROM ops_patrol_handover WHERE id=?", row.get("id")).get("ids")));
            row.put("issues", ids.isEmpty() ? List.of() : jdbc.queryForList(
                    "SELECT id,issue_no AS issueNo,merchant_name AS merchantName,merchant_code AS merchantCode,stall_code AS stallCode,category,description,status FROM ops_patrol_issue WHERE id IN (" + String.join(",", ids) + ")"));
        }
        return rows;
    }

    // ================================================================ 报表
    public Map<String, Object> dailyReport(String date) {
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        List<Map<String, Object>> deptStats = jdbc.queryForList(
                "SELECT d.id AS deptId,d.name AS deptName,d.form_type AS formType,"
                        + "COUNT(t.id) AS total,"
                        + "SUM(CASE WHEN t.status='DONE' THEN 1 ELSE 0 END) AS done,"
                        + "SUM(CASE WHEN t.status='DONE' AND t.late_minutes>0 THEN 1 ELSE 0 END) AS lateDone,"
                        + "SUM(CASE WHEN t.status='OVERDUE' THEN 1 ELSE 0 END) AS overdue,"
                        + "SUM(CASE WHEN t.status='EXEMPT' THEN 1 ELSE 0 END) AS exempt,"
                        + "COALESCE(SUM(t.pass_count),0) AS passCount,COALESCE(SUM(t.fail_count),0) AS failCount,COALESCE(SUM(t.skip_count),0) AS skipCount "
                        + "FROM ops_patrol_dept d LEFT JOIN ops_patrol_task t ON t.dept_id=d.id AND t.task_date=? AND t.is_handover=0 "
                        + "GROUP BY d.id,d.name,d.form_type,d.sort_order ORDER BY d.sort_order", day);
        List<Map<String, Object>> issueStats = jdbc.queryForList(
                "SELECT d.id AS deptId,d.name AS deptName,"
                        + "COUNT(i.id) AS total,"
                        + "SUM(CASE WHEN i.status='CLOSED' THEN 1 ELSE 0 END) AS closed,"
                        + "SUM(CASE WHEN i.overdue_flag=1 AND i.status<>'CLOSED' THEN 1 ELSE 0 END) AS overdue,"
                        + "SUM(CASE WHEN i.repeat_flag=1 THEN 1 ELSE 0 END) AS repeated "
                        + "FROM ops_patrol_dept d LEFT JOIN ops_patrol_issue i ON i.dept_id=d.id AND DATE(i.submitted_at)=? "
                        + "GROUP BY d.id,d.name,d.sort_order ORDER BY d.sort_order", day);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day.toString());
        result.put("taskStats", deptStats);
        result.put("issueStats", issueStats);
        return result;
    }

    // ================================================================ 工具
    private long insert(String sql, Object... params) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof LocalDate d) ps.setDate(i + 1, java.sql.Date.valueOf(d));
                else if (p instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
                else ps.setObject(i + 1, p);
            }
            return ps;
        }, holder);
        return holder.getKey() == null ? 0 : holder.getKey().longValue();
    }

    private String randomToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(HEX.charAt(RANDOM.nextInt(16)));
        return sb.toString();
    }

    private List<String> csv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        for (String part : value.split(",")) if (!part.isBlank()) result.add(part.trim());
        return result;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    private LocalDateTime toLocal(Object value) {
        if (value instanceof LocalDateTime t) return t;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private String text(Object value) { return value == null ? "" : value.toString().trim(); }

    private int num(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return value != null && "true".equalsIgnoreCase(value.toString());
    }
}
