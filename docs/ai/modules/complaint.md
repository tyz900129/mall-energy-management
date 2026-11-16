# 模块：客诉管理（服务管理）

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/complaint/`
  - Controller：`controller/ComplaintController.java`
  - Service：`service/ComplaintService.java`、`ComplaintExcelService.java`、`ComplaintStatsService.java`、`ComplaintStaffService.java`、`ComplaintOptionService.java`
- 前端：`src/views/ComplaintList.vue`、`ComplaintFormDrawer.vue`、`ComplaintDashboard.vue`、`ComplaintStaff.vue`、`ComplaintQuickForm.vue`；`src/api/complaint.ts`
- 权限码前缀：`complaint.*`
  - `view` / `create` / `update` / `close` / `delete`
  - `import` / `import.force` / `export` / `stats.view`
  - `staff.manage` / `brand.manage`
- 表：`cs_complaint`、`cs_complaint_participant`、`cs_complaint_log`、`cs_complaint_dict`、`cs_brand`、`cs_staff`
- 迁移：V94、V95、V96、V97、V106
- 附件：复用 `attachment`，`business_type='COMPLAINT'`

## 关键不变量

1. 状态流：待处理 → 处理中 → 已解决 → 已关闭；已关闭是终态。
2. 已关闭单据默认禁止修改；仅同时具备 `complaint.import.force` 且请求 `allowClosed=true` 时，允许导入更新费用三列。
3. 导入更新只允许 `company_cost`、`merchant_cost`、`no_cost`；绝不修改 `status`、`closed_at`、`handle_days`、参与人、品牌、`created_at`。
4. `no_cost=是` 时费用强制归零，并在 `cs_complaint_log` 记录变更摘要。
5. 经办人/责任商管来自 `cs_staff` 人员名单并保存姓名快照，不依赖系统账号；被引用人员只能停用，不能物理删除。
6. 统计周期：季度 12-2 / 3-5 / 6-8 / 9-11；半年 3-8 / 9-2。
7. 数据范围按角色区分（客服/主管/系管/超管），属于身份场景，不是功能鉴权。

## 已知坑

- 新增导入接口默认 `dryRun=true`，逐行独立事务，单行失败不影响其他行。
- 客诉编号已存在则整行失败、不覆盖；编号留空才自动生成，不自动去重。
- 品牌/业态优先取商户主数据；无铺位主体（超市、自营主力等）走 `cs_brand`。
- 列表不展示“商户”列，商户仅用于品牌/铺位自动带出。
- 移动端快速登记页走免登录入口，权限与 token 校验在公开控制器内，不要套用后台权限。

## 测试

```bash
mvn -Dtest=Complaint*Test test
```

## 最后核对

2026-11-13 / main@83e49c4
