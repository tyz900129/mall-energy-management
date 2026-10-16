package com.wanyuecity.energy.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OperationPropertyInspectionService {
    private static final Set<String> FIELD_TYPES = Set.of("BOOLEAN", "TEXT", "NUMBER", "IMAGE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OperationPropertyInspectionService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id,t.name,t.business_type businessType," +
                "COALESCE(d.item_label,CASE WHEN t.business_type='DEFAULT' THEN '通用模板' ELSE t.business_type END) businessTypeLabel," +
                "t.fields_json fieldsJson,t.remark,t.updated_at updatedAt FROM operation_property_inspection_template t " +
                "LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=t.business_type " +
                "WHERE t.enabled=1 ORDER BY t.business_type,t.id");
        return withParsedFields(rows);
    }

    public Map<String, Object> resolve(String token) {
        Map<String, Object> stall = one("SELECT business_type businessType FROM stall WHERE operation_qr_token=?", token);
        String type = Objects.toString(stall.get("businessType"), "");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,name,business_type businessType,version_no versionNo,fields_json fieldsJson " +
                "FROM operation_property_inspection_template WHERE enabled=1 AND business_type IN(?,'DEFAULT') " +
                "ORDER BY CASE WHEN business_type=? THEN 0 ELSE 1 END,id DESC LIMIT 1", type, type);
        if (rows.isEmpty()) throw bad("未配置物业检查模板");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("fields", parse(result.remove("fieldsJson")));
        return result;
    }

    public Map<String,Object> form(String token, String username) {
        Map<String,Object> stall = one("SELECT s.id stallId,s.stall_code stallCode,s.floor_name floorName,s.status stallStatus,s.business_type businessType," +
                "COALESCE(NULLIF(m.merchant_code,''),'') merchantCode,COALESCE(NULLIF(m.merchant_name,''),s.stall_name) merchantName," +
                "COALESCE(NULLIF(m.contact_name,''),'') merchantContactName,COALESCE(NULLIF(m.contact_phone,''),'') merchantContactPhone " +
                "FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id WHERE s.operation_qr_token=?", token);
        if (!"OCCUPIED".equals(stall.get("stallStatus")) || "CK".equalsIgnoreCase(Objects.toString(stall.get("businessType"), "")))
            throw bad("该铺位不在物业日清检查范围");
        Map<String,Object> user = one("SELECT id,COALESCE(NULLIF(display_name,''),username) inspectorName,COALESCE(phone,'') inspectorPhone FROM sys_user WHERE username=?", username);
        Map<String,Object> result = new LinkedHashMap<>(stall);
        result.putAll(user);
        result.put("periodDate", inspectionPeriodDate().toString());
        result.put("violationItems", jdbc.queryForList("SELECT id,violation_category violationCategory,violation_content violationContent,incentive_measure incentiveMeasure FROM operation_violation_item WHERE enabled=1 ORDER BY sort_order,id"));
        return result;
    }

    @Transactional
    public Map<String, Object> createTemplate(Map<String, Object> body, String user) {
        TemplateInput input = validate(body);
        ensureBusinessTypeAvailable(input.businessType(), null);
        Integer internalVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM operation_property_inspection_template WHERE business_type=?", Integer.class, input.businessType());
        jdbc.update("INSERT INTO operation_property_inspection_template(name,business_type,version_no,fields_json,enabled,remark,created_by) VALUES(?,?,?,?,1,?,?)",
                input.name(), input.businessType(), internalVersion, toJson(input.fields()), input.remark(), user);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return templateDetail(id);
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long id, Map<String, Object> body) {
        Map<String, Object> existing = one("SELECT id,business_type businessType,version_no versionNo FROM operation_property_inspection_template WHERE id=? AND enabled=1", id);
        TemplateInput input = validate(body);
        ensureBusinessTypeAvailable(input.businessType(), id);
        String oldType = Objects.toString(existing.get("businessType"));
        int internalVersion = ((Number) existing.get("versionNo")).intValue();
        if (!oldType.equals(input.businessType())) {
            Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM operation_property_inspection_template WHERE business_type=?", Integer.class, input.businessType());
            internalVersion = next == null ? 1 : next;
        }
        jdbc.update("UPDATE operation_property_inspection_template SET name=?,business_type=?,version_no=?,fields_json=?,remark=? WHERE id=?",
                input.name(), input.businessType(), internalVersion, toJson(input.fields()), input.remark(), id);
        return templateDetail(id);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        int changed = jdbc.update("DELETE FROM operation_property_inspection_template WHERE id=? AND enabled=1", id);
        if (changed == 0) throw bad("模板不存在或已删除");
    }

    @Transactional
    public Map<String, Object> submit(String token, Map<String, Object> body, String username) {
        Map<String, Object> stall = one("SELECT s.id,s.stall_code stallCode,s.floor_name floorName,s.status,s.business_type businessType," +
                "COALESCE(NULLIF(m.merchant_code,''),'') merchantCode,COALESCE(NULLIF(m.merchant_name,''),s.stall_name) merchantName,COALESCE(NULLIF(m.contact_phone,''),'') merchantPhone " +
                "FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id WHERE s.operation_qr_token=?", token);
        if (!"OCCUPIED".equals(stall.get("status")) || "CK".equalsIgnoreCase(Objects.toString(stall.get("businessType"), "")))
            throw bad("该铺位不在物业日清检查范围");
        String status = Objects.toString(body.get("status"), "").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NORMAL", "ABNORMAL").contains(status)) throw bad("请选择清场检查结果");
        List<Long> violationIds = new ArrayList<>();
        if (body.get("violationItemIds") instanceof List<?> values) for (Object value : values) {
            try { violationIds.add(Long.valueOf(String.valueOf(value))); } catch (Exception ignored) { }
        }
        List<Map<String,Object>> photos = new ArrayList<>();
        if (body.get("photos") instanceof List<?> values) for (Object value : values) if (value instanceof Map<?,?> map) {
            Map<String,Object> photo = new LinkedHashMap<>((Map<String,Object>) map);
            String url = Objects.toString(photo.get("url"), "");
            if (url.matches("/api/v1/operations/form-images/[a-fA-F0-9]{32}")) photos.add(photo);
        }
        if (photos.size() > 9) throw bad("每次检查最多上传9张照片");
        List<Map<String,Object>> violations = new ArrayList<>();
        if ("ABNORMAL".equals(status)) {
            if (violationIds.isEmpty()) throw bad("异常检查请选择至少一个违规问题");
            if (photos.isEmpty()) throw bad("异常检查请上传现场照片");
            String placeholders = String.join(",", violationIds.stream().map(id -> "?").toList());
            violations.addAll(jdbc.queryForList("SELECT id,violation_category violationCategory,violation_content violationContent,incentive_measure incentiveMeasure FROM operation_violation_item WHERE enabled=1 AND id IN (" + placeholders + ") ORDER BY sort_order,id", violationIds.toArray()));
            if (violations.size() != new HashSet<>(violationIds).size()) throw bad("所选违规项目已停用或被删除，请重新选择");
        }
        String description = violations.stream().map(v -> Objects.toString(v.get("violationContent"), "")).filter(v -> !v.isBlank()).reduce((a,b) -> a + "；" + b).orElse("");
        Map<String, Object> user = one("SELECT id,COALESCE(NULLIF(display_name,''),username) displayName,COALESCE(phone,'') phone FROM sys_user WHERE username=?", username);
        Map<String,Object> template = one("SELECT id,name,version_no versionNo FROM operation_property_inspection_template WHERE business_type='SYSTEM_FIXED' AND enabled=1 ORDER BY id DESC LIMIT 1");
        LocalDate date = inspectionPeriodDate();
        List<Map<String, Object>> clearances = jdbc.queryForList("SELECT id FROM operation_clearance_record WHERE stall_id=? AND period_date=?", stall.get("id"), Date.valueOf(date));
        Object clearanceId = clearances.isEmpty() ? null : clearances.get(0).get("id");
        String number = "PI" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + stall.get("id");
        Map<String,Object> answers = new LinkedHashMap<>(); answers.put("status", status); answers.put("violations", violations); answers.put("photos", photos);
        jdbc.update("INSERT INTO operation_property_inspection(inspection_no,stall_id,period_date,clearance_record_id,template_id,template_version,template_name_snapshot,answers_json,inspector_user_id,inspector_name,inspector_phone,stall_code_snapshot,floor_name_snapshot,merchant_code_snapshot,merchant_name_snapshot,merchant_phone_snapshot,status,exception_description,violation_items_json,photo_urls_json) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE clearance_record_id=VALUES(clearance_record_id),template_id=VALUES(template_id),template_version=VALUES(template_version)," +
                        "template_name_snapshot=VALUES(template_name_snapshot),answers_json=VALUES(answers_json),inspector_user_id=VALUES(inspector_user_id),inspector_name=VALUES(inspector_name),inspector_phone=VALUES(inspector_phone)," +
                        "stall_code_snapshot=VALUES(stall_code_snapshot),floor_name_snapshot=VALUES(floor_name_snapshot),merchant_code_snapshot=VALUES(merchant_code_snapshot),merchant_name_snapshot=VALUES(merchant_name_snapshot),merchant_phone_snapshot=VALUES(merchant_phone_snapshot)," +
                        "status=VALUES(status),exception_description=VALUES(exception_description),violation_items_json=VALUES(violation_items_json),photo_urls_json=VALUES(photo_urls_json),inspected_at=CURRENT_TIMESTAMP(3)",
                number, stall.get("id"), Date.valueOf(date), clearanceId, template.get("id"), template.get("versionNo"), "物业清场检查（固定表单）", toJson(answers),
                user.get("id"), user.get("displayName"), user.get("phone"), stall.get("stallCode"), stall.get("floorName"), stall.get("merchantCode"), stall.get("merchantName"), stall.get("merchantPhone"), status, description, toJson(violations), toJson(photos));
        return one("SELECT id,inspection_no inspectionNo,status,inspected_at inspectedAt FROM operation_property_inspection WHERE stall_id=? AND period_date=?", stall.get("id"), Date.valueOf(date));
    }

    public List<Map<String, Object>> records(String date) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT i.id,i.period_date periodDate,COALESCE(i.stall_code_snapshot,s.stall_code) stallCode,i.floor_name_snapshot floorName,i.merchant_code_snapshot merchantCode,i.merchant_name_snapshot merchantName,i.merchant_phone_snapshot merchantPhone,i.inspector_name inspectorName,i.inspector_phone inspectorPhone," +
                "i.template_name_snapshot templateName,i.status,i.exception_description exceptionDescription,i.violation_items_json violationItemsJson,i.photo_urls_json photosJson,i.inspected_at inspectedAt,i.clearance_record_id clearanceRecordId " +
                "FROM operation_property_inspection i JOIN stall s ON s.id=i.stall_id WHERE (?='' OR i.period_date=?) " +
                "ORDER BY i.period_date DESC,i.inspected_at DESC LIMIT 1000", date, date.isBlank() ? null : Date.valueOf(date));
        for (Map<String,Object> row : rows) {
            row.put("violationItems", parse(Objects.toString(row.remove("violationItemsJson"), "[]")));
            row.put("photos", parse(Objects.toString(row.remove("photosJson"), "[]")));
        }
        return rows;
    }

    private record TemplateInput(String name, String businessType, List<Map<String, Object>> fields, String remark) {}

    private TemplateInput validate(Map<String, Object> body) {
        String name = text(body, "name");
        String type = text(body, "businessType").toUpperCase(Locale.ROOT);
        if (name.isBlank()) throw bad("模板名称不能为空");
        if (type.isBlank()) type = "DEFAULT";
        Object raw = body.get("fields");
        List<Map<String, Object>> fields = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> values) fields.add(new LinkedHashMap<>((Map<String, Object>) values));
        }
        if (fields.isEmpty()) throw bad("至少配置一个检查题");
        Set<String> codes = new HashSet<>();
        for (Map<String, Object> field : fields) {
            String code = Objects.toString(field.get("code"), "").trim();
            String label = Objects.toString(field.get("label"), "").trim();
            String fieldType = Objects.toString(field.get("type"), "BOOLEAN").toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Za-z][A-Za-z0-9_]{0,40}") || label.isBlank()) throw bad("题目编码或标题不正确");
            if (!codes.add(code)) throw bad("题目编码不能重复");
            if (!FIELD_TYPES.contains(fieldType)) throw bad("不支持的题目类型");
            field.put("code", code);
            field.put("label", label);
            field.put("type", fieldType);
        }
        return new TemplateInput(name, type, fields, text(body, "remark"));
    }

    private void ensureBusinessTypeAvailable(String type, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM operation_property_inspection_template WHERE business_type=? AND enabled=1", Integer.class, type)
                : jdbc.queryForObject("SELECT COUNT(*) FROM operation_property_inspection_template WHERE business_type=? AND enabled=1 AND id<>?", Integer.class, type, excludedId);
        if (count != null && count > 0) throw bad("该业态已配置模板，请直接编辑现有模板");
    }

    private Map<String, Object> templateDetail(Long id) {
        Map<String, Object> row = one("SELECT t.id,t.name,t.business_type businessType," +
                "COALESCE(d.item_label,CASE WHEN t.business_type='DEFAULT' THEN '通用模板' ELSE t.business_type END) businessTypeLabel," +
                "t.fields_json fieldsJson,t.remark,t.updated_at updatedAt FROM operation_property_inspection_template t " +
                "LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=t.business_type WHERE t.id=?", id);
        row.put("fields", parse(row.remove("fieldsJson")));
        return row;
    }

    private List<Map<String, Object>> withParsedFields(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : rows) {
            Map<String, Object> row = new LinkedHashMap<>(source);
            row.put("fields", parse(row.remove("fieldsJson")));
            result.add(row);
        }
        return result;
    }

    private LocalDate inspectionPeriodDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.toLocalTime().isBefore(LocalTime.of(9, 0)) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }
    private List<Map<String, Object>> parse(Object value) {
        try { return json.readValue(Objects.toString(value, "[]"), new TypeReference<>() {}); }
        catch (Exception ex) { throw bad("模板解析失败"); }
    }
    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw bad("数据保存失败"); }
    }
    private String text(Map<String, Object> body, String key) { return Objects.toString(body.get(key), "").trim(); }
    private Map<String, Object> one(String query, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(query, args);
        if (rows.isEmpty()) throw bad("数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
