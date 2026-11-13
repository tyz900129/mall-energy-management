package com.wanyuecity.energy.platform.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.adapter.TestConnectionResult;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigUpdateRequest;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<PlatformConfigView> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<PlatformConfigView> update(@Valid @RequestBody PlatformConfigUpdateRequest req) {
        return ApiResponse.ok(service.update(req));
    }

    @PostMapping("/test-connection")
    public ApiResponse<TestConnectionResult> testConnection() {
        return ApiResponse.ok(service.testConnection());
    }
}
