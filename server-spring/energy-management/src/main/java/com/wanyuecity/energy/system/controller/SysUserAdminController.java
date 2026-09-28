package com.wanyuecity.energy.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.service.SysUserService;
import com.wanyuecity.energy.system.service.PermissionService;
import com.wanyuecity.energy.system.service.dto.SysUserPasswordRequest;
import com.wanyuecity.energy.system.service.dto.SysUserRoleAssignRequest;
import com.wanyuecity.energy.system.service.dto.SysUserUpsertRequest;
import com.wanyuecity.energy.system.service.dto.SysUserView;
import com.wanyuecity.energy.system.service.dto.UserPermissionProfile;
import com.wanyuecity.energy.system.service.dto.UserPermissionUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

/**
 * User admin endpoints. Distinct from {@link AuthController} which handles login.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class SysUserAdminController {

    private final SysUserService userService;
    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Page<SysUserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(userService.list(keyword, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SysUserView> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SysUserView> create(@Valid @RequestBody SysUserUpsertRequest req) {
        return ApiResponse.ok(userService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SysUserView> update(@PathVariable Long id, @Valid @RequestBody SysUserUpsertRequest req) {
        return ApiResponse.ok(userService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> changePassword(@PathVariable Long id, @Valid @RequestBody SysUserPasswordRequest req) {
        userService.changePassword(id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SysUserView> assignRoles(@PathVariable Long id, @Valid @RequestBody SysUserRoleAssignRequest req) {
        return ApiResponse.ok(userService.assignRoles(id, req.getRoleIds()));
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserPermissionProfile> permissions(@PathVariable Long id) {
        return ApiResponse.ok(permissionService.profile(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserPermissionProfile> updatePermissions(@PathVariable Long id,
                                                                 @Valid @RequestBody UserPermissionUpdateRequest req,
                                                                 Authentication authentication) {
        return ApiResponse.ok(permissionService.replaceOverrides(id, req,
                authentication == null ? null : authentication.getName()));
    }
}
