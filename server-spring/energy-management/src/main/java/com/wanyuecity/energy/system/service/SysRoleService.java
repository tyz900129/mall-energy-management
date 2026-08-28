package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysRole;
import com.wanyuecity.energy.system.entity.SysRoleMenu;
import com.wanyuecity.energy.system.mapper.SysRoleMapper;
import com.wanyuecity.energy.system.mapper.SysRoleMenuMapper;
import com.wanyuecity.energy.system.mapper.SysUserRoleMapper;
import com.wanyuecity.energy.system.service.dto.SysRoleUpsertRequest;
import com.wanyuecity.energy.system.service.dto.SysRoleView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private static final Set<String> BUILTIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "SYSTEM_ADMIN", "DEVICE_VERIFIER", "APPLICANT", "VIEWER");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<SysRoleView> list() {
        List<SysRole> roles = roleMapper.selectList(new QueryWrapper<SysRole>()
                .in("code", BUILTIN_ROLE_CODES).orderByAsc("id"));
        return roles.stream().map(r -> SysRoleView.from(r, roleMenuMapper.findMenuIdsByRoleId(r.getId()))).toList();
    }

    public SysRoleView get(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw BizException.notFound("Role not found: " + id);
        }
        return SysRoleView.from(r, roleMenuMapper.findMenuIdsByRoleId(id));
    }

    @Transactional
    public SysRoleView create(SysRoleUpsertRequest req) {
        Long same = roleMapper.selectCount(new QueryWrapper<SysRole>().eq("code", req.getCode()));
        if (same != null && same > 0) {
            throw BizException.badRequest("Role code already exists");
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
            throw BizException.badRequest("Cannot delete built-in role: " + r.getCode());
        }
        // 解除所有用户与该角色的关联
        userRoleMapper.delete(new QueryWrapper<com.wanyuecity.energy.system.entity.SysUserRole>().eq("role_id", id));
        roleMenuMapper.deleteByRoleId(id);
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

    private boolean isBuiltin(String code) {
        return code != null && BUILTIN_ROLE_CODES.contains(code);
    }
}
