package com.wanyuecity.energy.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.platform.service.dto.PlatformDeviceView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台设备只读视图（spec §4 GET /devices/search）。
 * <p>
 * 数据源 {@code platform_device_current}（由 Phase 3 同步状态机维护）。
 * 当前表为空时返回空分页，但不影响接口契约。
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final PlatformDeviceCurrentMapper deviceMapper;

    /**
     * 模糊搜索：commAddress / serialNumber / name 任一命中即可。
     * <p>{@code bound} 状态可选：null=全部，{@code true}=已绑定 stall，{@code false}=未绑定。
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<Page<PlatformDeviceView>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String energyType,
            @RequestParam(required = false) Boolean bound,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        QueryWrapper<PlatformDeviceCurrent> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like("device_name", k)
                    .or().like("comm_address", k)
                    .or().like("serial_number", k)
                    .or().like("platform_device_id", k));
        }
        if (energyType != null && !energyType.isBlank()) {
            q.eq("energy_type", energyType.trim());
        }
        if (bound != null) {
            // 平台 current 表没有 bound 字段；通过子查询判定
            if (Boolean.TRUE.equals(bound)) {
                q.exists("SELECT 1 FROM stall_device sd WHERE sd.platform_device_id = platform_device_current.platform_device_id AND sd.status = 'ACTIVE'");
            } else {
                q.notExists("SELECT 1 FROM stall_device sd WHERE sd.platform_device_id = platform_device_current.platform_device_id AND sd.status = 'ACTIVE'");
            }
        }
        q.orderByDesc("last_synced_at");
        Page<PlatformDeviceCurrent> p = deviceMapper.selectPage(Page.of(pageNum, pageSize), q);
        Page<PlatformDeviceView> out = Page.of(pageNum, pageSize, p.getTotal());
        out.setRecords(p.getRecords().stream().map(this::toView).toList());
        return ApiResponse.ok(out);
    }

    @GetMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<PlatformDeviceView> get(@PathVariable String deviceId) {
        PlatformDeviceCurrent d = deviceMapper.selectOne(new QueryWrapper<PlatformDeviceCurrent>()
                .eq("platform_device_id", deviceId)
                .last("LIMIT 1"));
        if (d == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(toView(d));
    }

    private PlatformDeviceView toView(PlatformDeviceCurrent d) {
        PlatformDeviceView v = new PlatformDeviceView();
        v.setPlatformDeviceId(d.getPlatformDeviceId());
        v.setDeviceName(d.getDeviceName());
        v.setCommAddress(d.getCommAddress());
        v.setSerialNumber(d.getSerialNumber());
        v.setGatewayId(d.getGatewayId());
        v.setGatewayName(d.getGatewayName());
        v.setChannelName(d.getChannelName());
        v.setProductName(d.getProductName());
        v.setEnergyType(d.getEnergyType());
        v.setEnergyTypeName(d.getEnergyTypeName());
        v.setCommStatus(d.getCommStatus());
        v.setLastAcqTime(d.getLastAcqTime());
        v.setLastSyncedAt(d.getLastSyncedAt());
        v.setSyncBatchId(d.getSyncBatchId());
        return v;
    }
}
