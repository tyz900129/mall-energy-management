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
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class PermissionService {
    /** 门禁认领白名单（self/mine 分支）：对所有物业课开放。
     *  门禁管理端（dashboard、persons、devices、visitors 管理端、sync 写入等）不在此列。
     *  后期如需为特定使用人开放门禁管理权限，走 sys_user_permission 的 ALLOW 例外（见 effectivePermissionCodes），
     *  例外会叠加回最终集合并绕开部门裁剪。 */
    private static final Predicate<String> ACCESS_CLAIM_ONLY = code ->
            "access.self.view".equals(code)
                    || "access.self.manage".equals(code)
                    || "access.event.view.self".equals(code)
                    || "visitor.view.own".equals(code);

    /** 巡场管理基础权限：填报、问题处理、记录查看等全部 patrol.*，
     *  但排除 patrol.config（点位/整改部门/值班排班/分类字典 的新增、编辑、删除保存码）。 */
    private static final Predicate<String> PATROL_BASE = code ->
            code.startsWith("patrol.") && !"patrol.config".equals(code);

    /** 物业各课通用：施工申请所需的铺位/施工权限（角色已有才保留，不会新增）。 */
    private static final Predicate<String> PROP_CONSTRUCTION_ACCESS = code ->
            "stall.view".equals(code) || "construction.create".equals(code) || "construction.view".equals(code);

    /** 部门级权限裁剪：物业各课只保留其职责领域的权限码。
     *  裁剪仅过滤"角色已授权的码"，不会新增角色未授权的码；商管处与客服课不在此列。 */
    private static final Map<String, Predicate<String>> DEPARTMENT_PERMISSION_RULES = Map.of(
            "PROP_EQ", code -> code.startsWith("inspection.") || code.startsWith("energy.")
                    || PATROL_BASE.test(code) || ACCESS_CLAIM_ONLY.test(code)
                    || PROP_CONSTRUCTION_ACCESS.test(code),
            "PROP_SEC", code -> ACCESS_CLAIM_ONLY.test(code) || PATROL_BASE.test(code)
                    || "operation.clearance.review".equals(code) || "operation.property.view".equals(code)
                    || PROP_CONSTRUCTION_ACCESS.test(code),
            "PROP_CLN", code -> "operation.merchant.view".equals(code)
                    || PATROL_BASE.test(code) || ACCESS_CLAIM_ONLY.test(code)
                    || PROP_CONSTRUCTION_ACCESS.test(code)
    );

    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public record DepartmentInfo(Long id, String code, String name) {}

    public Set<String> effectivePermissionCodes(Long userId) {
        if (userId == null) return Set.of();
        Set<String> result = new LinkedHashSet<>(basePermissionCodes(userId));
        for (UserPermissionProfile.UserOverride item : activeOverrides(userId)) {
            if ("DENY".equals(item.getEffect())) result.remove(item.getPermissionCode());
            else if ("ALLOW".equals(item.getEffect())) result.add(item.getPermissionCode());
        }
        return result;
    }

    /** 角色默认权限（含超级管理员短路）再按所属部门裁剪。 */
    private Set<String> basePermissionCodes(Long userId) {
        List<String> roles = roleCodes(userId);
        Set<String> codes = new LinkedHashSet<>();
        if (roles.contains("SUPER_ADMIN")) {
            codes.addAll(allEnabledPermissionCodes());
        } else if (roles.contains("SYSTEM_ADMIN")) {
            codes.addAll(allEnabledPermissionCodes());
            codes.removeIf(code -> code.startsWith("system.user.") || "construction.delete".equals(code));
        } else {
            codes.addAll(rolePermissionCodes(userId));
        }
        DepartmentInfo department = department(userId);
        // 超级管理员/系统管理员已是全量权限，不受"物业课"部门裁剪影响
        if (roles.contains("SUPER_ADMIN") || roles.contains("SYSTEM_ADMIN")) return codes;
        if (department != null) {
            Predicate<String> rule = DEPARTMENT_PERMISSION_RULES.get(department.code());
            if (rule != null) codes.removeIf(code -> !rule.test(code));
        }
        return codes;
    }

    public DepartmentInfo department(Long userId) {
        if (userId == null) return null;
        List<DepartmentInfo> rows = jdbcTemplate.query(
                "SELECT d.id, d.code, d.name FROM sys_user u JOIN sys_department d ON d.id = u.department_id " +
                        "WHERE u.id = ? AND d.enabled = 1",
                (rs, rowNum) -> new DepartmentInfo(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> roleCodes(Long userId) {
        return jdbcTemplate.queryForList("SELECT DISTINCT r.code FROM sys_role r " +
                "JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", String.class, userId);
    }

    private List<String> allEnabledPermissionCodes() {
        return jdbcTemplate.queryForList("SELECT code FROM sys_permission WHERE enabled = 1", String.class);
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
