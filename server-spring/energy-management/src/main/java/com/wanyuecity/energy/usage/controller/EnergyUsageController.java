package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.EnergyUsageService;
import com.wanyuecity.energy.usage.service.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
public class EnergyUsageController {
    private final EnergyUsageService service;

    @GetMapping("/groups") public ApiResponse<List<Map<String,Object>>> groups() { return ApiResponse.ok(service.groups()); }
    @PostMapping("/groups") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody DeviceGroupRequest r) { return ApiResponse.ok(service.createGroup(r)); }
    @PutMapping("/groups/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody DeviceGroupRequest r) { service.updateGroup(id,r); return ApiResponse.ok(null); }
    @DeleteMapping("/groups/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.deleteGroup(id); return ApiResponse.ok(null); }
    @GetMapping("/devices") public ApiResponse<List<Map<String,Object>>> devices(
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Long groupId,
            @RequestParam(required=false) Boolean ungrouped,@RequestParam(required=false) String energyType) {
        return ApiResponse.ok(service.devices(keyword,groupId,ungrouped,energyType));
    }
    @PostMapping("/devices/assign") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Integer> assign(@Valid @RequestBody DeviceGroupAssignRequest r) { return ApiResponse.ok(service.assign(r)); }
    @GetMapping("/metrics") public ApiResponse<List<Map<String,Object>>> metrics() { return ApiResponse.ok(service.metrics()); }
    @PutMapping("/metrics") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> metric(@Valid @RequestBody EnergyMetricRequest r) { service.saveMetric(r); return ApiResponse.ok(null); }
    @PostMapping("/usage/sync") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> sync(@Valid @RequestBody UsageSyncRequest r) { return ApiResponse.ok(service.sync(r)); }
    @GetMapping("/usage/sync-batches") public ApiResponse<List<Map<String,Object>>> batches() { return ApiResponse.ok(service.syncBatches()); }
    @GetMapping("/analysis") public ApiResponse<Map<String,Object>> analysis(
            @RequestParam LocalDate startDate,@RequestParam LocalDate endDate,
            @RequestParam(defaultValue="MONTH") String period,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) String deviceId) {
        return ApiResponse.ok(service.analysis(startDate,endDate,period,groupId,deviceId));
    }
}
