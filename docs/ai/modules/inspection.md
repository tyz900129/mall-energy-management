# 模块：设施巡检 + 任务中心（日历）

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/inspection/`
  - Controller：`InspectionController.java`、`InspectionEquipmentController.java`、`CalendarController.java`、`CalendarPublicController.java`
  - Service：`InspectionService`、`InspectionEquipmentService`、`CalendarPublicService`
  - 定时：`InspectionService` 内每 5 分钟生成/逾期/提醒
- 前端：`InspectionDashboard.vue`、`InspectionTasks.vue`、`InspectionIssues.vue`、`InspectionEquipment.vue`、`InspectionCategories.vue`、`InspectionManagement.vue`、`InspectionEquipmentScan.vue`、`CalendarView.vue`、`CalendarPlans.vue`、`CalendarReceipt.vue`
- 权限码：
  - `inspection.dashboard.view` / `task.view` / `task.execute`
  - `inspection.plan.manage` / `template.manage` / `type.manage` / `category.manage`
  - `inspection.equipment.view|manage` / `issue.manage|review`
  - `calendar.view` / `calendar.manage` / `calendar.report`
- 表：`inspection_type`、`inspection_template`、`inspection_plan`、`inspection_plan_target`、`inspection_task`、`inspection_task_log`、`inspection_issue`、`inspection_reminder_log`、`inspection_equipment_category`、`inspection_equipment`、`inspection_category_template`、`calendar_generation_log`
- 迁移：V46、V47、V103、V104、V105、V112
- 日历计划接口（`/api/v1/calendar`，GET 需 `calendar.view`，写操作需 `calendar.manage`）：
  - `GET /plans`（keyword/status/category）、`GET /plans/{id}`（含最近 7 天生成日志）
  - `POST /plans`、`PUT /plans/{id}`、`POST /plans/{id}/toggle?enabled=`
  - `POST /plans/{id}/generate?date=`
- 任务操作日志：`GET /api/v1/inspections/tasks/{id}` 返回 `logs`（认领 / 改派 / 网页回执 / H5 回执）
- 附件：复用 `attachment`，按巡检/日历业务类型区分

## 关键不变量

1. 任务生成时保存模板快照；后续改模板不影响历史任务与记录。
2. 计划归属支持个人 / 岗位 / 部门；归属快照在生成时固化，组织调整不影响历史任务。
3. 日历任务通过 `inspection_task.source_type` 区分，日历列表与巡检任务列表互斥，避免同一任务出现两次。
4. 日历公开回执：token 默认 7 天、仅可访问单条任务、返回内容脱敏、有频率限制。
5. 公开回执路径必须同时满足：`SecurityConfig` 按方法放行 + `RequestPermissionResolver` 标记公开。
6. 异常必须带现场照片；巡检记录保存设备快照。
7. 日历计划（`inspection_type.code='CALENDAR'`）不使用设备目标：整体下发时写入一条 `target_type='CALENDAR'` 占位目标，`PER_CHILD` 由生成器按子部门展开。
8. 日历计划保存/启用会立即生成当天任务；重复生成由 `calendar_generation_log.period_key` 幂等去重。
9. 公开回执提交前服务端按 `fields_json.required` 做必填兜底校验；PHOTO 字段由 `require_photo` 单独约束。

## 已知坑

- **日历计划与巡检计划共用 `inspection_plan` 表**：日历计划必须走 `/calendar/plans` 专用接口，不要用巡检保存接口（后者要求设备目标）。
- 日历周视图按责任对象（`ownerName`）分泳道；缺少归属字段的数据会落到“未指派”。
- 生产设备台账数据很少（曾仅 1 台），功能完整度不代表数据完整度。
- 逾期与提醒依赖定时任务；多实例部署会重复执行。
- 修复历史问题时不要新起“第三套巡检引擎”，优先复用本模块。

## 测试

```bash
mvn -Dtest=Inspection*Test test
mvn -Dtest=Calendar*Test test
```

## 最后核对

2026-11-16 / main（日历计划管理页、周视图资源泳道、任务操作日志）
