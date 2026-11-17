package com.wanyuecity.energy.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 角色数据范围：
 * <ul>
 *   <li>ALL：管理员，可见全部任务；</li>
 *   <li>DEPT_SUB：领导，可见本部门及子部门任务，以及本人任务；</li>
 *   <li>SELF：普通人员，仅可见本人及本部门可认领任务。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final JdbcTemplate jdbc;

    public record Scope(boolean all, boolean deptSub, Long userId, String username,
                        Long deptId, String deptPath, String displayName,
                        Set<Long> roleIds, Set<Long> deptIds) {

        /** 责任归属是否落在当前数据范围内。 */
        public boolean covers(String ownerType, Long ownerId, Long ownerDeptId) {
            if (all) return true;
            String type = ownerType == null ? "" : ownerType;
            if ("USER".equals(type) && ownerId != null && ownerId.equals(userId)) return true;
            if ("ROLE".equals(type) && ownerId != null && roleIds.contains(ownerId)) return true;
            return ownerDeptId != null && deptIds.contains(ownerDeptId);
        }
    }

    public Scope resolve(String username) {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id deptId "
                        + "FROM sys_user WHERE username=? AND status=1 LIMIT 1", username);
        if (users.isEmpty()) return new Scope(false, false, null, username, null, "/", username, Set.of(), Set.of());

        Map<String, Object> u = users.get(0);
        Long userId = ((Number) u.get("id")).longValue();
        String displayName = String.valueOf(u.get("name"));

        Integer level = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CASE r.data_scope WHEN 'ALL' THEN 2 WHEN 'DEPT_SUB' THEN 1 ELSE 0 END),0) "
                        + "FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=?",
                Integer.class, userId);
        int scopeLevel = level == null ? 0 : level;
        boolean all = scopeLevel >= 2;
        boolean deptSub = scopeLevel == 1;

        Long deptId = u.get("deptId") == null ? null : ((Number) u.get("deptId")).longValue();
        if (deptId == null) {
            List<Long> roleDepts = jdbc.queryForList(
                    "SELECT r.dept_id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id "
                            + "WHERE ur.user_id=? AND r.dept_id IS NOT NULL ORDER BY r.id LIMIT 1", Long.class, userId);
            if (!roleDepts.isEmpty()) deptId = roleDepts.get(0);
        }

        String deptPath = "/";
        if (deptId != null) {
            List<String> ancestors = jdbc.queryForList(
                    "SELECT ancestors FROM sys_department WHERE id=? AND deleted_at IS NULL", String.class, deptId);
            String base = ancestors.isEmpty() || ancestors.get(0) == null ? "/" : ancestors.get(0);
            if (!base.endsWith("/")) base = base + "/";
            deptPath = base + deptId + "/";
        }

        Set<Long> roleIds = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId));

        Set<Long> deptIds = new LinkedHashSet<>();
        if (!all && deptId != null) {
            if (deptSub) {
                deptIds.addAll(jdbc.queryForList(
                        "SELECT id FROM sys_department WHERE deleted_at IS NULL AND (id=? OR ancestors LIKE CONCAT(?, '%'))",
                        Long.class, deptId, deptPath));
                deptIds.add(deptId);
            } else {
                deptIds.add(deptId);
                for (String seg : deptPath.split("/")) {
                    if (!seg.isBlank()) deptIds.add(Long.parseLong(seg));
                }
            }
        }
        return new Scope(all, deptSub, userId, username, deptId, deptPath, displayName, roleIds, deptIds);
    }
}
