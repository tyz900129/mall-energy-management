package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpsPatrolScheduler {
    private final OpsPatrolService service;
    private final MigrationMaintenance migrationMaintenance;

    /** 每日 00:05 生成当天全部处室的时段任务 */
    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void dailyGenerate() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.generateTasks(LocalDate.now());
        } catch (Exception ex) {
            log.error("ops patrol daily generate failed: {}", ex.getMessage(), ex);
        }
    }

    /** 每分钟扫描：到期推送任务卡片 */
    @Scheduled(cron = "30 * * * * ?", zone = "Asia/Shanghai")
    public void pushScan() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.generateTasks(LocalDate.now());
            service.pushDueTasks();
        } catch (Exception ex) {
            log.error("ops patrol push scan failed: {}", ex.getMessage(), ex);
        }
    }

    /** 每分钟扫描：未开始催办、截止前提醒、任务逾期 */
    @Scheduled(cron = "50 * * * * ?", zone = "Asia/Shanghai")
    public void progressScan() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.urgeDueTasks();
            service.remindDueTasks();
            service.markOverdueTasks();
        } catch (Exception ex) {
            log.error("ops patrol progress scan failed: {}", ex.getMessage(), ex);
        }
    }

    /** 每 10 分钟扫描：问题逾期标记与推送 */
    @Scheduled(cron = "0 */10 * * * ?", zone = "Asia/Shanghai")
    public void issueOverdueScan() {
        if (migrationMaintenance.isActive()) return;
        try {
            service.markOverdueIssues();
        } catch (Exception ex) {
            log.error("ops patrol issue overdue scan failed: {}", ex.getMessage(), ex);
        }
    }
}
