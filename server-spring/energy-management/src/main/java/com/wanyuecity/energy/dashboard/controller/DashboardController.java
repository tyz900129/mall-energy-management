package com.wanyuecity.energy.dashboard.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.dashboard.service.DashboardService;
import com.wanyuecity.energy.dashboard.service.dto.DashboardStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<DashboardStatsView> stats() {
        return ApiResponse.ok(service.stats());
    }
}
