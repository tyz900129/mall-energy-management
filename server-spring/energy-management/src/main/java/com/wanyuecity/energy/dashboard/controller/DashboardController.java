package com.wanyuecity.energy.dashboard.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.dashboard.service.DashboardService;
import com.wanyuecity.energy.dashboard.service.dto.DashboardStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsView> stats() {
        return ApiResponse.ok(service.stats());
    }
}
