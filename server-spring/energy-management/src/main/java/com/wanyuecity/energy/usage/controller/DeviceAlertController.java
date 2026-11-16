package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.DeviceAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy/device-alerts")
@RequiredArgsConstructor
public class DeviceAlertController {
    private final DeviceAlertService service;

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('energy.offline.view')")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(service.getSetting());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('energy.offline.manage')")
    public ApiResponse<Void> updateSettings(@RequestBody Map<String, Object> body) {
        service.updateSetting(body);
        return ApiResponse.ok();
    }

    @GetMapping("/exclusions")
    @PreAuthorize("hasAuthority('energy.offline.view')")
    public ApiResponse<Map<String, Object>> exclusions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.exclusions(keyword, enabled, pageNum, pageSize));
    }

    @PostMapping("/exclusions")
    @PreAuthorize("hasAuthority('energy.offline.manage')")
    public ApiResponse<Void> addExclusion(@RequestBody Map<String, Object> body) {
        service.addExclusion(body);
        return ApiResponse.ok();
    }

    @PostMapping("/exclusions/batch")
    @PreAuthorize("hasAuthority('energy.offline.manage')")
    public ApiResponse<Map<String, Object>> addExclusions(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(Map.of("added", service.addExclusions(body)));
    }

    @GetMapping("/offline-devices")
    @PreAuthorize("hasAuthority('energy.offline.view')")
    public ApiResponse<Map<String, Object>> offlineDevices() {
        return ApiResponse.ok(service.currentOfflineDevices());
    }

    @DeleteMapping("/exclusions/{id}")
    @PreAuthorize("hasAuthority('energy.offline.manage')")
    public ApiResponse<Void> deleteExclusion(@PathVariable Long id) {
        service.deleteExclusion(id);
        return ApiResponse.ok();
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('energy.offline.view')")
    public ApiResponse<Map<String, Object>> events(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.events(level, status, pageNum, pageSize));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('energy.offline.view')")
    public ApiResponse<List<Map<String, Object>>> runs() {
        return ApiResponse.ok(service.runs());
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('energy.offline.manage')")
    public ApiResponse<Map<String, Object>> run() {
        return ApiResponse.ok(service.run("MANUAL"));
    }
}
