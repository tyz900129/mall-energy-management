package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j(topic = "platform-sync")
@Component
@RequiredArgsConstructor
public class ExceptionAccountScheduler {
    private final ExceptionAccountService service;
    private final MigrationMaintenance migrationMaintenance;

    @Async
    @Scheduled(fixedDelay = 300_000, zone = "Asia/Shanghai")
    public void tick() {
        if (migrationMaintenance.isActive()) {
            log.debug("exception account sync skipped during migration");
            return;
        }
        try {
            service.sync("SCHEDULED");
        } catch (Exception ex) {
            log.error("scheduled exception account sync failed: {}", ex.getMessage());
        }
    }
}
