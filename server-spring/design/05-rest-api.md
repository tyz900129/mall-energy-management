# REST API 清单

> 全部 `application/json`，统一前缀 `/api/v1/`，统一响应 `{ code: 0, message: "success", data: {} }`。
> 鉴权：除 `/auth/login`、`/health` 外，均需 `Authorization: Bearer <jwt>`。  
> 错误码：`0=成功`、`1xxx=业务参数错`、`2xxx=鉴权`、`3xxx=平台数据`、`4xxx=同步`、`5xxx=系统`。

## 1. 认证 / 用户（Phase 1）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| POST | `/auth/login` | 公开 | 登录，返回 JWT |
| POST | `/auth/logout` | 已登录 | 注销 |
| GET  | `/auth/me` | 已登录 | 当前用户、角色、菜单 |
| GET  | `/users` | SYSTEM_ADMIN+ | 用户列表（分页） |
| POST | `/users` | SYSTEM_ADMIN+ | 创建用户 |
| PUT  | `/users/{id}` | SYSTEM_ADMIN+ | 修改 |
| DELETE | `/users/{id}` | SYSTEM_ADMIN+ | 软删 |
| POST | `/users/{id}/reset-password` | SYSTEM_ADMIN+ | 重置密码 |
| GET  | `/roles` | SYSTEM_ADMIN+ | 角色列表 |
| GET  | `/menus` | 已登录 | 当前用户菜单树 |

## 2. 源平台配置 + 同步（Phase 2-3，对应文档第 58 节）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/platform/config` | SYSTEM_ADMIN+ | 获取配置（不含明文密码） |
| PUT  | `/platform/config` | SYSTEM_ADMIN+ | 更新（password 入参时加密入库） |
| POST | `/platform/test-connection` | SYSTEM_ADMIN+ | 文档第 10.2 流程 |
| POST | `/platform/sync` | SYSTEM_ADMIN+ | 手工触发同步 |
| GET  | `/platform/sync-logs` | SYSTEM_ADMIN+ | 同步日志（分页） |
| GET  | `/platform/accounts` | 已登录 | 账户分页（按编号/名称/商户代码） |
| GET  | `/platform/accounts/{accountNumber}` | 已登录 | 账户详情 |
| GET  | `/platform/devices` | 已登录 | 设备分页 |
| GET  | `/platform/devices/{deviceId}` | 已登录 | 设备详情 |
| GET  | `/platform/accounts/{accountNumber}/devices` | 已登录 | 账户下设备 |
| GET  | `/platform/merchant/{merchantCode}` | 已登录 | 按商户代码查账户列表 |

## 3. 铺位（Phase 4）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET    | `/stalls` | 已登录 | 列表（分页、按楼层/业态筛选） |
| POST   | `/stalls` | SYSTEM_ADMIN+ | 新建 |
| GET    | `/stalls/{id}` | 已登录 | 详情 |
| PUT    | `/stalls/{id}` | SYSTEM_ADMIN+ | 更新 |
| DELETE | `/stalls/{id}` | SYSTEM_ADMIN+ | 软删 |
| GET    | `/stalls/{id}/devices` | 已登录 | 铺位设备 |
| POST   | `/stalls/{id}/devices` | SYSTEM_ADMIN+ | 绑定设备 |
| DELETE | `/stalls/{id}/devices/{deviceId}` | SYSTEM_ADMIN+ | 解绑 |

## 4. 商户 / 设备搜索
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/merchants/{code}` | 已登录 | 商户详情（含关联账户） |
| GET | `/devices/search` | 已登录 | 按 commAddress/serialNumber/name 模糊 |

## 5. 流程（Phase 6，对应文档第 19-23 节）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/workflows` | 已登录 | 列表（按类型/状态/商户/铺位） |
| POST | `/workflows` | APPLICANT+ | 创建草稿 |
| GET  | `/workflows/{id}` | 已登录 | 详情 |
| POST | `/workflows/{id}/submit` | APPLICANT+ | 提交（预检查 → DRAFT→SUBMITTED） |
| POST | `/workflows/{id}/cancel` | APPLICANT+ | 取消 |
| GET  | `/workflows/{id}/devices` | 已登录 | 流程设备 |
| GET  | `/workflows/{id}/expected-changes` | 已登录 | 预期变更 |
| GET  | `/workflows/{id}/timeline` | 已登录 | 状态变更时间轴 |
| POST | `/workflows/precheck/new-merchant` | APPLICANT+ | 全新商户预检 |
| POST | `/workflows/precheck/add-stall` | APPLICANT+ | 加铺位预检 |
| POST | `/workflows/precheck/offboarding` | APPLICANT+ | 撤柜预检 |

## 6. 现场核验（Phase 7，对应文档第 28-31 节）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/verification/tasks` | DEVICE_VERIFIER+ | 我的核验任务 |
| GET  | `/verification/tasks/{token}` | 公开（按 token） | 移动端核验详情 |
| POST | `/verification/tasks/{token}/start` | DEVICE_VERIFIER+ | 标记开始 |
| PUT  | `/verification/tasks/{token}/devices/{id}` | DEVICE_VERIFIER+ | 填写表号/序列号/读数/匹配结果 |
| POST | `/verification/tasks/{token}/devices/{id}/attachments` | DEVICE_VERIFIER+ | 上传照片（multipart） |
| POST | `/verification/tasks/{token}/complete` | DEVICE_VERIFIER+ | 提交完成 |

## 7. Webhook（Phase 8）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/webhooks` | SYSTEM_ADMIN+ | 配置列表 |
| POST | `/webhooks` | SYSTEM_ADMIN+ | 新建（url/secret 入参时加密入库） |
| PUT  | `/webhooks/{id}` | SYSTEM_ADMIN+ | 修改 |
| DELETE | `/webhooks/{id}` | SYSTEM_ADMIN+ | 删除 |
| GET  | `/webhooks/{id}/logs` | SYSTEM_ADMIN+ | 发送日志 |

## 8. 异常中心（Phase 9）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/exceptions` | 已登录 | 列表（按类型/严重度/状态） |
| GET  | `/exceptions/{id}` | 已登录 | 详情 |
| POST | `/exceptions/{id}/handle` | AUDITOR+ | 处理（resolve/ignore/ack） |

## 9. 能耗（Phase 10，预留）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/energy/daily` | 已登录 | 日维度 |
| GET | `/energy/monthly` | 已登录 | 月维度 |
| GET | `/energy/yearly` | 已登录 | 年维度 |
| GET | `/energy/trend` | 已登录 | 趋势 |
| GET | `/energy/ranking` | 已登录 | 排名 |
| GET | `/energy/anomalies` | 已登录 | 异常事件 |

## 10. 系统 / 仪表盘
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET  | `/health` | 公开 | 健康检查（容器探针） |
| GET  | `/dashboard/summary` | 已登录 | 首页：今日待核验、等待平台、异常、同步状态 |

## 11. 审计（只读）
| Method | Path | 角色 | 说明 |
|---|---|---|---|
| GET | `/audit-logs` | AUDITOR+ | 分页 + 过滤 |

