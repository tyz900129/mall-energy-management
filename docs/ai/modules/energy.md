# 模块：能源管理（用量 / 账单 / 欠费 / 异动 / 余额预警 / 设备离线 / 账户异常）

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/usage/`
  - Controller：`controller/EnergyUsageController.java`、`ArrearsNotificationController.java`、`AccountAnomalyMonitorController.java`、`BalanceWarningController.java`、`DeviceAlertController.java`、`ExceptionAccountController.java`
  - Service：`EnergyUsageService`、`UtilityBillService`、`AnalysisDimensionService`、`ArrearsNotificationService`、`AccountAnomalyMonitorService`、`BalanceWarningService`、`DeviceAlertService`、`ExceptionAccountService`
  - 定时：`EnergyUsageSyncScheduler`、`ArrearsNotificationScheduler`、`AccountAnomalyMonitorScheduler`、`BalanceWarningScheduler`、`DeviceAlertScheduler`、`ExceptionAccountScheduler`
- 前端：`UsageDashboard.vue`、`UsageAnalysis.vue`、`UsageSync.vue`、`UtilityBill.vue`、`ArrearsNotification.vue`、`AccountAnomalyMonitor.vue`、`BalanceWarning.vue`、`DeviceGroup.vue`、`DeviceOfflineAlert.vue`、`ExceptionAccountNotify.vue`；`src/api/energy.ts`、`arrears.ts`、`accountAnomaly.ts`、`balanceWarning.ts`、`deviceAlert.ts`、`exceptionAccount.ts`
- 权限码前缀：`energy.*`
  - `dashboard.view` / `analysis.view` / `dimension.view|manage`
  - `sync.view|execute` / `bill.view|manage`
  - `arrears.view|manage` / `anomaly.view|manage` / `balance-warning.view|manage`
  - `offline.view|manage`（设备离线告警） / `exception.view|manage`（账户异常通知）
- 表：`energy_usage_daily`、`energy_usage_monthly`、`energy_usage_monthly_dimension`、`energy_usage_sync_batch`、`energy_usage_sync_exclusion`、`device_group`、`device_group_assignment`、`device_energy_metric`、`analysis_dimension`、`analysis_dimension_value`、`device_dimension_assignment`、`energy_utility_bill`、`energy_arrears_account`、`energy_arrears_sync_run`、`energy_account_daily_bill`、`energy_account_anomaly_rule|result|run`、`energy_balance_warning_rule|result|run`、`energy_device_monitor_setting`、`energy_device_offline_exclude`、`energy_device_alert_event`、`energy_device_alert_run`、`energy_exception_monitor_setting`、`energy_exception_account`、`energy_exception_sync_run`
- 迁移：V13、V33、V34、V35、V37、V42、V65、V67、V68、V110

## 关键不变量

1. 月度用量是账单与分析的权威口径；日数据只保留近 3 个月。
2. 每台计量设备只允许关联一个“启用”计费账户（数据库唯一约束）。
3. 账单按账户生成，金额与用量、单价、倍率必须可追溯。
4. 同步批次必须留痕（`energy_usage_sync_batch`），失败可定位到批次与设备。
5. 欠费、异动、余额预警都基于“账户日账单”，规则可配置，不在代码里写死阈值。
6. 设备离线按网关 → 端口 → 设备三级判定：上级离线时抑制下级告警；同一范围仅保留一条未恢复事件（`open_scope_key` 唯一约束），恢复后置空。
7. 设备离线与账户异常均按“连续次数/已通知标记”去重，并在静默时段内只记录不发 Webhook。

## 已知坑

- 产品类型到测量点的映射是自动选择，新增产品类型时先确认映射，不要手工填测量点。
- 日数据补采可能造成重复，写入必须按业务键幂等。
- 同步异常优先查平台连接配置与批次日志，不要先改业务代码。
- 定时任务按单实例设计；多实例部署会重复执行，需要分布式锁或改造。
- 月同步在每月 1 日 03:00，跨月补跑要显式指定月份。
- 离线巡检的间隔配置由 5 分钟 tick + 最近一次成功运行时间判断，不要改成 cron，否则重启后补偿逻辑失效。
- Webhook 事件名为 `ENERGY_DEVICE_OFFLINE`（设备离线）、`ENERGY_DEVICE_ONLINE`（恢复在线）、`ENERGY_ACCOUNT_EXCEPTION`（账户异常）；未在 `webhook_config.event_types` 勾选则只落库不推送。恢复事件不要复用 OFFLINE 前缀，Webhook 匹配是子串匹配。

## 测试

```bash
mvn -Dtest=Energy*Test test
mvn -Dtest=*Usage*Test test
```

## 最后核对

2026-11-14 / feat/permission-code-model（V110）
