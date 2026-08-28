package com.wanyuecity.energy.stalldevice.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.stalldevice.service.StallDeviceService;
import com.wanyuecity.energy.stalldevice.service.dto.StallDeviceBindRequest;
import com.wanyuecity.energy.stalldevice.service.dto.StallDeviceView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StallDeviceController {

    private final StallDeviceService service;

    @PostMapping("/stalls/{stallId}/devices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<StallDeviceView> bind(@PathVariable Long stallId,
                                             @Valid @RequestBody StallDeviceBindRequest req,
                                             Authentication auth) {
        req.setStallId(stallId);
        if (req.getBoundBy() == null) req.setBoundBy(auth == null ? null : auth.getName());
        return ApiResponse.ok(service.bind(req));
    }

    @DeleteMapping("/stalls/{stallId}/devices/{deviceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<StallDeviceView> unbind(@PathVariable Long stallId,
                                               @PathVariable String deviceId,
                                               Authentication auth) {
        return ApiResponse.ok(service.unbind(stallId, deviceId, auth == null ? null : auth.getName()));
    }

    @GetMapping("/stalls/{stallId}/devices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<List<StallDeviceView>> listByStall(@PathVariable Long stallId) {
        return ApiResponse.ok(service.listByStall(stallId));
    }

    @GetMapping("/devices/{deviceId}/stall")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<List<StallDeviceView>> listByDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(service.listByDevice(deviceId));
    }

    /**
     * spec §17：设备的铺位绑定历史轨迹（含已解绑记录）。
     */
    @GetMapping("/devices/{deviceId}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<List<StallDeviceView>> historyByDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(service.historyByDevice(deviceId));
    }
}
