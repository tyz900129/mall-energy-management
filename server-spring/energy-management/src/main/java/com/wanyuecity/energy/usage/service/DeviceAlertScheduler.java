package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j(topic = "device-alert")
@Component
@RequiredArgsConstructor
public class DeviceAlertScheduler {
    private final DeviceAlertService service;
    private final MigrationMaintenance migrationMaintenance;

    @Async
    @Scheduled(fixedDelay = 300_000, zone = "Asia/Shanghai")
    public void tick() {
        if (migrationMaintenance.isActive()) {
            log.debug("device alert skipped during migration");
            return;
        }
        try {
            service.run("SCHEDULED");
        } catch (Exception ex) {
            log.error("scheduled device alert failed: {}", ex.getMessage());
        }
    }
}
