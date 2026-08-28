# 第一阶段实施计划（对应文档第 49 节 Phase 1-3 + 第 58 节第一次任务）

> Phase 1-3 必须全部完成且通过 10 个验收场景（文档第 50 节）后才能进入 Phase 4。  
> 每个 Phase 末尾产出可演示的 war 包，部署到 `/vol2/.../webapps/energy.war`。

## Phase 1 — 项目骨架（对应文档第 49 节 Phase 1）
- [x] 基础设施就绪（本文档前述）
- [ ] Maven `pom.xml`：parent=spring-boot-starter-parent 3.3.x；deps：
  - spring-boot-starter-web / security / validation / data-redis / actuator
  - mybatis-plus-boot-starter 3.5.x
  - mysql-connector-j 8.x
  - flyway-mysql 10.x
  - jjwt 0.12.x
  - okhttp 4.x（PlatformAdapter）
  - caffeine（本地缓存）+ lock（分布式锁 by redis 或 MySQL row）
  - lombok / mapstruct
  - springdoc-openapi 2.x
- [ ] `application.yml` / `dev` / `prod`：profiles + Flyway 启用 + MyBatis-Plus 配置 + 日志路径
- [ ] `logback-spring.xml`：5 类 logger（application / platform-sync / workflow / webhook / security），禁止输出密码/token
- [ ] `EnergyApplication` + 基础包扫描
- [ ] Spring Security + JWT：`SecurityConfig`、`JwtTokenProvider`、`JwtAuthenticationFilter`、`UserDetailsServiceImpl`
- [ ] `ApiResponse<T>`、`BizException`、`GlobalExceptionHandler`
- [ ] Flyway `V1__init_system.sql`（含默认 admin 用户 BCrypt 哈希 + 7 个角色 + 初始菜单）
- [ ] 鉴权接口 `/auth/login` `/auth/me`
- [ ] Vue3 前端骨架：登录页、Dashboard、菜单、Layout
- [ ] **验收**：能登录、看到 Dashboard 占位、日志分类输出、Flyway 自动建表、admin BCrypt 登录通过

## Phase 2 — 源平台配置（对应文档第 49 节 Phase 2）
- [ ] Flyway `V2__platform_tables.sql`（platform_config / sync_batch / current / snapshot）
- [ ] `CredentialCryptoService`：AES-256-GCM，IV 12 字节；key 派生：`SHA-256(PLATFORM_CREDENTIAL_SECRET)` 截前 32 字节
- [ ] `PlatformAdapter` 接口 + `LeoEnergyPlatformAdapter` 第一期实现
  - `login()`：POST `https://app.leotechnology.cn/api/login`（具体端点见《水电费系统结构.md》，待读取后确认）
  - `fetchAccounts()` / `fetchDevices()` / `fetchAccountDeviceRelations()`：从已知 API 读取
- [ ] `PlatformConfigService`：CRUD + `testConnection()`（文档第 10.2）
- [ ] `PlatformConfigController` `/api/v1/platform/config` + `test-connection`
- [ ] 前端页面：系统管理 → 源平台配置（不显示密码字段，仅首次录入）
- [ ] **验收**：在 UI 录入 `base_url=https://app.leotechnology.cn`、`username=w3066`、`password=*****`（明文仅入参瞬时存在）、`space_id=4801`，保存后数据库 password_ciphertext 非空；点击"测试连接"返回成功；密码字段在所有 GET 响应中均不返回

## Phase 3 — 平台同步（对应文档第 49 节 Phase 3）
- [ ] `PlatformSyncService.runSync(triggerType, userId)`：分布式锁（MySQL `SELECT ... FOR UPDATE` 或 Redis SETNX）保证唯一实例
- [ ] 同步状态机：
  1. 创建 Batch（status=RUNNING）
  2. 完整拉取三类数据（分页迭代）
  3. 写暂存（中间表 staging_account/staging_device/staging_relation）
  4. 完整性校验（数量 > 0，关系左右表都覆盖）
  5. 事务：清 current → 写 current → 写 snapshot → COMMIT
  6. 治理：触发 Multi-Account 检测、Unauthorized Change 检测
  7. 触发待完成流程的 Reconciliation（Phase 6 才会有流程，此步暂空跑）
  8. Batch → SUCCESS
- [ ] 失败路径：保持上一批 current，Batch=FAILED + error_message
- [ ] `@Scheduled` 每日 02:00 触发（cron 可配）
- [ ] 同步日志页面（分页、按状态/触发方式/批次号过滤）
- [ ] **验收**：
  - 首次同步后 `platform_account_current` / `device_current` / `account_device_current` 三表数据量 > 0
  - 同步日志页面显示批次号、状态、耗时、各表数量
  - 手工同步：点击"立即同步"产生新 Batch，老 Batch 状态不变
  - 模拟中途失败：注入错误让抓取阶段异常 → Batch=FAILED → current 保持上一批不变

## Phase 3 验收（文档第 50 节可演示场景 1-3 之前置）
- [ ] 场景 1（部分）：在 UI 录入平台配置 → 测试连接 → 立即同步 → 同步日志可见
- [ ] 场景 10：注入失败 → Batch FAILED → Current 数据未被破坏

## Phase 3 后的 Gate（进入 Phase 4 前必须确认）
- [ ] 上述 3 个 Phase 全部通过冒烟
- [ ] 「源平台只读、平台数据为最终事实、流程只记录预期变化」三原则在代码与文档中均有体现
- [ ] `PLATFORM_CREDENTIAL_SECRET` 仅以环境变量形式存在，密码字段任何 API 响应均不返回
- [ ] 日志无平台密码/Token 痕迹
- [ ] Flyway 迁移在干净库上可一键初始化
- [ ] war 包部署到 `/energy` 上下文后页面可访问

## 不在本阶段范围（明确延后）
- Phase 4 铺位 / 商户代码解析
- Phase 5-9 流程、核验、Webhook、对账、异常
- Phase 10 能耗分析
- 多租户 / 多项目（当前 spaceId=4801 硬编码于 platform_config 单行）

## 风险与缓解
| 风险 | 缓解 |
|---|---|
| 源平台 API 字段未完全映射 | 保留 raw_json，DTO 不确定字段标注 `@Transient` 并列入 `toConfirm.md` |
| 1Panel Tomcat 旧 webapp 与新 `/energy` 冲突 | 上下文路径独立 `/energy`；新 war 部署不影响其他 webapp |
| AES-GCM key 派生跨 JVM 不一致 | 使用固定 SHA-256 派生 + IV 持久化，禁止随机重派生 |
| 高并发同步 | 分布式锁；同一账号 1 个 RUNNING batch；ADMIN 手工触发与定时互斥 |
| 同步过程中 current 表被读到中间态 | 同步走"先 staging 后 current 切换"的两阶段；事务内切换 |
