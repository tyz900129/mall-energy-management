# 模块：营运巡场管理

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/opspatrol/`
  - Controller：`OpsPatrolAdminController.java`、`OpsPatrolPublicController.java`
  - Service：`OpsPatrolService`、`OpsPatrolScheduler`、`OpsPatrolNotifyService`
  - 定时：00:05 生成任务、每分钟推送扫描、每 10 分钟进度/问题逾期扫描
- 前端：`src/views/OpsPatrol*.vue`（看板、任务、检查项、问题、排班、交接、报表等 8 页）、`OpsPatrolEntry.vue`（免登录 H5）
- 权限码：`ops.patrol.assign` / `config` / `dept` / `report` / `review`
- 表：`ops_patrol_dept`、`ops_patrol_dept_floor`、`ops_patrol_slot`、`ops_patrol_check_item`、`ops_patrol_item_slot`、`ops_patrol_duty`、`ops_patrol_task`、`ops_patrol_task_item`、`ops_patrol_issue`、`ops_patrol_issue_log`、`ops_patrol_handover`
- 迁移：V82–V85、V89
- 附件：复用 `attachment`，营运巡场业务类型

## 关键不变量

1. 一个处室 × 一个固定时段，同一时期只生成一条任务。
2. 任务生成时固化部门、时段、检查项快照；后续配置调整不影响历史任务。
3. 异常必须提交现场照片；一项检查可登记多家商户异常。
4. 企业微信/飞书推送必须包含**全部**现场照片，不能只推首张；推送消息同时包含分派部门链接和整改链接。
5. 整改提交后直接闭环，不再走复核。
6. 免登录 H5 必须记录填报人并持久化；登录态 180 天，每次使用自动顺延。
7. 部门群通过 `sys_department_webhook` 绑定，禁止在代码里硬编码部门与机器人关系。

## 已知坑

- 本模块与旧 `patrol`、`qrinspection` 存在业务重叠；新需求先确认归属，**不要新增第四套巡场流程**。
- 业务数据曾被清理、暂未正式启用；改动前先确认当前启用状态，不要假设有生产数据。
- 照片必须一次性随表单提交，分批上传会导致推送缺图。
- 移动端页面操作区要折叠，避免遮挡关键信息。
- 公开入口需同时处理 `SecurityConfig` 与 `RequestPermissionResolver` 两层。

## 测试

```bash
mvn -Dtest=OpsPatrol*Test test
```

## 最后核对

2026-11-13 / main@83e49c4
