# AGENTS.md — 万悦城后勤管理系统

> 本文件是 AI 协作的唯一规则源。任何 AI 在修改本仓库前，先读完本文件，再按 §6 路由到对应模块卡。
> 优先级：**用户本轮明确指令 > 代码与数据库的客观事实 > 本文件 > docs/ai/** > README.md / 旧文档**。
> 核对锚点：2026-11-13 ｜ main@83e49c4 ｜ Flyway 最高 V109 ｜ 当前另有分支 feat/permission-code-model

---

## 1. 项目速览

商场后勤一体化平台，覆盖：能源管理、水电费变更/商户施工流程、设施巡检、二维码巡检、
营运巡场、门禁访客、客诉服务、任务中心（日历）、系统与权限管理。

| 项 | 内容 |
|---|---|
| 后端 | `server-spring/energy-management`，Java 17 / Spring Boot 3.3.5 / MyBatis-Plus / MySQL 8 / Flyway |
| 前端 | `server-spring/energy-admin`，Vue 3 / TypeScript / Vite / Element Plus / Pinia |
| 迁移 | `server-spring/energy-management/src/main/resources/db/migration`，当前最高 V109 |
| 部署 | Docker Compose；220 生产、225 开发（细节不在本文件） |
| 代码规模 | 约 385 个 Java 文件、57 个 Controller、90 个前端页面 |

后端一级包：`access complaint dashboard facility inspection merchant notification operation
opspatrol patrol platform power qrinspection security stall stalldevice system usage verification workflow`。

---

## 2. 常用命令

```bash
# 后端（工作目录：server-spring/energy-management）
mvn test
mvn -Dtest=Complaint*Test test
mvn clean package -DskipTests
mvn spring-boot:run

# 前端（工作目录：server-spring/energy-admin）
npm install
npm run dev     # http://localhost:5173
npm run build   # vue-tsc --noEmit && vite build
```

- Flyway 随应用启动自动执行；**已应用的迁移脚本禁止修改**，只能新增。
- 生产构建与部署脚本不在本仓库规范内，禁止由 AI 自行执行。

---

## 3. 硬约束（违反即返工）

1. **禁止自动 `git commit` / `git push`**。改动留在工作区，等用户确认。
2. **禁止部署到 220 生产、禁止修改线上数据库与业务数据**。需要上线时只输出步骤，不执行。
3. **新增接口必须登记权限**：在 `security/RequestPermissionResolver` 增加路径 → 权限码规则。
   未登记的 `/api/v1/**` 默认返回 403，不是放行。
4. **功能鉴权一律用权限码**（`@PreAuthorize("hasAuthority('x')")` 或路径映射）。
   禁止硬编码角色名做功能判断；仅数据范围/身份场景可判角色，且必须写注释说明原因。
5. **不恢复部门权限裁剪**。V107 已停用全部部门策略；有效权限 = 角色权限 ∪ 用户 ALLOW − 用户 DENY。
6. **删除操作**：必须二次确认 + 对应 `*.delete` 权限码（高危删除仅 SUPER_ADMIN）+ 级联清理数据与文件。
7. **一人一岗、岗位即角色**。禁止恢复 `sys_post` / `sys_user_post` 系列表，禁止一人多角色叠加权限。
8. **数据库变更只走 Flyway**，脚本必须幂等（`CREATE TABLE IF NOT EXISTS`、`ON DUPLICATE KEY`），编号顺延。
   MySQL DDL 隐式提交，失败后需清理 `flyway_schema_history` 中 `success=0` 的记录。
9. **新增角色三件事缺一不可**：迁移插入角色/权限/菜单；`is_assignable` 或可分配白名单；前端登录落地页 fallback。
10. **免登录 H5 公开接口必须两层放行**：`SecurityConfig` 按方法精确 permitAll + `RequestPermissionResolver` 返回 `PUBLIC`。
11. **不新增巡检/巡场引擎**。新需求优先复用 `inspection`（含日历）或 `qrinspection`。
12. **文档与代码不得包含**账号、密码、密钥、证书、生产 IP。生产环境只写“220 生产 / 225 开发”。

---

## 4. 编码风格

### 后端

- Controller 只做参数接收、权限注解、调用 Service；业务逻辑放 Service。
- 统一返回 `ApiResponse<T>`；业务失败抛 `BizException`，不要返回裸 Map 表达错误。
- 写操作加 `@Transactional`；幂等优先用数据库条件更新，不要只靠前端 nonce。
- 新增接口使用 DTO，不要再用 `Map<String,Object>` 传参（历史代码除外）。
- 注释、日志、文档使用中文；只为“为什么”写注释，不为“是什么”写注释。

### 前端

- 页面使用 `<script setup lang="ts">`；接口调用统一走 `src/api/*.ts`，不要在页面里直接写 axios。
- 菜单与按钮可见性由权限码驱动（`src/router/menu.ts`、权限 store）；不要新增角色名判断。
- 移动端适配统一用 `useIsMobile`（768px），样式集中在 `src/styles/ops-patrol.css` 等既有文件。
- axios 拦截器已统一解包，业务代码不要再重复 `.data`。
- 删除操作必须有二次确认弹窗。

### 结构参考（先看结构，再动手；不要整仓通读）

- Controller：`complaint/controller/ComplaintController.java`
- Service：`inspection/InspectionService.java`
- 权限映射：`security/RequestPermissionResolver.java`
- 前端列表页：`src/views/ComplaintList.vue`
- 前端菜单：`src/router/menu.ts`

---

## 5. 权限模型（当前有效版本）

```
有效权限 = 角色权限(sys_role_permission) ∪ 用户 ALLOW(sys_user_permission) − 用户 DENY
```

- JWT 只携带用户 ID 与角色；权限码每次请求实时查库，不缓存在 token。
- 读取路径（GET）通常映射 `*.view`，写操作映射 `*.manage/create/update/delete/execute`。
- 公开路径必须在 `RequestPermissionResolver` 显式声明，并同步 `SecurityConfig`。
- V107–V109 属于 `feat/permission-code-model` 分支，尚未合并 main；改动前先确认当前分支与生产迁移版本。
- `DeptPermissionPolicy` 已标记废弃，不得重新调用。

---

## 6. 模块路由表

| 任务涉及 | 先读 |
|---|---|
| 客诉 / 服务管理 | `docs/ai/modules/complaint.md` |
| 能源 / 用量 / 账单 / 欠费 | `docs/ai/modules/energy.md` |
| 设施巡检 / 设备台账 / 任务中心日历 | `docs/ai/modules/inspection.md` |
| 门禁 / 访客 / 通行记录 | `docs/ai/modules/access.md` |
| 营运巡场 | `docs/ai/modules/ops-patrol.md` |
| 用户 / 角色 / 组织 / 字典 / Webhook / 权限 | `docs/ai/modules/system-permission.md` |
| 新增接口 | 本文件 §3 + §5 + `RequestPermissionResolver` + 参考 Controller |
| 新增页面 | `src/router/menu.ts` + 参考页面 + 对应模块卡 |
| 新增数据库迁移 | 本文件 §3 第 8 条 + 最近 3 个迁移脚本 |
| 新模块 | 按 `docs/ai/modules/_template.md` 新建模块卡，并登记到本表 |

其余模块（水电费流程、施工流程、运营管理、二维码巡检、旧巡场、平台同步、数据迁移）
暂无模块卡，先按 `docs/ai/README.md` 的“新建模块卡”流程补卡，再做修改。

---

## 7. 文档维护规则

| 变更 | 必须同步 |
|---|---|
| 新增/修改接口 | 对应模块卡 + `RequestPermissionResolver`；重新跑 `python scripts/ai/generate-context.py` |
| 新增表/迁移 | 对应模块卡的“表/迁移”段 + 本文件锚点版本 + 重新生成 |
| 新增权限码 | 对应模块卡 + 迁移脚本 + 角色授权 + 重新生成 |
| 新增模块 | 新建模块卡 + 本文件 §6 路由表 + `docs/ai/README.md` 索引 |
| 架构决策 | `docs/ai/decisions/ADR-xxx.md` |

- `docs/ai/generated/` 是脚本产物，**禁止手改**；事实以生成结果为准。
- 每次改动后执行：`python scripts/ai/generate-context.py` → `python scripts/ai/check-context.py`，校验必须 0 错误。
- 提交前对照 `docs/ai/CHECKLIST.md` 逐项确认。
- 每次修改模块后，更新模块卡末尾的“最后核对：日期 / commit”。
- 文档与代码冲突时，以代码为准，并立即修正文档。
- 不确定的事实不要写进文档；标注“待核实”，不要猜。
