package com.wanyuecity.energy.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily 02:00 trigger for {@link PlatformSyncService#runScheduled()}. The
 * cron expression is configurable via {@code energy.platform.sync-cron}
 * (env {@code ENERGY_PLATFORM_SYNC_CRON}); the default below matches the
 * design spec.
 */
@Slf4j(topic = "platform-sync")
@Component
@RequiredArgsConstructor
public class PlatformSyncScheduler {

    private final PlatformSyncService syncService;

    @Scheduled(cron = "${energy.platform.sync-cron:0 0 2 * * ?}")
    public void runDaily() {
        try {
            log.info("scheduled platform sync starting");
            syncService.runScheduled();
        } catch (Exception ex) {
            log.error("scheduled platform sync failed: {}", ex.getMessage(), ex);
        }
    }
}
