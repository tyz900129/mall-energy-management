package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysRole;
import com.wanyuecity.energy.system.entity.SysRoleMenu;
import com.wanyuecity.energy.system.mapper.SysRoleMapper;
import com.wanyuecity.energy.system.mapper.SysRoleMenuMapper;
import com.wanyuecity.energy.system.mapper.SysUserRoleMapper;
import com.wanyuecity.energy.system.service.dto.SysRolePermissionAssignRequest;
import com.wanyuecity.energy.system.service.dto.SysRolePermissionView;
import com.wanyuecity.energy.system.service.dto.SysRoleUpsertRequest;
import com.wanyuecity.energy.system.service.dto.SysRoleView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private static final Set<String> BUILTIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "SYSTEM_ADMIN", "DEVICE_VERIFIER", "APPLICANT", "VIEWER",
            "PROPERTY_MANAGER", "OPS_PATROL_MANAGER");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final JdbcTemplate jdbcTemplate;

    public List<SysRoleView> list() {
        List<SysRole> roles = roleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("id"));
        return roles.stream().map(r -> SysRoleView.from(r,
                roleMenuMapper.findMenuIdsByRoleId(r.getId()),
                countUsers(r.getId()), countPermissions(r.getId()))).toList();
    }

    public SysRoleView get(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        return SysRoleView.from(r, roleMenuMapper.findMenuIdsByRoleId(id),
                countUsers(id), countPermissions(id));
    }

    @Transactional
    public SysRoleView create(SysRoleUpsertRequest req) {
        Long same = roleMapper.selectCount(new QueryWrapper<SysRole>().eq("code", req.getCode()));
        if (same != null && same > 0) {
            throw BizException.badRequest("角色编码已存在: " + req.getCode());
        }
        SysRole r = new SysRole();
        r.setCode(req.getCode());
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        roleMapper.insert(r);
        return get(r.getId());
    }

    @Transactional
    public SysRoleView update(Long id, SysRoleUpsertRequest req) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        roleMapper.updateById(r);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        if (isBuiltin(r.getCode())) {
            throw BizException.badRequest("内置角色不允许删除: " + r.getCode());
        }
        int users = countUsers(id);
        if (users > 0) {
            throw BizException.badRequest("该角色仍有 " + users + " 个用户在使用，请先调整这些用户的角色后再删除");
        }
        roleMenuMapper.deleteByRoleId(id);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
        roleMapper.deleteById(id);
    }

    @Transactional
    public SysRoleView assignMenus(Long id, List<Long> menuIds) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        roleMenuMapper.deleteByRoleId(id);
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(id);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
        return get(id);
    }

    /** 角色默认权限：全量权限目录 + 已授权码。 */
    public SysRolePermissionView permissions(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id " +
                        "WHERE rp.role_id=? AND p.enabled=1 ORDER BY p.sort_order,p.id",
                String.class, id);
        return new SysRolePermissionView(id, r.getCode(), r.getName(), permissionService.permissionModules(), codes);
    }

    /** 覆盖式保存角色默认权限。 */
    @Transactional
    public SysRolePermissionView assignPermissions(Long id, SysRolePermissionAssignRequest req) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        if ("SUPER_ADMIN".equals(r.getCode())) {
            throw BizException.badRequest("超级管理员固定拥有全部权限，无需配置");
        }
        Set<String> codes = new LinkedHashSet<>();
        if (req.getPermissionCodes() != null) {
            for (String raw : req.getPermissionCodes()) {
                String code = raw == null ? "" : raw.trim();
                if (code.isEmpty()) continue;
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_permission WHERE code=? AND enabled=1", Integer.class, code);
                if (exists == null || exists == 0) throw BizException.badRequest("无效权限: " + code);
                codes.add(code);
            }
        }
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
        for (String code : codes) {
            jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) " +
                    "SELECT ?,id FROM sys_permission WHERE code=? AND enabled=1", id, code);
        }
        return permissions(id);
    }

    private int countUsers(Long roleId) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE role_id=?", Integer.class, roleId);
        return n == null ? 0 : n;
    }

    private int countPermissions(Long roleId) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, roleId);
        return n == null ? 0 : n;
    }

    private boolean isBuiltin(String code) {
        return code != null && BUILTIN_ROLE_CODES.contains(code);
    }
}
