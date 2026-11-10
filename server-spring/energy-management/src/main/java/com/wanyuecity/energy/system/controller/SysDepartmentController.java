package com.wanyuecity.energy.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.SysDepartment;
import com.wanyuecity.energy.system.mapper.SysDepartmentMapper;
import com.wanyuecity.energy.system.service.SysDepartmentService;
import com.wanyuecity.energy.system.service.dto.SysDepartmentMoveRequest;
import com.wanyuecity.energy.system.service.dto.SysDepartmentNode;
import com.wanyuecity.energy.system.service.dto.SysDepartmentStatusRequest;
import com.wanyuecity.energy.system.service.dto.SysDepartmentUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 部门管理：下拉数据 + 组织架构维护。
 * 维护接口仅超管/系管可用；组织树按 system.dept.view 在前端控制入口。
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class SysDepartmentController {

    private static final String READ_ROLES = "hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')";
    private static final String MANAGE_ROLES = "hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')";

    private final SysDepartmentMapper departmentMapper;
    private final SysDepartmentService departmentService;

    /** 用户管理用的平铺下拉，只返回启用且未删除的部门。 */
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<SysDepartment>> list() {
        return ApiResponse.ok(departmentMapper.selectList(new QueryWrapper<SysDepartment>()
                .eq("enabled", 1)
                .isNull("deleted_at")
                .orderByAsc("sort_order", "id")));
    }

    /** 组织架构树（含停用节点）。 */
    @GetMapping("/tree")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<List<SysDepartmentNode>> tree() {
        return ApiResponse.ok(departmentService.tree());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ApiResponse<SysDepartmentNode> detail(@PathVariable Long id) {
        return ApiResponse.ok(departmentService.detail(id));
    }

    @PostMapping
    @PreAuthorize(MANAGE_ROLES)
    public ApiResponse<SysDepartmentNode> create(@RequestBody SysDepartmentUpsertRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE_ROLES)
    public ApiResponse<SysDepartmentNode> update(@PathVariable Long id, @RequestBody SysDepartmentUpsertRequest request) {
        return ApiResponse.ok(departmentService.update(id, request));
    }

    @PutMapping("/{id}/move")
    @PreAuthorize(MANAGE_ROLES)
    public ApiResponse<SysDepartmentNode> move(@PathVariable Long id, @RequestBody SysDepartmentMoveRequest request,
                                               Authentication authentication) {
        String operator = authentication == null ? "system" : authentication.getName();
        return ApiResponse.ok(departmentService.move(id, request.getParentId(), operator));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(MANAGE_ROLES)
    public ApiResponse<SysDepartmentNode> changeStatus(@PathVariable Long id, @RequestBody SysDepartmentStatusRequest request) {
        return ApiResponse.ok(departmentService.changeStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE_ROLES)
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        return ApiResponse.ok(departmentService.delete(id));
    }
}
