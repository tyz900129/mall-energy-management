package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import com.wanyuecity.energy.usage.service.dto.UsageSyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

@Slf4j(topic = "platform-sync")
@Component
@RequiredArgsConstructor
public class EnergyUsageSyncScheduler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final EnergyUsageService usageService;
    private final MigrationMaintenance migrationMaintenance;

    @Async
    @Scheduled(cron = "${energy.usage.monthly-sync-cron:0 0 3 1 * *}", zone = "Asia/Shanghai")
    public void syncPreviousMonth() {
        if (migrationMaintenance.isActive()) {
            log.info("scheduled monthly usage sync skipped during system migration");
            return;
        }
        YearMonth month = YearMonth.now(BUSINESS_ZONE).minusMonths(1);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        if (usageService.hasSuccessfulFullSync(start, end)) {
            log.info("scheduled monthly usage sync skipped because a successful full-range batch exists, month={}", month);
            return;
        }
        try {
            log.info("scheduled monthly usage sync starting, month={}", month);
            UsageSyncRequest request = new UsageSyncRequest(start, end, null, null, false, true);
            Map<String, Object> result = usageService.sync(request);
            log.info("scheduled monthly usage sync completed, month={}, batchNo={}, status={}, requested={}, succeeded={}, failed={}, noData={}",
                    month, result.get("batchNo"), result.get("status"), result.get("requested"),
                    result.get("succeeded"), result.get("failed"), result.get("noDataDevices"));
        } catch (Exception ex) {
            log.error("scheduled monthly usage sync failed, month={}, error={}", month, ex.getMessage(), ex);
        }
    }
}
