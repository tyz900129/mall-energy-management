package com.wanyuecity.energy.system.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.SystemConfig;
import com.wanyuecity.energy.system.service.SystemConfigService;
import com.wanyuecity.energy.system.service.dto.SystemConfigUpsertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService service;

    @GetMapping
    public ApiResponse<List<SystemConfig>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @PostMapping
    public ApiResponse<SystemConfig> upsert(@Valid @RequestBody SystemConfigUpsertRequest req) {
        return ApiResponse.ok(service.upsert(req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
