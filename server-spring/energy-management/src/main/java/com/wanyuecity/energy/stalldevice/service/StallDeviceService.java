package com.wanyuecity.energy.stalldevice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.service.StallNameResolver;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import com.wanyuecity.energy.stalldevice.service.dto.StallDeviceBindRequest;
import com.wanyuecity.energy.stalldevice.service.dto.StallDeviceView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enforces the design rule (设计文档 §2 规则 5):
 *   "一台设备同一时间只能绑定一个计费账户；该规则既要用于流程前置校验，
 *    也要用于每日全局审计。"
 *
 * <p>We extend the same rule to stall binding: one physical device → at most
 * one ACTIVE stall row. The unique index {@code uk_sd_active} on the
 * generated column {@code active_dedup} guarantees this at the DB level;
 * the application pre-check gives a friendlier error message.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StallDeviceService {

    private final StallDeviceMapper stallDeviceMapper;
    private final StallMapper stallMapper;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final StallNameResolver stallNameResolver;

    @Transactional
    public StallDeviceView bind(StallDeviceBindRequest req) {
        Stall stall = stallMapper.selectById(req.getStallId());
        if (stall == null) throw BizException.badRequest("stall not found: id=" + req.getStallId());
        if ("DECOMMISSIONED".equals(stall.getStatus())) {
            throw BizException.badRequest("stall is DECOMMISSIONED");
        }

        PlatformDeviceCurrent dev = deviceMapper.selectOne(
                new LambdaQueryWrapper<PlatformDeviceCurrent>()
                        .eq(PlatformDeviceCurrent::getPlatformDeviceId, req.getPlatformDeviceId()));
        if (dev == null) {
            throw BizException.badRequest("platform device not in current mirror; run sync first");
        }

        StallDevice existing = stallDeviceMapper.findActiveByDeviceId(req.getPlatformDeviceId());
        if (existing != null && !existing.getStallId().equals(stall.getId())) {
            throw BizException.badRequest("device is already bound to stall_id=" + existing.getStallId());
        }
        if (existing != null && existing.getStallId().equals(stall.getId())) {
            // Idempotent re-bind on the same stall — return the existing row.
            return toView(existing, stall, dev);
        }

        StallDevice row = new StallDevice();
        row.setStallId(stall.getId());
        row.setPlatformDeviceId(req.getPlatformDeviceId());
        row.setEnergyType(dev.getEnergyType());
        row.setStatus("ACTIVE");
        row.setEffectiveFrom(LocalDateTime.now());
        row.setBoundBy(req.getBoundBy());
        row.setRemark(req.getRemark());
        try {
            stallDeviceMapper.insert(row);
        } catch (DuplicateKeyException dke) {
            // Lost a race with another concurrent bind on the same device.
            throw BizException.badRequest("device is already bound (concurrent insert detected)");
        }
        log.info("stall-device bound: stall={}, device={}, by={}",
                stall.getStallCode(), dev.getPlatformDeviceId(), req.getBoundBy());
        // 绑定后立即按设备关联的唯一启用计费账户刷新铺位商户名称。
        stall.setStallName(stallNameResolver.refresh(stall.getId()));
        return toView(row, stall, dev);
    }

    @Transactional
    public StallDeviceView unbind(Long stallId, String platformDeviceId, String unboundBy) {
        StallDevice row = stallDeviceMapper.findActiveByDeviceId(platformDeviceId);
        if (row == null) {
            throw BizException.badRequest("device is not actively bound to any stall");
        }
        if (stallId != null && !row.getStallId().equals(stallId)) {
            throw BizException.badRequest("device is not bound to stall_id=" + stallId);
        }
        row.setStatus("INACTIVE");
        row.setEffectiveTo(LocalDateTime.now());
        row.setUnboundBy(unboundBy);
        stallDeviceMapper.updateById(row);
        stallNameResolver.refresh(row.getStallId());
        log.info("stall-device unbound: stall_id={}, device={}, by={}", stallId, platformDeviceId, unboundBy);

        Stall stall = stallMapper.selectById(row.getStallId());
        PlatformDeviceCurrent dev = deviceMapper.selectOne(
                new LambdaQueryWrapper<PlatformDeviceCurrent>()
                        .eq(PlatformDeviceCurrent::getPlatformDeviceId, platformDeviceId));
        return toView(row, stall, dev);
    }

    public List<StallDeviceView> listByStall(Long stallId) {
        List<StallDevice> rows = stallDeviceMapper.listActiveByStall(stallId);
        return enrich(rows);
    }

    public List<StallDeviceView> listByDevice(String platformDeviceId) {
        StallDevice one = stallDeviceMapper.findActiveByDeviceId(platformDeviceId);
        if (one == null) return List.of();
        Stall stall = stallMapper.selectById(one.getStallId());
        PlatformDeviceCurrent dev = deviceMapper.selectOne(
                new LambdaQueryWrapper<PlatformDeviceCurrent>()
                        .eq(PlatformDeviceCurrent::getPlatformDeviceId, platformDeviceId));
        return List.of(toView(one, stall, dev));
    }

    /**
     * spec §17 设备铺位绑定历史：同一 device 多次绑定/解绑都能查到。
     * 按 effective_from 降序。
     */
    public List<StallDeviceView> historyByDevice(String platformDeviceId) {
        List<StallDevice> rows = stallDeviceMapper.listAllByDevice(platformDeviceId);
        if (rows.isEmpty()) return List.of();
        Map<Long, Stall> stalls = new HashMap<>();
        for (StallDevice r : rows) {
            if (!stalls.containsKey(r.getStallId())) {
                stalls.put(r.getStallId(), stallMapper.selectById(r.getStallId()));
            }
        }
        PlatformDeviceCurrent dev = deviceMapper.selectOne(
                new LambdaQueryWrapper<PlatformDeviceCurrent>()
                        .eq(PlatformDeviceCurrent::getPlatformDeviceId, platformDeviceId));
        return rows.stream()
                .map(r -> toView(r, stalls.get(r.getStallId()), dev))
                .toList();
    }

    private List<StallDeviceView> enrich(List<StallDevice> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, Stall> stalls = new HashMap<>();
        for (StallDevice r : rows) {
            if (!stalls.containsKey(r.getStallId())) {
                stalls.put(r.getStallId(), stallMapper.selectById(r.getStallId()));
            }
        }
        Map<String, PlatformDeviceCurrent> devices = new HashMap<>();
        List<String> ids = rows.stream().map(StallDevice::getPlatformDeviceId).distinct().toList();
        if (!ids.isEmpty()) {
            for (PlatformDeviceCurrent device : deviceMapper.selectList(
                    new LambdaQueryWrapper<PlatformDeviceCurrent>()
                            .in(PlatformDeviceCurrent::getPlatformDeviceId, ids))) {
                devices.put(device.getPlatformDeviceId(), device);
            }
        }
        return rows.stream()
                .map(r -> toView(r, stalls.get(r.getStallId()), devices.get(r.getPlatformDeviceId())))
                .toList();
    }

    private StallDeviceView toView(StallDevice r, Stall stall, PlatformDeviceCurrent dev) {
        StallDeviceView v = new StallDeviceView();
        v.setId(r.getId());
        v.setStallId(r.getStallId());
        if (stall != null) {
            v.setStallCode(stall.getStallCode());
            v.setStallName(stall.getStallName());
        }
        v.setPlatformDeviceId(r.getPlatformDeviceId());
        if (dev != null) {
            v.setDeviceName(dev.getDeviceName());
            v.setCommAddress(dev.getCommAddress());
            v.setSerialNumber(dev.getSerialNumber());
        }
        v.setEnergyType(r.getEnergyType());
        v.setStatus(r.getStatus());
        v.setEffectiveFrom(r.getEffectiveFrom());
        v.setEffectiveTo(r.getEffectiveTo());
        v.setBoundBy(r.getBoundBy());
        v.setUnboundBy(r.getUnboundBy());
        v.setRemark(r.getRemark());
        v.setCreatedAt(r.getCreatedAt());
        return v;
    }
}
