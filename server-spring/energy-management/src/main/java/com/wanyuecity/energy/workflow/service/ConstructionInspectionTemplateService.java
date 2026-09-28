package com.wanyuecity.energy.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.workflow.service.dto.ConstructionInspectionTemplateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConstructionInspectionTemplateService {
    private static final Set<String> TYPES = Set.of("SECURITY", "ENGINEERING", "CLEANING");
    private static final Set<String> CONTROLS = Set.of("NORMAL_ABNORMAL", "BOOLEAN", "SELECT", "MULTISELECT", "NUMBER", "TEXT");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> list(boolean enabledOnly) {
        String sql = "SELECT id,inspection_type inspectionType,name,fields_json fieldsJson,enabled,updated_at updatedAt FROM construction_inspection_template"
                + (enabledOnly ? " WHERE enabled=1" : "") + " ORDER BY FIELD(inspection_type,'SECURITY','ENGINEERING','CLEANING')";
        return jdbcTemplate.queryForList(sql);
    }

    public Map<String, Object> require(String type) {
        String normalized = normalizeType(type);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,inspection_type inspectionType,name,fields_json fieldsJson,enabled,updated_at updatedAt FROM construction_inspection_template WHERE inspection_type=? LIMIT 1", normalized);
        if (rows.isEmpty()) throw BizException.notFound("施工巡检题库不存在");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> save(String type, ConstructionInspectionTemplateRequest request) {
        String normalized = normalizeType(type);
        validateFields(request.getFieldsJson());
        int enabled = Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1;
        jdbcTemplate.update("UPDATE construction_inspection_template SET name=?,fields_json=?,enabled=? WHERE inspection_type=?",
                request.getName().trim(), request.getFieldsJson(), enabled, normalized);
        return require(normalized);
    }

    public JsonNode parseFields(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception ex) { throw BizException.badRequest("施工巡检题目格式无效"); }
    }

    private void validateFields(String json) {
        JsonNode fields = parseFields(json);
        if (!fields.isArray() || fields.isEmpty()) throw BizException.badRequest("至少配置一个巡检题目");
        if (fields.size() > 100) throw BizException.badRequest("巡检题目不能超过100项");
        for (JsonNode field : fields) {
            String key = field.path("key").asText("").trim();
            String label = field.path("label").asText("").trim();
            String control = field.path("controlType").asText("").toUpperCase(Locale.ROOT);
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{1,47}")) throw BizException.badRequest("题目标识格式不正确：" + key);
            if (label.isBlank() || label.length() > 100) throw BizException.badRequest("题目名称不能为空且不能超过100字");
            if (!CONTROLS.contains(control)) throw BizException.badRequest("不支持的题目类型：" + control);
            if (("SELECT".equals(control) || "MULTISELECT".equals(control)) && !field.path("options").isArray())
                throw BizException.badRequest("选择题必须配置选项：" + label);
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw BizException.badRequest("不支持的施工巡检类型");
        return normalized;
    }
}
