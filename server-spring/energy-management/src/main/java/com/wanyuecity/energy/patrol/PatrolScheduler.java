package com.wanyuecity.energy.patrol;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatrolScheduler {
    private final PatrolService service;
    private final MigrationMaintenance migrationMaintenance;

    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Shanghai")
    public void dailyEntry() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.pushDailyEntry();
        } catch (Exception ex) {
            log.error("patrol daily entry failed: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 0 17 * * ?", zone = "Asia/Shanghai")
    public void unassignedReminder() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.remindUnassigned();
        } catch (Exception ex) {
            log.error("patrol unassigned reminder failed: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(cron = "0 5 * * * ?", zone = "Asia/Shanghai")
    public void overdueScan() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.scanOverdue();
        } catch (Exception ex) {
            log.error("patrol overdue scan failed: {}", ex.getMessage(), ex);
        }
    }
}
