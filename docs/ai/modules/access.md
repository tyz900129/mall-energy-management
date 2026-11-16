# 模块：门禁与访客

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/access/`
  - Controller：`AccessControlController.java`、`PublicVisitorController.java`
  - Service：`AccessControlService`、`HikvisionIsapiClient`、`AccessPersonScanService`、`AccessCardScanService`、`AccessEventSyncService`
  - 定时：人员比对 03:00、卡片比对 03:30、通行记录同步每 30 分钟
- 前端：`AccessControl.vue`（7 个区块）、`VisitorApply.vue`；`src/api/accessControl.ts`、`accessPublic.ts`
- 权限码：
  - `access.dashboard.view` / `access.self.view|manage`
  - `access.device.view|manage` / `access.person.view|manage|revoke|review|sensitive.view`
  - `access.event.view.self|view.all|sync` / `access.sync.import|write`
  - `access.test-data.delete`
  - `visitor.create` / `visitor.approve` / `visitor.view.own` / `visitor.view.all`
- 表：`access_point`、`access_device`、`access_person`、`access_person_device`、`access_event`、`access_person_review`、`visitor_appointment`、`access_person_scan_job`、`access_person_snapshot`、`access_card_scan_job`、`access_card_snapshot`、`access_person_document`、`access_application`、`access_device_sync_task`、`access_device_capability`、`access_person_retention_snapshot`、`access_person_claim_log`
- 迁移：V50–V64

## 关键不变量

1. 对海康设备的对接走 **ISAPI（HTTP）**，不是 HCNetSDK；`HikvisionIsapiClient` 是唯一出口。
2. 人脸照片必须先过质量检测，再进入同步/比对流程。
3. 永久删除人员/访客/测试数据只允许 `SUPER_ADMIN`，并由 `access.test-data.delete` 权限码和控制器双重拦截。
4. 缩略图必须落盘缓存，禁止每次请求实时解码原图。
5. 敏感字段（证件号、手机号、照片）需 `access.person.sensitive.view`。
6. “我的门禁/我的通行记录”只能看本人数据（`access.self.*` / `access.event.view.self`）。
7. 设备写操作受“写模式”开关约束，默认不写生产设备。

## 已知坑

- README 第七节仍写“海康 SDK 接入未交付”，**已过期**，以本卡为准。
- 前端“我的门禁”曾无条件调用缩略图接口，导致无 `access.event.view.self` 的用户 403；新增页面先判权限。
- 抓拍缩略图无缓存会打满 CPU；并发加载要做去重。
- 访客审批与设备下发是异步/轮询，失败重试状态需要查同步任务，不要只看访客单状态。
- 修改删除接口前先确认控制器内的硬拦截，不要只改权限码。

## 测试

```bash
mvn -Dtest=Access*Test test
```

## 最后核对

2026-11-13 / main@83e49c4
