package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.entity.SysUserRole;
import com.wanyuecity.energy.system.entity.SysRole;
import com.wanyuecity.energy.system.mapper.SysRoleMapper;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.system.mapper.SysUserRoleMapper;
import com.wanyuecity.energy.system.service.dto.SysUserPasswordRequest;
import com.wanyuecity.energy.system.service.dto.SysUserUpsertRequest;
import com.wanyuecity.energy.system.service.dto.SysUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final Set<String> ASSIGNABLE_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "SYSTEM_ADMIN", "DEVICE_VERIFIER", "APPLICANT", "VIEWER");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUser findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public SysUser findById(Long id) { return userMapper.selectById(id); }

    @Transactional
    public void selfRegister(String username, String password, String displayName, String phone) {
        if (userMapper.findByUsername(username) != null) throw BizException.badRequest("用户名已存在");
        SysUser u = new SysUser();
        u.setUsername(username); u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName(displayName); u.setPhone(phone); u.setStatus(1);
        userMapper.insert(u);
        SysRole viewer = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("code", "VIEWER").last("LIMIT 1"));
        if (viewer == null) throw new IllegalStateException("系统缺少只读用户角色");
        replaceUserRole(u.getId(), viewer.getId());
    }

    public List<String> findRoleCodes(Long userId) {
        return roleMapper.findRoleCodesByUserId(userId);
    }

    public void recordLogin(Long userId) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setLastLoginAt(java.time.LocalDateTime.now());
        userMapper.updateById(u);
    }

    public Page<SysUserView> list(String keyword, Integer status, int pageNum, int pageSize) {
        QueryWrapper<SysUser> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like("username", k)
                    .or().like("display_name", k)
                    .or().like("email", k)
                    .or().like("phone", k));
        }
        if (status != null) {
            q.eq("status", status);
        }
        q.orderByDesc("id");
        Page<SysUser> p = userMapper.selectPage(Page.of(pageNum, pageSize), q);
        Page<SysUserView> out = Page.of(p.getCurrent(), p.getSize(), p.getTotal());
        List<SysUserView> records = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            records.add(SysUserView.from(u,
                    roleMapper.findRoleCodesByUserId(u.getId()),
                    roleMapper.findRoleIdsByUserId(u.getId())));
        }
        out.setRecords(records);
        return out;
    }

    public SysUserView get(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("User not found: " + id);
        }
        return SysUserView.from(u,
                roleMapper.findRoleCodesByUserId(u.getId()),
                roleMapper.findRoleIdsByUserId(u.getId()));
    }

    @Transactional
    public SysUserView create(SysUserUpsertRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw BizException.badRequest("Username already exists");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw BizException.badRequest("Password is required for new user");
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        userMapper.insert(u);
        replaceUserRoles(u.getId(), req.getRoleIds());
        return get(u.getId());
    }

    @Transactional
    public SysUserView update(Long id, SysUserUpsertRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("User not found: " + id);
        }
        if (!u.getUsername().equals(req.getUsername())) {
            throw BizException.badRequest("Username cannot be changed");
        }
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        if (req.getStatus() != null) {
            u.setStatus(req.getStatus());
        }
        userMapper.updateById(u);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("User not found: " + id);
        }
        if ("admin".equals(u.getUsername())) {
            throw BizException.badRequest("Cannot delete built-in admin");
        }
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
    }

    @Transactional
    public void changePassword(Long id, SysUserPasswordRequest req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("User not found: " + id);
        }
        if (req.getOldPassword() != null && !req.getOldPassword().isBlank()) {
            if (!passwordEncoder.matches(req.getOldPassword(), u.getPasswordHash())) {
                throw BizException.badRequest("Current password is incorrect");
            }
        }
        if (req.getNewPassword() == null || req.getNewPassword().isBlank()) {
            throw BizException.badRequest("New password is required");
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(u);
    }

    @Transactional
    public SysUserView assignRoles(Long id, List<Long> roleIds) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("User not found: " + id);
        }
        SysRole selectedRole = validateSingleRole(roleIds);
        if ("admin".equals(u.getUsername()) && !"SUPER_ADMIN".equals(selectedRole.getCode())) {
            throw BizException.badRequest("内置管理员必须保留超级管理员角色");
        }
        replaceUserRole(id, selectedRole.getId());
        return get(id);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        SysRole selectedRole = validateSingleRole(roleIds);
        replaceUserRole(userId, selectedRole.getId());
    }

    private SysRole validateSingleRole(List<Long> roleIds) {
        if (roleIds == null || roleIds.size() != 1 || roleIds.get(0) == null) {
            throw BizException.badRequest("每个用户必须且只能选择一个角色");
        }
        SysRole role = roleMapper.selectById(roleIds.get(0));
        if (role == null || !ASSIGNABLE_ROLE_CODES.contains(role.getCode())) {
            throw BizException.badRequest("请选择系统预设的有效角色");
        }
        return role;
    }

    private void replaceUserRole(Long userId, Long roleId) {
        userRoleMapper.deleteByUserId(userId);
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
    }
}
