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
public class ArrearsNotificationScheduler {
    private final ArrearsNotificationService service;
    private final MigrationMaintenance migrationMaintenance;

    @Async
    @Scheduled(cron="0 0 8 * * *",zone="Asia/Shanghai")
    public void atEight(){run("SCHEDULED_0800");}

    @Async
    @Scheduled(cron="0 25 8 * * *",zone="Asia/Shanghai")
    public void atEightTwentyFive(){run("SCHEDULED_0825");}

    private void run(String trigger){if(migrationMaintenance.isActive()){log.info("arrears sync skipped during migration, trigger={}",trigger);return;}try{service.sync(trigger);}catch(Exception ex){log.error("scheduled arrears sync failed, trigger={}, error={}",trigger,ex.getMessage());}}
}
