package com.wanyuecity.energy.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OperationClearanceTemplateService {
    private static final Set<String> FIELD_TYPES = Set.of("BOOLEAN", "TEXT", "NUMBER", "IMAGE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OperationClearanceTemplateService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id,t.name,t.business_type businessType," +
                "COALESCE(d.item_label,CASE WHEN t.business_type='DEFAULT' THEN '通用模板' ELSE t.business_type END) businessTypeLabel," +
                "t.fields_json fieldsJson,t.remark,t.created_by createdBy,t.updated_at updatedAt " +
                "FROM operation_clearance_template t LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' " +
                "AND d.item_value=t.business_type WHERE t.enabled=1 ORDER BY t.business_type,t.id");
        return withParsedFields(rows);
    }

    public Map<String, Object> resolveByToken(String token) {
        List<Map<String, Object>> stalls = jdbc.queryForList("SELECT business_type businessType FROM stall WHERE operation_qr_token=? AND status<>'DECOMMISSIONED'", token);
        if (stalls.isEmpty()) throw bad("铺位不存在");
        String type = Objects.toString(stalls.get(0).get("businessType"), "");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,name,business_type businessType,version_no versionNo,fields_json fieldsJson " +
                "FROM operation_clearance_template WHERE enabled=1 AND business_type IN (?, 'DEFAULT') " +
                "ORDER BY CASE WHEN business_type=? THEN 0 ELSE 1 END,id DESC LIMIT 1", type, type);
        if (rows.isEmpty()) throw bad("未配置可用的日清模板");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("fields", parse(result.remove("fieldsJson")));
        return result;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, String user) {
        TemplateInput input = validate(body);
        ensureBusinessTypeAvailable(input.businessType(), null);
        Integer internalVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM operation_clearance_template WHERE business_type=?", Integer.class, input.businessType());
        jdbc.update("INSERT INTO operation_clearance_template(name,business_type,version_no,fields_json,enabled,remark,created_by) VALUES(?,?,?,?,1,?,?)",
                input.name(), input.businessType(), internalVersion, json(input.fields()), input.remark(), user);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Map<String, Object> existing = one("SELECT id,business_type businessType,version_no versionNo FROM operation_clearance_template WHERE id=? AND enabled=1", id);
        TemplateInput input = validate(body);
        ensureBusinessTypeAvailable(input.businessType(), id);
        String oldType = Objects.toString(existing.get("businessType"));
        int internalVersion = ((Number) existing.get("versionNo")).intValue();
        if (!oldType.equals(input.businessType())) {
            Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM operation_clearance_template WHERE business_type=?", Integer.class, input.businessType());
            internalVersion = next == null ? 1 : next;
        }
        jdbc.update("UPDATE operation_clearance_template SET name=?,business_type=?,version_no=?,fields_json=?,remark=? WHERE id=?",
                input.name(), input.businessType(), internalVersion, json(input.fields()), input.remark(), id);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        int changed = jdbc.update("DELETE FROM operation_clearance_template WHERE id=? AND enabled=1", id);
        if (changed == 0) throw bad("模板不存在或已删除");
    }

    public Evaluation evaluate(String token, Object raw) {
        Map<String, Object> template = resolveByToken(token);
        Map<String, Object> answers = raw instanceof Map<?, ?> values ? (Map<String, Object>) values : new LinkedHashMap<>();
        List<Map<String, Object>> fields = (List<Map<String, Object>>) template.get("fields");
        boolean normal = true;
        for (Map<String, Object> field : fields) {
            String code = Objects.toString(field.get("code"));
            String label = Objects.toString(field.get("label"), code);
            String type = Objects.toString(field.get("type"), "BOOLEAN");
            Object value = answers.get(code);
            if (Boolean.TRUE.equals(field.get("required")) && (value == null || value.toString().isBlank())) throw bad("请填写：" + label);
            if ("BOOLEAN".equals(type) && field.containsKey("expected") &&
                    !Objects.equals(Boolean.parseBoolean(Objects.toString(value, "false")), Boolean.parseBoolean(Objects.toString(field.get("expected"))))) normal = false;
        }
        return new Evaluation(((Number) template.get("id")).longValue(), ((Number) template.get("versionNo")).intValue(),
                Objects.toString(template.get("name")), json(answers), normal, answers);
    }

    public record Evaluation(Long templateId, Integer version, String name, String answersJson, boolean normal, Map<String, Object> answers) {}
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
                ? jdbc.queryForObject("SELECT COUNT(*) FROM operation_clearance_template WHERE business_type=? AND enabled=1", Integer.class, type)
                : jdbc.queryForObject("SELECT COUNT(*) FROM operation_clearance_template WHERE business_type=? AND enabled=1 AND id<>?", Integer.class, type, excludedId);
        if (count != null && count > 0) throw bad("该业态已配置模板，请直接编辑现有模板");
    }

    private Map<String, Object> detail(Long id) {
        Map<String, Object> row = one("SELECT t.id,t.name,t.business_type businessType," +
                "COALESCE(d.item_label,CASE WHEN t.business_type='DEFAULT' THEN '通用模板' ELSE t.business_type END) businessTypeLabel," +
                "t.fields_json fieldsJson,t.remark,t.updated_at updatedAt FROM operation_clearance_template t " +
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

    private List<Map<String, Object>> parse(Object value) {
        try { return json.readValue(Objects.toString(value, "[]"), new TypeReference<>() {}); }
        catch (Exception ex) { throw bad("模板内容无法解析"); }
    }
    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw bad("模板内容无法保存"); }
    }
    private String text(Map<String, Object> body, String key) { return Objects.toString(body.get(key), "").trim(); }
    private Map<String, Object> one(String query, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(query, args);
        if (rows.isEmpty()) throw bad("模板不存在");
        return new LinkedHashMap<>(rows.get(0));
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
