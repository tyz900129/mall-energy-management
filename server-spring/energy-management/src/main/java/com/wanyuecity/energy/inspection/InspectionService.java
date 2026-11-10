/*
 * ============================================================================
 * 銆愬弽缂栬瘧杩樺師 路 浠呬緵 diff 鍙傝€?路 璇峰嬁鍩轰簬鏈枃浠舵敼浠ｇ爜涓婄嚎銆? *
 * 鏈枃浠剁敱绾夸笂杩愯浜х墿鍙嶇紪璇戣繕鍘燂紝涓嶆槸鍘熷婧愮爜锛? *   鏉ユ簮 war   : /home/admin/energy-deploy/energy-management.war
 *   war sha256 : cca2127f7007f817ff5e3e9d6af3056ad7d18f9278a2d3471d53ae1fc1b37722
 *   鏉ユ簮 class : WEB-INF/classes/com/wanyuecity/energy/inspection/InspectionService.class
 *   鍙嶇紪璇戝伐鍏?: CFR 0.152锛?-extraclasspath 鍚悓绫诲唴閮ㄧ被锛? *   杩樺師鏃堕棿   : 2026-11-10
 *
 * 宸茬煡灞€闄愶紙濡傚疄璁板綍锛屽嬁褰撲綔缂洪櫡娓呭崟鍘讳慨锛夛細
 *   1. 娉涘瀷琚摝闄わ紝鍑虹幇澶ч噺瑁哥被鍨嬶紙List / Map锛夛紝涓庡師瀹炵幇婧愮爜涓嶅悓銆? *   2. 閮ㄥ垎鏋勯€犺 CFR 鍚堝苟閲嶆帓锛岃涔夊彲鑳戒笌绾夸笂瀹炵幇涓嶄竴鑷达紝渚嬪
 *      d.put("path", ((d = new LinkedHashMap<>(...))... 杩欑鑰﹀悎鍐欐硶銆? *   3. 灞€閮ㄥ彉閲忓悕銆佹敞閲娿€佺┖琛屻€佹敞瑙ｄ功鍐欏舰寮忥紙濡?value={"/x"}锛夊潎宸插け鐪熴€? *   4. 鏈仛浜哄伐澶嶆牳锛屼笉淇濊瘉鍙紪璇戙€佷笉淇濊瘉璇箟姝ｇ‘銆? *
 * 鐢ㄩ€旓細baseline/deployed-20261109 鐨勫鐓ч敋鐐癸紝鐢ㄤ簬鍥炵瓟鈥滅嚎涓?2026-11-09 鐨? *      鏋勫缓鍒板簳鍖呭惈鍝簺浠ｇ爜鈥濄€傚彲鐢ㄤ簬 diff锛屼笉鍙洿鎺ユ彁浜や慨澶嶆垨閮ㄧ讲銆? * ============================================================================
 *//*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.JsonProcessingException
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  com.google.zxing.BarcodeFormat
 *  com.google.zxing.client.j2se.MatrixToImageWriter
 *  com.google.zxing.common.BitMatrix
 *  com.google.zxing.qrcode.QRCodeWriter
 *  com.wanyuecity.energy.notification.service.NotificationService
 *  lombok.Generated
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.dao.DuplicateKeyException
 *  org.springframework.http.HttpStatus
 *  org.springframework.http.HttpStatusCode
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.scheduling.annotation.Scheduled
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.web.server.ResponseStatusException
 */
package com.wanyuecity.energy.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wanyuecity.energy.notification.service.NotificationService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Generated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NotificationService notificationService;
    @Value(value="${energy.public-base-url:https://auto.apperload.com}")
    private String publicBaseUrl;

    public List<Map<String, Object>> types() {
        return this.jdbc.queryForList("SELECT id,code,name,target_category targetCategory,description,enabled,created_at createdAt,updated_at updatedAt FROM inspection_type ORDER BY enabled DESC,id DESC");
    }

    @Transactional
    public Map<String, Object> saveType(Long id, Map<String, Object> body) {
        this.require(body, "code", "\u7c7b\u578b\u7f16\u7801\u4e0d\u80fd\u4e3a\u7a7a");
        this.require(body, "name", "\u7c7b\u578b\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        if (id == null) {
            this.jdbc.update("INSERT INTO inspection_type(code,name,target_category,description,enabled) VALUES(?,?,?,?,?)", new Object[]{InspectionService.text(body, "code").trim().toUpperCase(), InspectionService.text(body, "name"), InspectionService.defaultText(body, "targetCategory", "DEVICE"), InspectionService.text(body, "description"), InspectionService.boolInt(body, "enabled", 1)});
            id = (Long)this.jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            this.jdbc.update("UPDATE inspection_type SET code=?,name=?,target_category=?,description=?,enabled=? WHERE id=?", new Object[]{InspectionService.text(body, "code").trim().toUpperCase(), InspectionService.text(body, "name"), InspectionService.defaultText(body, "targetCategory", "DEVICE"), InspectionService.text(body, "description"), InspectionService.boolInt(body, "enabled", 1), id});
        }
        return this.one("SELECT id,code,name,target_category targetCategory,description,enabled FROM inspection_type WHERE id=?", id);
    }

    public List<Map<String, Object>> templates() {
        return this.jdbc.queryForList("SELECT t.id,t.inspection_type_id inspectionTypeId,y.name inspectionTypeName,t.name,t.version_no versionNo,t.fields_json fieldsJson,t.enabled,t.updated_at updatedAt FROM inspection_template t JOIN inspection_type y ON y.id=t.inspection_type_id ORDER BY t.enabled DESC,t.id DESC");
    }

    @Transactional
    public Map<String, Object> saveTemplate(Long id, Map<String, Object> body) {
        Long typeId = InspectionService.longValue(body.get("inspectionTypeId"));
        if (typeId == null) {
            this.bad("\u8bf7\u9009\u62e9\u5de1\u68c0\u7c7b\u578b");
        }
        this.require(body, "name", "\u6a21\u677f\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        String fields = this.jsonText(body.get("fields"));
        if ("[]".equals(fields)) {
            this.bad("\u81f3\u5c11\u914d\u7f6e\u4e00\u4e2a\u5de1\u68c0\u9879");
        }
        if (id == null) {
            this.jdbc.update("INSERT INTO inspection_template(inspection_type_id,name,version_no,fields_json,enabled) VALUES(?,?,?,?,?)", new Object[]{typeId, InspectionService.text(body, "name"), InspectionService.intValue(body.get("versionNo"), 1), fields, InspectionService.boolInt(body, "enabled", 1)});
            id = (Long)this.jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            this.jdbc.update("UPDATE inspection_template SET inspection_type_id=?,name=?,version_no=?,fields_json=?,enabled=? WHERE id=?", new Object[]{typeId, InspectionService.text(body, "name"), InspectionService.intValue(body.get("versionNo"), 1), fields, InspectionService.boolInt(body, "enabled", 1), id});
        }
        return this.one("SELECT id,inspection_type_id inspectionTypeId,name,version_no versionNo,fields_json fieldsJson,enabled FROM inspection_template WHERE id=?", id);
    }

    public List<Map<String, Object>> targetOptions(String keyword) {
        String key = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return this.jdbc.queryForList("SELECT CAST(e.id AS CHAR) targetId,e.equipment_code targetCode,e.equipment_name targetName,e.installation_location locationText,c.name categoryName,e.equipment_status equipmentStatus,e.floor_code floorCode,e.area_name areaName FROM inspection_equipment e JOIN inspection_equipment_category c ON c.id=e.category_id WHERE e.enabled=1 AND (?='%%' OR e.equipment_code LIKE ? OR e.equipment_name LIKE ? OR e.serial_number LIKE ? OR e.installation_location LIKE ?) ORDER BY c.sort_order,e.equipment_code LIMIT 500", new Object[]{key, key, key, key, key});
    }

    public List<Map<String, Object>> plans() {
        List rows = this.jdbc.queryForList("SELECT p.id,p.name,p.inspection_type_id inspectionTypeId,y.name inspectionTypeName,p.template_id templateId,t.name templateName,p.frequency_type frequencyType,p.frequency_value frequencyValue,p.schedule_config_json scheduleConfigJson,p.start_date startDate,p.end_date endDate,p.execute_time executeTime,p.overdue_minutes overdueMinutes,p.assignee_user_id assigneeUserId,p.assignee_name assigneeName,p.owner_type ownerType,p.owner_id ownerId,p.target_mode targetMode,p.claim_mode claimMode,p.webhook_config_ids webhookConfigIds,p.enabled,p.last_generated_at lastGeneratedAt,p.updated_at updatedAt,(SELECT COUNT(*) FROM inspection_plan_target x WHERE x.plan_id=p.id AND x.enabled=1) targetCount FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id JOIN inspection_template t ON t.id=p.template_id ORDER BY p.enabled DESC,p.id DESC");
        for (Map row : rows) {
            row.put("targets", this.jdbc.queryForList("SELECT id,target_type targetType,target_id targetId,target_code targetCode,target_name targetName,location_text locationText,enabled FROM inspection_plan_target WHERE plan_id=? ORDER BY id", new Object[]{row.get("id")}));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> savePlan(Long id, Map<String, Object> body) {
        String claimMode;
        String targetMode;
        LocalTime execute;
        LocalDate start;
        this.require(body, "name", "\u8ba1\u5212\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        Long typeId = InspectionService.longValue(body.get("inspectionTypeId"));
        Long templateId = InspectionService.longValue(body.get("templateId"));
        if (typeId == null || templateId == null) {
            this.bad("\u8bf7\u9009\u62e9\u5de1\u68c0\u7c7b\u578b\u548c\u8868\u5355\u6a21\u677f");
        }
        if ((start = InspectionService.parseDate(body.get("startDate"))) == null) {
            this.bad("\u8bf7\u9009\u62e9\u5f00\u59cb\u65e5\u671f");
        }
        if ((execute = InspectionService.parseTime(body.get("executeTime"))) == null) {
            this.bad("\u8bf7\u9009\u62e9\u6267\u884c\u65f6\u95f4");
        }
        String ownerType = InspectionService.defaultText(body, "ownerType", "USER");
        if (!Set.of("USER", "ROLE", "DEPT").contains(ownerType)) {
            this.bad("\u5f52\u5c5e\u7c7b\u578b\u65e0\u6548");
        }
        Long ownerId = InspectionService.longValue(body.get("ownerId"));
        if ("USER".equals(ownerType) && ownerId == null) {
            ownerId = InspectionService.longValue(body.get("assigneeUserId"));
        }
        if (!"USER".equals(ownerType) && ownerId == null) {
            this.bad("\u8bf7\u9009\u62e9\u8d23\u4efb\u5c97\u4f4d\u6216\u90e8\u95e8");
        }
        if ("PER_CHILD".equals(targetMode = InspectionService.defaultText(body, "targetMode", "UNIT")) && !"DEPT".equals(ownerType)) {
            this.bad("\u6309\u5b50\u8282\u70b9\u4e0b\u53d1\u4ec5\u652f\u6301\u90e8\u95e8\u4efb\u52a1");
        }
        if ((claimMode = InspectionService.text(body, "claimMode")).isBlank()) {
            claimMode = "USER".equals(ownerType) ? "NONE" : "CLAIM";
        }
        boolean fanout = "DEPT".equals(ownerType) && "PER_CHILD".equals(targetMode);
        Object[] args = new Object[]{InspectionService.text(body, "name"), typeId, templateId, InspectionService.defaultText(body, "frequencyType", "DAILY"), InspectionService.intValue(body.get("frequencyValue"), 1), this.jsonText(body.get("scheduleConfig")), Date.valueOf(start), InspectionService.sqlDate(body.get("endDate")), Time.valueOf(execute), InspectionService.intValue(body.get("overdueMinutes"), 1440), InspectionService.longValue(body.get("assigneeUserId")), InspectionService.text(body, "assigneeName"), this.jsonText(body.get("webhookConfigIds")), ownerType, ownerId, targetMode, claimMode, InspectionService.boolInt(body, "enabled", 1)};
        if (id == null) {
            this.jdbc.update("INSERT INTO inspection_plan(name,inspection_type_id,template_id,frequency_type,frequency_value,schedule_config_json,start_date,end_date,execute_time,overdue_minutes,assignee_user_id,assignee_name,webhook_config_ids,owner_type,owner_id,target_mode,claim_mode,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", args);
            id = (Long)this.jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            Object[] update = Arrays.copyOf(args, args.length + 1);
            update[args.length] = id;
            this.jdbc.update("UPDATE inspection_plan SET name=?,inspection_type_id=?,template_id=?,frequency_type=?,frequency_value=?,schedule_config_json=?,start_date=?,end_date=?,execute_time=?,overdue_minutes=?,assignee_user_id=?,assignee_name=?,webhook_config_ids=?,owner_type=?,owner_id=?,target_mode=?,claim_mode=?,enabled=? WHERE id=?", update);
            this.jdbc.update("DELETE FROM inspection_plan_target WHERE plan_id=?", new Object[]{id});
        }
        if (!fanout) {
            Integer invalid;
            Object targets = body.get("targets");
            if (targets instanceof List) {
                List list = (List)targets;
                for (Object item : list) {
                    if (!(item instanceof Map)) continue;
                    Map m = (Map)item;
                    this.jdbc.update("INSERT INTO inspection_plan_target(plan_id,target_type,target_id,target_code,target_name,location_text,enabled) VALUES(?,?,?,?,?,?,1)", new Object[]{id, InspectionService.defaultText(m, "targetType", "PLATFORM_DEVICE"), InspectionService.text(m, "targetId"), InspectionService.text(m, "targetCode"), InspectionService.text(m, "targetName"), InspectionService.text(m, "locationText")});
                }
            }
            if ((Integer)this.jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target WHERE plan_id=?", Integer.class, new Object[]{id}) == 0) {
                this.bad("\u81f3\u5c11\u9009\u62e9\u4e00\u4e2a\u5de1\u68c0\u5bf9\u8c61");
            }
            if ((invalid = (Integer)this.jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target x JOIN inspection_equipment e ON e.id=CAST(x.target_id AS UNSIGNED) LEFT JOIN inspection_category_template ct ON ct.category_id=e.category_id AND ct.template_id=? AND ct.enabled=1 WHERE x.plan_id=? AND x.target_type='INSPECTION_EQUIPMENT' AND ct.id IS NULL", Integer.class, new Object[]{templateId, id})) != null && invalid > 0) {
                this.bad("\u6240\u9009\u8bbe\u5907\u5206\u7c7b\u5c1a\u672a\u5173\u8054\u8be5\u5de1\u68c0\u8868\u5355\uff0c\u8bf7\u5148\u5728\u8bbe\u5907\u5206\u7c7b\u4e2d\u914d\u7f6e\u5173\u8054\u8868\u5355");
            }
        }
        this.generatePlan(id, LocalDate.now());
        Long savedId = id;
        return this.plans().stream().filter(p -> Objects.equals(InspectionService.longValue(p.get("id")), savedId)).findFirst().orElseThrow();
    }

    @Scheduled(cron="0 */5 * * * *")
    public void scheduledGenerate() {
        this.generateAll();
        this.markOverdue();
        this.sendReminders();
    }

    public int generateAll() {
        int total = 0;
        for (Map p : this.jdbc.queryForList("SELECT id FROM inspection_plan WHERE enabled=1 AND start_date<=CURRENT_DATE AND (end_date IS NULL OR end_date>=CURRENT_DATE)")) {
            total += this.generatePlan(InspectionService.longValue(p.get("id")), LocalDate.now());
        }
        return total;
    }

    @Transactional
    public int generatePlan(Long planId, LocalDate day) {
        Map<String, Object> p = this.one("SELECT * FROM inspection_plan WHERE id=?", planId);
        if (InspectionService.intValue(p.get("enabled"), 0) != 1 || day.isBefore(InspectionService.toDate(p.get("start_date"))) || p.get("end_date") != null && day.isAfter(InspectionService.toDate(p.get("end_date")))) {
            return 0;
        }
        if (!this.due(p, day)) {
            return 0;
        }
        LocalTime execute = ((Time)p.get("execute_time")).toLocalTime();
        LocalDateTime scheduled = LocalDateTime.of(day, execute);
        String period = this.periodKey(p, day);
        LocalDateTime dueAt = scheduled.plusMinutes(InspectionService.intValue(p.get("overdue_minutes"), 1440));
        String ownerType = InspectionService.defaultText(p, "owner_type", "USER");
        Long ownerId = InspectionService.longValue(p.get("owner_id"));
        String targetMode = InspectionService.defaultText(p, "target_mode", "UNIT");
        String claimMode = InspectionService.defaultText(p, "claim_mode", "NONE");
        int count = 0;
        if ("DEPT".equals(ownerType) && "PER_CHILD".equals(targetMode)) {
            List children = this.jdbc.queryForList("SELECT id,code,name FROM sys_department WHERE parent_id=? AND deleted_at IS NULL AND status='ACTIVE' ORDER BY sort_order,id", new Object[]{ownerId});
            if (children.isEmpty()) {
                children = this.jdbc.queryForList("SELECT id,code,name FROM sys_department WHERE id=? AND deleted_at IS NULL", new Object[]{ownerId});
            }
            for (Map c : children) {
                count += this.insertTask(p, period, scheduled, dueAt, "DEPT", String.valueOf(c.get("id")), String.valueOf(c.get("code")), String.valueOf(c.get("name")), null, this.deptOwner(InspectionService.longValue(c.get("id")), claimMode));
            }
        } else {
            Owner o = this.resolveOwner(ownerType, ownerId, InspectionService.text(p, "assignee_name"), claimMode);
            for (Map t : this.jdbc.queryForList("SELECT * FROM inspection_plan_target WHERE plan_id=? AND enabled=1", new Object[]{planId})) {
                count += this.insertTask(p, period, scheduled, dueAt, String.valueOf(t.get("target_type")), String.valueOf(t.get("target_id")), InspectionService.text(t, "target_code"), InspectionService.text(t, "target_name"), InspectionService.text(t, "location_text"), o);
            }
        }
        this.jdbc.update("UPDATE inspection_plan SET last_generated_at=NOW(3) WHERE id=?", new Object[]{planId});
        return count;
    }

    private int insertTask(Map<String, Object> p, String period, LocalDateTime scheduled, LocalDateTime dueAt, String targetType, String targetId, String targetCode, String targetName, String locationText, Owner o) {
        try {
            String no = "XJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + String.format("%04d", Math.abs(UUID.randomUUID().hashCode()) % 10000);
            this.jdbc.update("INSERT INTO inspection_task(task_no,plan_id,inspection_type_id,template_id,target_type,target_id,target_code,target_name,location_text,equipment_snapshot_json,period_key,scheduled_at,due_at,assignee_user_id,assignee_name,access_token,status,source_type,owner_type,owner_id,owner_name,owner_dept_id,owner_dept_name,owner_ancestors,claimed_by,claimed_at,assignee_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING','PLAN',?,?,?,?,?,?,?,?,?)", new Object[]{no, InspectionService.longValue(p.get("id")), p.get("inspection_type_id"), p.get("template_id"), targetType, targetId, targetCode, targetName, locationText, this.equipmentSnapshot(targetType, targetId), period, Timestamp.valueOf(scheduled), Timestamp.valueOf(dueAt), o.assigneeId(), o.assigneeName(), UUID.randomUUID().toString().replace("-", ""), o.type(), o.id(), o.name(), o.deptId(), o.deptName(), o.ancestors(), o.claimedBy(), o.claimedBy() == null ? null : Timestamp.valueOf(LocalDateTime.now()), o.assigneeStatus()});
            return 1;
        }
        catch (DuplicateKeyException ignored) {
            return 0;
        }
    }

    public List<Map<String, Object>> tasks(String status, String keyword, String username, boolean ownOnly) {
        StringBuilder sql = new StringBuilder("SELECT k.id,k.task_no taskNo,k.plan_id planId,p.name planName,y.name inspectionTypeName,k.target_type targetType,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.status,k.source_type sourceType,k.owner_type ownerType,k.owner_id ownerId,k.owner_name ownerName,k.owner_dept_id ownerDeptId,k.owner_dept_name ownerDeptName,k.claimed_by claimedBy,k.claimed_at claimedAt,k.assignee_status assigneeStatus,k.started_at startedAt,k.submitted_at submittedAt,k.result_summary resultSummary,k.completed_by completedBy,p.claim_mode planClaimMode FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id WHERE 1=1");
        ArrayList<Object> args = new ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND k.status=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (k.task_no LIKE ? OR k.target_name LIKE ? OR k.target_code LIKE ?)");
            String x = "%" + keyword + "%";
            args.add(x);
            args.add(x);
            args.add(x);
        }
        if (ownOnly) {
            Map<String, Object> u = this.userRow(username);
            if (u == null) {
                sql.append(" AND (k.assignee_name=? OR k.assignee_user_id=(SELECT id FROM sys_user WHERE username=? LIMIT 1))");
                args.add(username);
                args.add(username);
            } else {
                Long uid = InspectionService.longValue(u.get("id"));
                String fullPath = this.deptFullPath(InspectionService.longValue(u.get("department_id")));
                sql.append(" AND (k.assignee_user_id=? OR k.claimed_by=? OR (k.claimed_by IS NULL AND ((k.owner_type='ROLE' AND k.owner_id IN (SELECT role_id FROM sys_user_role WHERE user_id=?)) OR (k.owner_type='DEPT' AND k.owner_ancestors IS NOT NULL AND ? LIKE CONCAT(k.owner_ancestors,'%')))))");
                args.add(uid);
                args.add(username);
                args.add(uid);
                args.add(fullPath);
            }
        }
        sql.append(" ORDER BY k.scheduled_at DESC,k.id DESC LIMIT 500");
        return this.jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> task(Long id) {
        Map<String, Object> r = this.one("SELECT k.*,k.task_no taskNo,k.plan_id planId,k.target_type targetType,k.target_id targetId,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.equipment_snapshot_json equipmentSnapshotJson,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.status,k.source_type sourceType,k.owner_type ownerType,k.owner_id ownerId,k.owner_name ownerName,k.owner_dept_id ownerDeptId,k.owner_dept_name ownerDeptName,k.owner_ancestors ownerAncestors,k.claimed_by claimedBy,k.claimed_at claimedAt,k.assignee_status assigneeStatus,k.started_at startedAt,k.submitted_at submittedAt,k.result_json resultJson,k.result_summary resultSummary,k.completed_by completedBy,p.name planName,p.claim_mode planClaimMode,y.name inspectionTypeName,t.name templateName,t.fields_json fieldsJson FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id JOIN inspection_template t ON t.id=k.template_id WHERE k.id=?", id);
        r.put("issues", this.issues(id, null));
        return r;
    }

    public Map<String, Object> taskByToken(String token) {
        Map<String, Object> row = this.one("SELECT id FROM inspection_task WHERE access_token=?", token);
        return this.task(InspectionService.longValue(row.get("id")));
    }

    public byte[] qr(Long id) {
        Map<String, Object> t = this.task(id);
        try {
            String url = this.publicBaseUrl.replaceAll("/$", "") + "/inspection/tasks?taskToken=" + String.valueOf(t.get("accessToken"));
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 420, 420);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream((BitMatrix)matrix, (String)"PNG", (OutputStream)out);
            return out.toByteArray();
        }
        catch (Exception e) {
            this.bad("\u5de1\u68c0\u4e8c\u7ef4\u7801\u751f\u6210\u5931\u8d25");
            return new byte[0];
        }
    }

    public byte[] qrByToken(String token) {
        Map<String, Object> row = this.one("SELECT id FROM inspection_task WHERE access_token=?", token);
        return this.qr(InspectionService.longValue(row.get("id")));
    }

    @Transactional
    public Map<String, Object> submit(Long id, Map<String, Object> body, String username) {
        int n;
        Map<String, Object> u;
        String claimedBy;
        Map<String, Object> task = this.task(id);
        String status = String.valueOf(task.get("status"));
        if (Set.of("COMPLETED", "CLOSED", "CANCELLED").contains(status)) {
            this.bad("\u8be5\u4efb\u52a1\u5df2\u7ed3\u675f\uff0c\u4e0d\u80fd\u91cd\u590d\u63d0\u4ea4");
        }
        String string = claimedBy = task.get("claimedBy") == null ? null : String.valueOf(task.get("claimedBy"));
        if (claimedBy != null && !claimedBy.equals(username)) {
            this.bad("\u8be5\u4efb\u52a1\u5df2\u7531 " + claimedBy + " \u8ba4\u9886\uff0c\u4e0d\u80fd\u91cd\u590d\u56de\u6267");
        }
        Long uid = (u = this.userRow(username)) == null ? null : InspectionService.longValue(u.get("id"));
        String display = u == null ? username : String.valueOf(u.getOrDefault("name", username));
        String result = this.jsonText(body.get("result"));
        Object issues = body.get("issues");
        if (issues instanceof List) {
            List l = (List)issues;
            n = l.size();
        } else {
            n = 0;
        }
        int issueCount = n;
        this.jdbc.update("UPDATE inspection_task SET status=?,started_at=COALESCE(started_at,NOW(3)),submitted_at=NOW(3),result_json=?,result_summary=?,completed_by=?,claimed_by=COALESCE(claimed_by,?),claimed_at=COALESCE(claimed_at,NOW(3)),assignee_user_id=COALESCE(assignee_user_id,?),assignee_name=COALESCE(NULLIF(assignee_name,''),?),assignee_status='ASSIGNED' WHERE id=?", new Object[]{issueCount > 0 ? "PENDING_RECTIFICATION" : "COMPLETED", result, InspectionService.text(body, "summary"), username, username, uid, display, id});
        this.jdbc.update("DELETE FROM inspection_issue WHERE task_id=? AND status='PENDING'", new Object[]{id});
        if (issues instanceof List) {
            List list = (List)issues;
            for (Object item : list) {
                if (!(item instanceof Map)) continue;
                Map m = (Map)item;
                this.jdbc.update("INSERT INTO inspection_issue(task_id,field_key,field_label,level,description,rectification_requirement,responsible_user_id,responsible_name,due_at,status) VALUES(?,?,?,?,?,?,?,?,?,'PENDING')", new Object[]{id, InspectionService.text(m, "fieldKey"), InspectionService.text(m, "fieldLabel"), InspectionService.defaultText(m, "level", "WARNING"), InspectionService.text(m, "description"), InspectionService.text(m, "rectificationRequirement"), InspectionService.longValue(m.get("responsibleUserId")), InspectionService.text(m, "responsibleName"), InspectionService.sqlTimestamp(m.get("dueAt"))});
            }
        }
        return this.task(id);
    }

    @Transactional
    public Map<String, Object> claim(Long id, String username) {
        Map<String, Object> u;
        Map<String, Object> t = this.one("SELECT k.id,k.status,k.owner_type ownerType,k.owner_id ownerId,k.claimed_by claimedBy,k.owner_ancestors ownerAncestors,p.claim_mode claimMode FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.id=?", id);
        if (Set.of("COMPLETED", "CLOSED", "CANCELLED").contains(String.valueOf(t.get("status")))) {
            this.bad("\u4efb\u52a1\u5df2\u7ed3\u675f\uff0c\u65e0\u9700\u8ba4\u9886");
        }
        String ownerType = String.valueOf(t.get("ownerType"));
        if (!Set.of("DEPT", "ROLE").contains(ownerType)) {
            this.bad("\u8be5\u4efb\u52a1\u4e0d\u652f\u6301\u8ba4\u9886");
        }
        if (!"CLAIM".equals(String.valueOf(t.get("claimMode")))) {
            this.bad("\u8be5\u4efb\u52a1\u672a\u5f00\u542f\u8ba4\u9886");
        }
        if (t.get("claimedBy") != null) {
            if (username.equals(String.valueOf(t.get("claimedBy")))) {
                return this.task(id);
            }
            this.bad("\u8be5\u4efb\u52a1\u5df2\u88ab " + String.valueOf(t.get("claimedBy")) + " \u8ba4\u9886");
        }
        if ((u = this.userRow(username)) == null) {
            this.bad("\u5f53\u524d\u7528\u6237\u4e0d\u5b58\u5728");
        }
        Long uid = InspectionService.longValue(u.get("id"));
        if ("ROLE".equals(ownerType)) {
            Integer n = (Integer)this.jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_id=?", Integer.class, new Object[]{uid, InspectionService.longValue(t.get("ownerId"))});
            if (n == null || n == 0) {
                this.bad("\u5f53\u524d\u7528\u6237\u4e0d\u5c5e\u4e8e\u8be5\u5c97\u4f4d\uff0c\u4e0d\u80fd\u8ba4\u9886");
            }
        } else {
            String ownerPath;
            String path = this.deptFullPath(InspectionService.longValue(u.get("department_id")));
            String string = ownerPath = t.get("ownerAncestors") == null ? null : String.valueOf(t.get("ownerAncestors"));
            if (ownerPath == null || !path.startsWith(ownerPath)) {
                this.bad("\u5f53\u524d\u7528\u6237\u4e0d\u5c5e\u4e8e\u8be5\u90e8\u95e8\uff0c\u4e0d\u80fd\u8ba4\u9886");
            }
        }
        String display = String.valueOf(u.getOrDefault("name", username));
        int rows = this.jdbc.update("UPDATE inspection_task SET claimed_by=?,claimed_at=NOW(3),assignee_user_id=?,assignee_name=?,assignee_status='ASSIGNED' WHERE id=? AND claimed_by IS NULL", new Object[]{username, uid, display, id});
        if (rows == 0) {
            Map<String, Object> again = this.one("SELECT claimed_by claimedBy FROM inspection_task WHERE id=?", id);
            if (username.equals(String.valueOf(again.get("claimedBy")))) {
                return this.task(id);
            }
            this.bad("\u4efb\u52a1\u5df2\u88ab\u4ed6\u4eba\u8ba4\u9886");
        }
        return this.task(id);
    }

    @Transactional
    public Map<String, Object> reassign(Long id, Map<String, Object> body) {
        Map<String, Object> t = this.one("SELECT id,status FROM inspection_task WHERE id=?", id);
        if (Set.of("COMPLETED", "CLOSED", "CANCELLED").contains(String.valueOf(t.get("status")))) {
            this.bad("\u5df2\u5b8c\u6210\u6216\u5df2\u53d6\u6d88\u7684\u4efb\u52a1\u4e0d\u5141\u8bb8\u91cd\u65b0\u5f52\u5c5e");
        }
        String type = InspectionService.defaultText(body, "ownerType", "DEPT");
        Long oid = InspectionService.longValue(body.get("ownerId"));
        if (!Set.of("USER", "ROLE", "DEPT").contains(type)) {
            this.bad("\u5f52\u5c5e\u7c7b\u578b\u65e0\u6548");
        }
        if (oid == null) {
            this.bad("\u8bf7\u9009\u62e9\u65b0\u7684\u8d23\u4efb\u5bf9\u8c61");
        }
        Owner o = this.resolveOwner(type, oid, null, "CLAIM");
        this.jdbc.update("UPDATE inspection_task SET owner_type=?,owner_id=?,owner_name=?,owner_dept_id=?,owner_dept_name=?,owner_ancestors=?,claimed_by=NULL,claimed_at=NULL,assignee_user_id=?,assignee_name=?,assignee_status=? WHERE id=?", new Object[]{o.type(), o.id(), o.name(), o.deptId(), o.deptName(), o.ancestors(), o.assigneeId(), o.assigneeName(), o.assigneeStatus(), id});
        return this.task(id);
    }

    public Map<String, Object> ownerOptions() {
        LinkedHashMap<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("departments", this.jdbc.queryForList("SELECT id,code,name,parent_id parentId,ancestors,depth,dept_level deptLevel,status FROM sys_department WHERE deleted_at IS NULL ORDER BY sort_order,id"));
        r.put("roles", this.jdbc.queryForList("SELECT id,code,name,dept_id deptId FROM sys_role ORDER BY dept_id IS NULL DESC,dept_id,id"));
        r.put("users", this.jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id deptId FROM sys_user WHERE status=1 ORDER BY department_id,id"));
        return r;
    }

    public List<Map<String, Object>> issues(Long taskId, String status) {
        String sql = "SELECT i.id,i.task_id taskId,k.task_no taskNo,k.target_name targetName,i.field_key fieldKey,i.field_label fieldLabel,i.level,i.description,i.rectification_requirement rectificationRequirement,i.responsible_user_id responsibleUserId,i.responsible_name responsibleName,i.due_at dueAt,i.status,i.rectification_note rectificationNote,i.rectified_by rectifiedBy,i.rectified_at rectifiedAt,i.reviewed_by reviewedBy,i.reviewed_at reviewedAt,i.created_at createdAt FROM inspection_issue i JOIN inspection_task k ON k.id=i.task_id WHERE (? IS NULL OR i.task_id=?) AND (? IS NULL OR i.status=?) ORDER BY i.id DESC";
        return this.jdbc.queryForList(sql, new Object[]{taskId, taskId, status, status});
    }

    @Transactional
    public Map<String, Object> rectify(Long id, String note, String user) {
        if (note == null || note.isBlank()) {
            this.bad("\u8bf7\u586b\u5199\u6574\u6539\u8bf4\u660e");
        }
        this.jdbc.update("UPDATE inspection_issue SET status='RECTIFIED',rectification_note=?,rectified_by=?,rectified_at=NOW(3) WHERE id=?", new Object[]{note, user, id});
        return this.one("SELECT * FROM inspection_issue WHERE id=?", id);
    }

    @Transactional
    public Map<String, Object> review(Long id, boolean passed, String user) {
        Map<String, Object> issue = this.one("SELECT * FROM inspection_issue WHERE id=?", id);
        this.jdbc.update("UPDATE inspection_issue SET status=?,reviewed_by=?,reviewed_at=NOW(3) WHERE id=?", new Object[]{passed ? "CLOSED" : "PENDING", user, id});
        Long taskId = InspectionService.longValue(issue.get("task_id"));
        if (passed && (Integer)this.jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE task_id=? AND status<>'CLOSED'", Integer.class, new Object[]{taskId}) == 0) {
            this.jdbc.update("UPDATE inspection_task SET status='CLOSED' WHERE id=?", new Object[]{taskId});
        } else if (!passed) {
            this.jdbc.update("UPDATE inspection_task SET status='PENDING_RECTIFICATION' WHERE id=?", new Object[]{taskId});
        }
        return this.one("SELECT * FROM inspection_issue WHERE id=?", id);
    }

    public Map<String, Object> dashboard() {
        LinkedHashMap<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("summary", this.one("SELECT COUNT(*) total,SUM(status='PENDING') pending,SUM(status='IN_PROGRESS') inProgress,SUM(status IN ('COMPLETED','CLOSED')) completed,SUM(status='OVERDUE') overdue,SUM(status='PENDING_RECTIFICATION') rectification FROM inspection_task WHERE scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01')", new Object[0]));
        r.put("typeStats", this.jdbc.queryForList("SELECT y.name typeName,COUNT(*) total,SUM(k.status IN ('COMPLETED','CLOSED')) completed,SUM(k.status='OVERDUE') overdue FROM inspection_task k JOIN inspection_type y ON y.id=k.inspection_type_id WHERE k.scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') GROUP BY y.id,y.name ORDER BY total DESC"));
        r.put("recent", this.jdbc.queryForList("SELECT DATE(scheduled_at) day,COUNT(*) total,SUM(status IN ('COMPLETED','CLOSED')) completed FROM inspection_task WHERE scheduled_at>=CURRENT_DATE-INTERVAL 13 DAY GROUP BY DATE(scheduled_at) ORDER BY day"));
        r.put("openIssues", this.jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE status<>'CLOSED'", Integer.class));
        return r;
    }

    private void markOverdue() {
        this.jdbc.update("UPDATE inspection_task SET status='OVERDUE' WHERE status IN ('PENDING','IN_PROGRESS') AND due_at<NOW(3)");
    }

    private String equipmentSnapshot(String targetType, String targetId) {
        if (!"INSPECTION_EQUIPMENT".equals(targetType)) {
            return "{}";
        }
        List rows = this.jdbc.queryForList("SELECT e.equipment_code equipmentCode,e.equipment_name equipmentName,c.name categoryName,e.system_name systemName,e.installation_location installationLocation,e.floor_code floorCode,e.area_name areaName,e.brand,e.model,e.specification,e.serial_number serialNumber,e.responsible_department responsibleDepartment,e.responsible_person responsiblePerson,e.responsible_phone responsiblePhone,e.equipment_status equipmentStatus,e.importance_level importanceLevel,e.attributes_json attributesJson FROM inspection_equipment e JOIN inspection_equipment_category c ON c.id=e.category_id WHERE e.id=?", new Object[]{targetId});
        return rows.isEmpty() ? "{}" : this.jsonText(rows.get(0));
    }

    private Owner resolveOwner(String type, Long id, String fallbackName, String claimMode) {
        if ("ROLE".equals(type)) {
            Map<String, Object> role = this.one("SELECT id,name,dept_id deptId FROM sys_role WHERE id=?", id);
            if (role == null) {
                this.bad("\u5c97\u4f4d\u4e0d\u5b58\u5728: " + id);
            }
            Map<String, Object> dept = this.deptSnapshot(InspectionService.longValue(role.get("deptId")));
            List holders = this.jdbc.queryForList("SELECT u.id,u.username,COALESCE(NULLIF(u.display_name,''),u.username) name FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id WHERE ur.role_id=? AND u.status=1 ORDER BY u.id", new Object[]{id});
            return new Owner("ROLE", id, String.valueOf(role.get("name")), InspectionService.longValue(role.get("deptId")), dept == null ? null : String.valueOf(dept.get("name")), dept == null ? null : String.valueOf(dept.get("path")), holders.size() == 1 ? InspectionService.longValue(((Map)holders.get(0)).get("id")) : null, holders.size() == 1 ? String.valueOf(((Map)holders.get(0)).get("name")) : null, holders.size() == 1 ? "ASSIGNED" : "NO_ASSIGNEE", holders.size() == 1 && "AUTO".equals(claimMode) ? String.valueOf(((Map)holders.get(0)).get("username")) : null);
        }
        if ("DEPT".equals(type)) {
            return this.deptOwner(id, claimMode);
        }
        if (id != null) {
            Map<String, Object> u = this.one("SELECT u.id,COALESCE(NULLIF(u.display_name,''),u.username) name,u.department_id deptId FROM sys_user u WHERE u.id=?", id);
            if (u == null) {
                this.bad("\u7528\u6237\u4e0d\u5b58\u5728: " + id);
            }
            Map<String, Object> dept = this.deptSnapshot(InspectionService.longValue(u.get("deptId")));
            return new Owner("USER", id, String.valueOf(u.get("name")), InspectionService.longValue(u.get("deptId")), dept == null ? null : String.valueOf(dept.get("name")), dept == null ? null : String.valueOf(dept.get("path")), id, String.valueOf(u.get("name")), "ASSIGNED", null);
        }
        String name = fallbackName == null ? "" : fallbackName.trim();
        return new Owner("USER", null, name, null, null, null, null, name.isBlank() ? null : name, name.isBlank() ? "NO_ASSIGNEE" : "ASSIGNED", null);
    }

    private Owner deptOwner(Long id, String claimMode) {
        Map<String, Object> dept = this.deptSnapshot(id);
        if (dept == null) {
            this.bad("\u90e8\u95e8\u4e0d\u5b58\u5728: " + id);
        }
        return new Owner("DEPT", id, String.valueOf(dept.get("name")), id, String.valueOf(dept.get("name")), String.valueOf(dept.get("path")), null, null, "CLAIM".equals(claimMode) ? "UNCLAIMED" : "NO_ASSIGNEE", null);
    }

    private Map<String, Object> deptSnapshot(Long id) {
        LinkedHashMap<String, Object> d;
        if (id == null) {
            return null;
        }
        List l = this.jdbc.queryForList("SELECT id,name,ancestors FROM sys_department WHERE id=? AND deleted_at IS NULL", new Object[]{id});
        if (l.isEmpty()) {
            return null;
        }
        d.put("path", ((d = new LinkedHashMap<String, Object>((Map)l.get(0))).get("ancestors") == null ? "/" : String.valueOf(d.get("ancestors"))) + id + "/");
        return d;
    }

    private Map<String, Object> userRow(String username) {
        List l = this.jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id FROM sys_user WHERE username=? LIMIT 1", new Object[]{username});
        return l.isEmpty() ? null : new LinkedHashMap((Map)l.get(0));
    }

    private String deptFullPath(Long deptId) {
        Map<String, Object> d = this.deptSnapshot(deptId);
        return d == null ? "/" : String.valueOf(d.get("path"));
    }

    private void sendReminders() {
        List due = this.jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status IN ('PENDING','IN_PROGRESS') AND k.due_at BETWEEN NOW(3) AND DATE_ADD(NOW(3),INTERVAL 2 HOUR)");
        for (Map x : due) {
            this.notifyOnce(InspectionService.longValue(x.get("id")), "INSPECTION_TASK_DUE", String.valueOf(x.getOrDefault("webhookIds", "[]")));
        }
        List overdue = this.jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status='OVERDUE' AND k.due_at>=DATE_SUB(NOW(3),INTERVAL 1 DAY)");
        for (Map x : overdue) {
            this.notifyOnce(InspectionService.longValue(x.get("id")), "INSPECTION_TASK_OVERDUE", String.valueOf(x.getOrDefault("webhookIds", "[]")));
        }
    }

    private void notifyOnce(Long taskId, String event, String configIds) {
        try {
            this.jdbc.update("INSERT INTO inspection_reminder_log(task_id,event_type) VALUES(?,?)", new Object[]{taskId, event});
            this.notificationService.notifyInspectionTask(taskId, event, configIds);
        }
        catch (DuplicateKeyException duplicateKeyException) {
        }
        catch (Exception ignored) {
            this.jdbc.update("DELETE FROM inspection_reminder_log WHERE task_id=? AND event_type=?", new Object[]{taskId, event});
        }
    }

    private boolean due(Map<String, Object> p, LocalDate d) {
        String type = String.valueOf(p.get("frequency_type"));
        int v = InspectionService.intValue(p.get("frequency_value"), 1);
        LocalDate start = InspectionService.toDate(p.get("start_date"));
        return switch (type) {
            case "WEEKLY" -> {
                if (Math.floorMod((int)(d.toEpochDay() - start.toEpochDay()), 7 * v) == 0) {
                    yield true;
                }
                yield false;
            }
            case "MONTHLY" -> {
                if (d.getDayOfMonth() == Math.min(start.getDayOfMonth(), d.lengthOfMonth()) && Math.floorMod((d.getYear() - start.getYear()) * 12 + d.getMonthValue() - start.getMonthValue(), v) == 0) {
                    yield true;
                }
                yield false;
            }
            case "INTERVAL_DAYS" -> {
                if (Math.floorMod((int)(d.toEpochDay() - start.toEpochDay()), v) == 0) {
                    yield true;
                }
                yield false;
            }
            default -> true;
        };
    }

    private String periodKey(Map<String, Object> p, LocalDate d) {
        return String.valueOf(p.get("frequency_type")) + ":" + String.valueOf(d);
    }

    private Map<String, Object> one(String sql, Object ... args) {
        List l = this.jdbc.queryForList(sql, args);
        if (l.isEmpty()) {
            throw new ResponseStatusException((HttpStatusCode)HttpStatus.NOT_FOUND, "\u6570\u636e\u4e0d\u5b58\u5728");
        }
        return new LinkedHashMap<String, Object>((Map)l.get(0));
    }

    private void require(Map<?, ?> m, String k, String msg) {
        if (InspectionService.text(m, k).isBlank()) {
            this.bad(msg);
        }
    }

    private void bad(String msg) {
        throw new ResponseStatusException((HttpStatusCode)HttpStatus.BAD_REQUEST, msg);
    }

    private String jsonText(Object o) {
        if (o == null) {
            return "[]";
        }
        if (o instanceof String) {
            String s = (String)o;
            return s.isBlank() ? "[]" : s;
        }
        try {
            return this.json.writeValueAsString(o);
        }
        catch (JsonProcessingException e) {
            this.bad("JSON \u6570\u636e\u683c\u5f0f\u9519\u8bef");
            return "[]";
        }
    }

    private static String text(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String defaultText(Map<?, ?> m, String k, String d) {
        String s = InspectionService.text(m, k);
        return s.isBlank() ? d : s;
    }

    private static Long longValue(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        return ((Number)(o instanceof Number ? o : Long.valueOf(String.valueOf(o)))).longValue();
    }

    private static int intValue(Object o, int d) {
        if (o == null || String.valueOf(o).isBlank()) {
            return d;
        }
        return ((Number)(o instanceof Number ? o : Integer.valueOf(String.valueOf(o)))).intValue();
    }

    private static int boolInt(Map<?, ?> m, String k, int d) {
        Object o = m.get(k);
        if (o == null) {
            return d;
        }
        if (o instanceof Boolean) {
            Boolean b = (Boolean)o;
            return b != false ? 1 : 0;
        }
        return InspectionService.intValue(o, d);
    }

    private static LocalDate parseDate(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        return LocalDate.parse(String.valueOf(o).substring(0, 10));
    }

    private static LocalTime parseTime(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        String s = String.valueOf(o);
        return LocalTime.parse((CharSequence)(s.length() == 5 ? s + ":00" : s.substring(0, 8)));
    }

    private static Date sqlDate(Object o) {
        LocalDate d = InspectionService.parseDate(o);
        return d == null ? null : Date.valueOf(d);
    }

    private static Timestamp sqlTimestamp(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        return Timestamp.valueOf(String.valueOf(o).replace("T", " ").substring(0, 19));
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof Date) {
            Date d = (Date)o;
            return d.toLocalDate();
        }
        return InspectionService.parseDate(o);
    }

    @Generated
    public InspectionService(JdbcTemplate jdbc, ObjectMapper json, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.json = json;
        this.notificationService = notificationService;
    }

    private record Owner(String type, Long id, String name, Long deptId, String deptName, String ancestors, Long assigneeId, String assigneeName, String assigneeStatus, String claimedBy) {
    }
}
