package com.wanyuecity.energy.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.system.service.dto.UserPermissionProfile;
import com.wanyuecity.energy.system.service.dto.UserPermissionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public record DepartmentInfo(Long id, String code, String name, String ancestors) {}

    public Set<String> effectivePermissionCodes(Long userId) {
        if (userId == null) return Set.of();
        Set<String> result = new LinkedHashSet<>(basePermissionCodes(userId));
        for (UserPermissionProfile.UserOverride item : activeOverrides(userId)) {
            if ("DENY".equals(item.getEffect())) result.remove(item.getPermissionCode());
            else if ("ALLOW".equals(item.getEffect())) result.add(item.getPermissionCode());
        }
        return result;
    }

    /**
     * 角色默认权限：角色授权（sys_role_permission）是唯一权威来源。
     * V107 起取消部门裁剪，V108 起超级管理员/系统管理员也通过角色授权显式持有权限，
     * 不再有任何代码内特判来源，满足「有效权限 = 角色权限 ∪ 用户 ALLOW − 用户 DENY」。
     */
    private Set<String> basePermissionCodes(Long userId) {
        return new LinkedHashSet<>(rolePermissionCodes(userId));
    }

    public DepartmentInfo department(Long userId) {
        if (userId == null) return null;
        List<DepartmentInfo> rows = jdbcTemplate.query(
                "SELECT d.id, d.code, d.name, d.ancestors FROM sys_user u JOIN sys_department d ON d.id = u.department_id " +
                        "WHERE u.id = ? AND d.enabled = 1",
                (rs, rowNum) -> new DepartmentInfo(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("ancestors")),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 全量权限目录（按模块分组），供个人权限与角色默认权限共用。 */
    public List<UserPermissionProfile.PermissionModule> permissionModules() {
        Map<String, UserPermissionProfile.PermissionModule> modules = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT id,code,name,module_code,module_name,action_type,description FROM sys_permission WHERE enabled=1 ORDER BY sort_order,id", rs -> {
            String moduleCode = rs.getString("module_code");
            String moduleName = rs.getString("module_name");
            UserPermissionProfile.PermissionModule module = modules.computeIfAbsent(moduleCode,
                    ignored -> new UserPermissionProfile.PermissionModule(moduleCode, moduleName, new ArrayList<>()));
            module.getPermissions().add(new UserPermissionProfile.PermissionItem(
                    rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("action_type"), rs.getString("description")));
        });
        return new ArrayList<>(modules.values());
    }

    public UserPermissionProfile profile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("用户不存在: " + userId);
        List<String> roleCodes = new ArrayList<>(basePermissionCodes(userId));
        List<UserPermissionProfile.UserOverride> overrides = activeOverrides(userId);
        return new UserPermissionProfile(userId, user.getUsername(), permissionModules(),
                roleCodes, new ArrayList<>(effectivePermissionCodes(userId)), overrides);
    }

    @Transactional
    public UserPermissionProfile replaceOverrides(Long userId, UserPermissionUpdateRequest request, String operator) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("用户不存在: " + userId);
        if ("admin".equals(user.getUsername())) throw BizException.badRequest("内置超级管理员不允许设置个人例外权限");
        List<UserPermissionProfile.UserOverride> before = activeOverrides(userId);
        List<UserPermissionUpdateRequest.OverrideItem> items = request.getOverrides() == null ? List.of() : request.getOverrides();
        Set<String> seen = new LinkedHashSet<>();
        for (UserPermissionUpdateRequest.OverrideItem item : items) {
            String code = item.getPermissionCode() == null ? "" : item.getPermissionCode().trim();
            String effect = item.getEffect() == null ? "" : item.getEffect().trim().toUpperCase();
            if (!Set.of("ALLOW", "DENY").contains(effect)) throw BizException.badRequest("权限效果必须是 ALLOW 或 DENY");
            if (!seen.add(code)) throw BizException.badRequest("存在重复权限: " + code);
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE code=? AND enabled=1", Integer.class, code);
            if (count == null || count == 0) throw BizException.badRequest("无效权限: " + code);
        }
        jdbcTemplate.update("DELETE FROM sys_user_permission WHERE user_id=?", userId);
        for (UserPermissionUpdateRequest.OverrideItem item : items) {
            jdbcTemplate.update("INSERT INTO sys_user_permission(user_id,permission_id,effect,expires_at,reason,granted_by) " +
                            "SELECT ?,id,?,?,?,? FROM sys_permission WHERE code=?",
                    userId, item.getEffect().trim().toUpperCase(), toTimestamp(item.getExpiresAt()),
                    trimToNull(item.getReason()), operator, item.getPermissionCode().trim());
        }
        List<UserPermissionProfile.UserOverride> after = activeOverrides(userId);
        jdbcTemplate.update("INSERT INTO sys_permission_change_log(user_id,operator_username,before_json,after_json) VALUES(?,?,?,?)",
                userId, operator, json(before), json(after));
        return profile(userId);
    }

    private List<String> rolePermissionCodes(Long userId) {
        return jdbcTemplate.queryForList("SELECT DISTINCT p.code FROM sys_permission p " +
                "JOIN sys_role_permission rp ON rp.permission_id=p.id " +
                "JOIN sys_user_role ur ON ur.role_id=rp.role_id " +
                "WHERE ur.user_id=? AND p.enabled=1 ORDER BY p.code", String.class, userId);
    }

    private List<UserPermissionProfile.UserOverride> activeOverrides(Long userId) {
        return jdbcTemplate.query("SELECT p.code,up.effect,up.expires_at,up.reason FROM sys_user_permission up " +
                        "JOIN sys_permission p ON p.id=up.permission_id WHERE up.user_id=? AND p.enabled=1 " +
                        "AND (up.expires_at IS NULL OR up.expires_at>NOW()) ORDER BY p.sort_order,p.id",
                (rs, rowNum) -> new UserPermissionProfile.UserOverride(rs.getString("code"), rs.getString("effect"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime(), rs.getString("reason")), userId);
    }

    private Timestamp toTimestamp(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("权限变更记录序列化失败", e); }
    }
}
