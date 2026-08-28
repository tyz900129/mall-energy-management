package com.wanyuecity.energy.platform.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.adapter.TestConnectionResult;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigUpdateRequest;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/config")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<PlatformConfigView> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<PlatformConfigView> update(@Valid @RequestBody PlatformConfigUpdateRequest req) {
        return ApiResponse.ok(service.update(req));
    }

    @PostMapping("/test-connection")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<TestConnectionResult> testConnection() {
        return ApiResponse.ok(service.testConnection());
    }
}

