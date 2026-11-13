# 模块：设施巡检 + 任务中心（日历）

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/inspection/`
  - Controller：`InspectionController.java`、`InspectionEquipmentController.java`、`CalendarController.java`、`CalendarPublicController.java`
  - Service：`InspectionService`、`InspectionEquipmentService`、`CalendarPublicService`
  - 定时：`InspectionService` 内每 5 分钟生成/逾期/提醒
- 前端：`InspectionDashboard.vue`、`InspectionTasks.vue`、`InspectionIssues.vue`、`InspectionEquipment.vue`、`InspectionCategories.vue`、`InspectionManagement.vue`、`InspectionEquipmentScan.vue`、`CalendarView.vue`、`CalendarReceipt.vue`
- 权限码：
  - `inspection.dashboard.view` / `task.view` / `task.execute`
  - `inspection.plan.manage` / `template.manage` / `type.manage` / `category.manage`
  - `inspection.equipment.view|manage` / `issue.manage|review`
  - `calendar.view` / `calendar.manage` / `calendar.report`
- 表：`inspection_type`、`inspection_template`、`inspection_plan`、`inspection_plan_target`、`inspection_task`、`inspection_issue`、`inspection_reminder_log`、`inspection_equipment_category`、`inspection_equipment`、`inspection_category_template`、`calendar_generation_log`
- 迁移：V46、V47、V103、V104、V105
- 附件：复用 `attachment`，按巡检/日历业务类型区分

## 关键不变量

1. 任务生成时保存模板快照；后续改模板不影响历史任务与记录。
2. 计划归属支持个人 / 岗位 / 部门；归属快照在生成时固化，组织调整不影响历史任务。
3. 日历任务通过 `inspection_task.source_type` 区分，日历列表与巡检任务列表互斥，避免同一任务出现两次。
4. 日历公开回执：token 默认 7 天、仅可访问单条任务、返回内容脱敏、有频率限制。
5. 公开回执路径必须同时满足：`SecurityConfig` 按方法放行 + `RequestPermissionResolver` 标记公开。
6. 异常必须带现场照片；巡检记录保存设备快照。

## 已知坑

- **日历计划没有独立管理页**，复用“巡检配置”页的日历字段；新增计划能力前先确认入口归属。
- 日历一期不支持周视图、资源泳道、设备维度；不要擅自扩展为排班系统。
- 生产设备台账数据很少（曾仅 1 台），功能完整度不代表数据完整度。
- 逾期与提醒依赖定时任务；多实例部署会重复执行。
- 修复历史问题时不要新起“第三套巡检引擎”，优先复用本模块。

## 测试

```bash
mvn -Dtest=Inspection*Test test
mvn -Dtest=Calendar*Test test
```

## 最后核对

2026-11-13 / feat/permission-code-model@9f40f22
