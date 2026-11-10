package com.wanyuecity.energy.system.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.service.dto.SysDepartmentNode;
import com.wanyuecity.energy.system.service.dto.SysDepartmentUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 组织架构维护：树查询、增改、移动、启停、软删除。
 *
 * <p>层级约定：0=部（GROUP）、1=处/课（SECTION）、2=班（TEAM），最多三级。
 * 删除一律软删除，不物理删除（sys_user / ops_patrol_* / qr_inspection_plan 等按 id 引用）。
 */
@Service
@RequiredArgsConstructor
public class SysDepartmentService {

    private static final int MAX_DEPTH = 2;
    private static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{1,63}$";

    private static final RowMapper<SysDepartmentNode> NODE_MAPPER = (rs, rowNum) -> {
        SysDepartmentNode node = new SysDepartmentNode();
        node.setId(rs.getLong("id"));
        node.setCode(rs.getString("code"));
        node.setName(rs.getString("name"));
        node.setDeptGroup(rs.getString("dept_group"));
        node.setParentId(rs.getObject("parent_id", Long.class));
        node.setAncestors(rs.getString("ancestors"));
        node.setSortOrder(rs.getInt("sort_order"));
        node.setDepth(rs.getInt("depth"));
        node.setDeptLevel(rs.getString("dept_level"));
        node.setStatus(rs.getString("status"));
        node.setEnabled(rs.getInt("enabled"));
        node.setCodeLocked(rs.getInt("code_locked"));
        node.setUserCount(rs.getLong("user_count"));
        return node;
    };

    private static final String NODE_COLUMNS = "d.id, d.code, d.name, d.dept_group, d.parent_id, d.ancestors, " +
            "d.sort_order, d.depth, d.dept_level, d.status, d.enabled, d.code_locked, " +
            "(SELECT COUNT(*) FROM sys_user u WHERE u.department_id = d.id) AS user_count";

    private final JdbcTemplate jdbcTemplate;

    /** 完整组织树（含停用节点，不含已删除）。 */
    public List<SysDepartmentNode> tree() {
        List<SysDepartmentNode> rows = jdbcTemplate.query(
                "SELECT " + NODE_COLUMNS + " FROM sys_department d WHERE d.deleted_at IS NULL " +
                        "ORDER BY d.depth, d.sort_order, d.id", NODE_MAPPER);
        Map<Long, SysDepartmentNode> byId = new LinkedHashMap<>();
        for (SysDepartmentNode node : rows) byId.put(node.getId(), node);
        List<SysDepartmentNode> roots = new ArrayList<>();
        for (SysDepartmentNode node : rows) {
            SysDepartmentNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent == null) roots.add(node);
            else parent.getChildren().add(node);
        }
        return roots;
    }

    public SysDepartmentNode detail(Long id) {
        return requireNode(id);
    }

    @Transactional
    public SysDepartmentNode create(SysDepartmentUpsertRequest request) {
        String code = normalizeCode(request.getCode());
        if (code == null) throw BizException.badRequest("部门编码不能为空");
        if (!code.matches(CODE_PATTERN)) {
            throw BizException.badRequest("部门编码只能使用大写字母、数字、下划线，且以字母开头");
        }
        String name = trimToNull(request.getName());
        if (name == null) throw BizException.badRequest("部门名称不能为空");
        if (existsCode(code, null)) throw BizException.badRequest("部门编码已存在：" + code);

        SysDepartmentNode parent = request.getParentId() == null ? null : requireNode(request.getParentId());
        int depth = parent == null ? 0 : parent.getDepth() + 1;
        if (depth > MAX_DEPTH) throw BizException.badRequest("最多支持三级：部 / 处·课 / 班，不能在班下继续新增");
        String ancestors = parent == null ? "/" : parent.getAncestors() + parent.getId() + "/";
        String deptGroup = trimToNull(request.getDeptGroup());
        if (deptGroup == null) deptGroup = parent == null ? "OPERATION" : parent.getDeptGroup();
        int sortOrder = request.getSortOrder() == null ? nextSortOrder(request.getParentId()) : request.getSortOrder();

        jdbcTemplate.update("INSERT INTO sys_department(code, name, dept_group, parent_id, ancestors, depth, " +
                        "dept_level, sort_order, enabled, code_locked, status) VALUES (?,?,?,?,?,?,?,?,1,0,'ACTIVE')",
                code, name, deptGroup, request.getParentId(), ancestors, depth, levelOf(depth), sortOrder);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sys_department WHERE code = ?", Long.class, code);
        return requireNode(id);
    }

    @Transactional
    public SysDepartmentNode update(Long id, SysDepartmentUpsertRequest request) {
        SysDepartmentNode node = requireNode(id);
        String name = trimToNull(request.getName());
        if (name == null) throw BizException.badRequest("部门名称不能为空");

        String code = node.getCode();
        String requestedCode = normalizeCode(request.getCode());
        if (requestedCode != null && !requestedCode.equals(code)) {
            if (node.getCodeLocked() != null && node.getCodeLocked() == 1) {
                throw BizException.badRequest("系统内置部门编码不允许修改");
            }
            if (!requestedCode.matches(CODE_PATTERN)) {
                throw BizException.badRequest("部门编码只能使用大写字母、数字、下划线，且以字母开头");
            }
            if (existsCode(requestedCode, id)) throw BizException.badRequest("部门编码已存在：" + requestedCode);
            code = requestedCode;
        }
        String deptGroup = trimToNull(request.getDeptGroup());
        if (deptGroup == null) deptGroup = node.getDeptGroup();
        int sortOrder = request.getSortOrder() == null ? node.getSortOrder() : request.getSortOrder();

        jdbcTemplate.update("UPDATE sys_department SET code = ?, name = ?, dept_group = ?, sort_order = ? WHERE id = ?",
                code, name, deptGroup, sortOrder, id);
        return requireNode(id);
    }

    @Transactional
    public SysDepartmentNode move(Long id, Long newParentId, String operator) {
        SysDepartmentNode node = requireNode(id);
        SysDepartmentNode parent = newParentId == null ? null : requireNode(newParentId);
        if (parent != null) {
            if (parent.getId().equals(id)) throw BizException.badRequest("不能把部门移动到自己下面");
            if (containsId(parent.getAncestors(), id)) {
                throw BizException.badRequest("不能把部门移动到自己的下级部门");
            }
        }
        if (Objects.equals(node.getParentId(), newParentId)) return node;

        int newDepth = parent == null ? 0 : parent.getDepth() + 1;
        if (newDepth + subtreeHeight(node) > MAX_DEPTH) {
            throw BizException.badRequest("移动后层级会超过三级上限（部 / 处·课 / 班）");
        }
        String newAncestors = parent == null ? "/" : parent.getAncestors() + parent.getId() + "/";
        String oldPrefix = node.getAncestors() + node.getId() + "/";
        String newPrefix = newAncestors + node.getId() + "/";
        int delta = newDepth - node.getDepth();

        jdbcTemplate.update("UPDATE sys_department SET parent_id = ?, ancestors = ?, depth = ?, dept_level = ? WHERE id = ?",
                newParentId, newAncestors, newDepth, levelOf(newDepth), id);
        if (delta != 0) {
            jdbcTemplate.update("UPDATE sys_department SET ancestors = CONCAT(?, SUBSTRING(ancestors, ?)), depth = depth + ? " +
                            "WHERE deleted_at IS NULL AND ancestors LIKE ?",
                    newPrefix, oldPrefix.length() + 1, delta, oldPrefix + "%");
        } else {
            jdbcTemplate.update("UPDATE sys_department SET ancestors = CONCAT(?, SUBSTRING(ancestors, ?)) " +
                            "WHERE deleted_at IS NULL AND ancestors LIKE ?",
                    newPrefix, oldPrefix.length() + 1, oldPrefix + "%");
        }
        jdbcTemplate.update("INSERT INTO dept_move_log(department_id, from_parent_id, to_parent_id, from_ancestors, " +
                        "to_ancestors, operator_username) VALUES (?,?,?,?,?,?)",
                id, node.getParentId(), newParentId, node.getAncestors(), newAncestors, operator);
        return requireNode(id);
    }

    @Transactional
    public SysDepartmentNode changeStatus(Long id, String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(value)) {
            throw BizException.badRequest("状态只能是 ACTIVE 或 DISABLED");
        }
        requireNode(id);
        jdbcTemplate.update("UPDATE sys_department SET status = ?, enabled = ? WHERE id = ?",
                value, "ACTIVE".equals(value) ? 1 : 0, id);
        return requireNode(id);
    }

    /** 软删除：有下级或有人员时禁止；系统内置部门（code_locked=1）禁止删除。 */
    @Transactional
    public Map<String, Object> delete(Long id) {
        SysDepartmentNode node = requireNode(id);
        if (node.getCodeLocked() != null && node.getCodeLocked() == 1) {
            throw BizException.badRequest("系统内置部门不允许删除，可改为停用");
        }
        Integer children = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_department WHERE parent_id = ? AND deleted_at IS NULL", Integer.class, id);
        if (children != null && children > 0) {
            throw BizException.badRequest("该部门下还有 " + children + " 个下级部门，请先移动或删除下级");
        }
        Integer users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE department_id = ?", Integer.class, id);
        if (users != null && users > 0) {
            throw BizException.badRequest("该部门下还有 " + users + " 名人员，请先调整人员归属");
        }
        jdbcTemplate.update("UPDATE sys_department SET deleted_at = NOW(3), enabled = 0, status = 'DISABLED' WHERE id = ?", id);
        return Map.of("id", id, "softDeleted", true);
    }

    private SysDepartmentNode requireNode(Long id) {
        List<SysDepartmentNode> rows = jdbcTemplate.query(
                "SELECT " + NODE_COLUMNS + " FROM sys_department d WHERE d.id = ? AND d.deleted_at IS NULL",
                NODE_MAPPER, id);
        if (rows.isEmpty()) throw BizException.notFound("部门不存在: " + id);
        return rows.get(0);
    }

    private boolean existsCode(String code, Long excludeId) {
        Integer count = excludeId == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_department WHERE code = ?", Integer.class, code)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_department WHERE code = ? AND id <> ?", Integer.class, code, excludeId);
        return count != null && count > 0;
    }

    private int nextSortOrder(Long parentId) {
        Integer max = parentId == null
                ? jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM sys_department WHERE parent_id IS NULL", Integer.class)
                : jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order), 0) FROM sys_department WHERE parent_id = ?", Integer.class, parentId);
        return (max == null ? 0 : max) + 10;
    }

    private int subtreeHeight(SysDepartmentNode node) {
        Integer maxDepth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(depth), ?) FROM sys_department WHERE deleted_at IS NULL AND ancestors LIKE ?",
                Integer.class, node.getDepth(), node.getAncestors() + node.getId() + "/%");
        return (maxDepth == null ? node.getDepth() : maxDepth) - node.getDepth();
    }

    private static boolean containsId(String ancestors, Long id) {
        return ancestors != null && ancestors.contains("/" + id + "/");
    }

    private static String levelOf(int depth) {
        return depth == 0 ? "GROUP" : depth == 1 ? "SECTION" : "TEAM";
    }

    private static String normalizeCode(String code) {
        String value = trimToNull(code);
        return value == null ? null : value.toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
