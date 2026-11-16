package com.wanyuecity.energy.system.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.service.SysRoleService;
import com.wanyuecity.energy.system.service.dto.SysRoleMenuAssignRequest;
import com.wanyuecity.energy.system.service.dto.SysRolePermissionAssignRequest;
import com.wanyuecity.energy.system.service.dto.SysRolePermissionView;
import com.wanyuecity.energy.system.service.dto.SysRoleUpsertRequest;
import com.wanyuecity.energy.system.service.dto.SysRoleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    public ApiResponse<List<SysRoleView>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<SysRoleView> get(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    public ApiResponse<SysRoleView> create(@Valid @RequestBody SysRoleUpsertRequest req) {
        return ApiResponse.ok(roleService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysRoleView> update(@PathVariable Long id, @Valid @RequestBody SysRoleUpsertRequest req) {
        return ApiResponse.ok(roleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/menus")
    public ApiResponse<SysRoleView> assignMenus(@PathVariable Long id, @Valid @RequestBody SysRoleMenuAssignRequest req) {
        return ApiResponse.ok(roleService.assignMenus(id, req.getMenuIds()));
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<SysRolePermissionView> permissions(@PathVariable Long id) {
        return ApiResponse.ok(roleService.permissions(id));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<SysRolePermissionView> assignPermissions(
            @PathVariable Long id, @RequestBody SysRolePermissionAssignRequest req) {
        return ApiResponse.ok(roleService.assignPermissions(id, req));
    }
}
