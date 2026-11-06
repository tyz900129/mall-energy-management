package com.wanyuecity.energy.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.SysDepartment;
import com.wanyuecity.energy.system.mapper.SysDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部门下拉数据，仅用户管理使用。 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class SysDepartmentController {

    private final SysDepartmentMapper departmentMapper;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<SysDepartment>> list() {
        return ApiResponse.ok(departmentMapper.selectList(new QueryWrapper<SysDepartment>()
                .eq("enabled", 1)
                .orderByAsc("sort_order", "id")));
    }
}
