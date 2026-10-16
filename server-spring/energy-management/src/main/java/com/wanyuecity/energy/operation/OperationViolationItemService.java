package com.wanyuecity.energy.operation;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OperationViolationItemService {
    private final JdbcTemplate jdbc;

    public OperationViolationItemService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> list(boolean includeDisabled) {
        return jdbc.queryForList("SELECT id,violation_category violationCategory,violation_content violationContent," +
                "incentive_measure incentiveMeasure,enabled,sort_order sortOrder,created_at createdAt,updated_at updatedAt " +
                "FROM operation_violation_item WHERE (?=1 OR enabled=1) ORDER BY enabled DESC,sort_order,id", includeDisabled ? 1 : 0);
    }

    @Transactional
    public Map<String,Object> create(Map<String,Object> body, String username) {
        Input input = validate(body);
        jdbc.update("INSERT INTO operation_violation_item(violation_category,violation_content,incentive_measure,enabled,sort_order,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",
                input.category(), input.content(), input.measure(), input.enabled(), input.sortOrder(), username, username);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return one(id);
    }

    @Transactional
    public Map<String,Object> update(Long id, Map<String,Object> body, String username) {
        one(id);
        Input input = validate(body);
        jdbc.update("UPDATE operation_violation_item SET violation_category=?,violation_content=?,incentive_measure=?,enabled=?,sort_order=?,updated_by=? WHERE id=?",
                input.category(), input.content(), input.measure(), input.enabled(), input.sortOrder(), username, id);
        return one(id);
    }

    @Transactional
    public void delete(Long id) {
        if (jdbc.update("DELETE FROM operation_violation_item WHERE id=?", id) == 0) throw bad("违规项目不存在或已删除");
    }

    private Input validate(Map<String,Object> body) {
        String category = text(body.get("violationCategory"));
        String content = text(body.get("violationContent"));
        String measure = text(body.get("incentiveMeasure"));
        if (category.isBlank()) throw bad("违规分类不能为空");
        if (content.isBlank()) throw bad("违规内容不能为空");
        if (category.length() > 64 || content.length() > 500 || measure.length() > 500) throw bad("填写内容过长");
        int enabled = Boolean.FALSE.equals(body.get("enabled")) || Objects.equals(body.get("enabled"), 0) ? 0 : 1;
        int sortOrder = body.get("sortOrder") instanceof Number number ? number.intValue() : 0;
        return new Input(category, content, measure, enabled, sortOrder);
    }

    private Map<String,Object> one(Long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,violation_category violationCategory,violation_content violationContent,incentive_measure incentiveMeasure,enabled,sort_order sortOrder,created_at createdAt,updated_at updatedAt FROM operation_violation_item WHERE id=?", id);
        if (rows.isEmpty()) throw bad("违规项目不存在");
        return new LinkedHashMap<>(rows.get(0));
    }
    private String text(Object value) { return Objects.toString(value, "").trim(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record Input(String category,String content,String measure,int enabled,int sortOrder) {}
}
