package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j(topic="platform-sync")
@Component
@RequiredArgsConstructor
public class AccountAnomalyMonitorScheduler {
    private final AccountAnomalyMonitorService service;
    private final MigrationMaintenance migrationMaintenance;
    @Async @Scheduled(cron="0 40 8 * * *",zone="Asia/Shanghai")
    public void daily(){if(migrationMaintenance.isActive()){log.info("account anomaly analysis skipped during migration");return;}try{service.syncAndAnalyze("SCHEDULED_0840");}catch(Exception ex){log.error("scheduled account anomaly analysis failed: {}",ex.getMessage());}}
}
