# 模块：系统管理 + 权限模型

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/`
  - `system/controller/`：用户、角色、部门、菜单、字典、Webhook、审计、迁移、系统配置
  - `system/service/`：`PermissionService`、`SysUserService`、`SysRoleService`、`SysDepartmentService` 等
  - `security/`：`RequestPermissionResolver`、`PermissionAuthorizationInterceptor`、JWT
  - `auth/`：登录、当前用户
- 前端：`SystemUser.vue`、`Organization.vue`、`BusinessDictionary.vue`、`WebhookConfig.vue`、`AuditLog.vue`、`SystemMigration.vue`；角色管理为 `SystemUser.vue` 内的抽屉
- 权限码：
  - `system.user.view|manage` / `system.user.permission.manage`
  - `system.dept.view|manage`
  - `system.dictionary.view|manage`
  - `system.webhook.view|manage`
  - `system.audit.view` / `system.migration.manage`
- 表：`sys_user`、`sys_role`、`sys_user_role`、`sys_permission`、`sys_role_permission`、`sys_user_permission`、`sys_menu`、`sys_role_menu`、`sys_department`、`sys_dept_permission_policy`、`dept_move_log`、`business_dictionary`、`webhook_config`、`sys_department_webhook`、`audit_log`、`system_config`
- 迁移：V1、V9、V12、V43、V86–V88、V91、V98–V102、V107–V109

## 关键不变量

1. **一人一岗**：每个用户恰好一个角色；岗位即角色，不存在 `sys_post` 系列表。
2. **有效权限 = 角色权限 ∪ 用户 ALLOW − 用户 DENY**；角色权限码是唯一功能鉴权来源。
3. **默认拒绝**：`/api/v1/**` 未在 `RequestPermissionResolver` 登记的路径返回 403，不是放行。
4. 不进行部门权限裁剪。`sys_dept_permission_policy` 仅保留历史行（V107 已全部 `enabled=0`），`DeptPermissionPolicy` 已废弃。
5. 内置角色 `is_system=1`：禁止删除、禁止修改角色编码；非内置角色改编码需全局唯一校验。
6. 组织移动禁止移入自身或子树；移动后重写子节点 `ancestors`（不含自身）与 `depth`，并记录审计。
7. 部门删除为软删除；内置部门、有下级、有关联用户的部门禁止删除；部门编码创建后锁定。
8. 自定义角色若仍有绑定用户，禁止删除；删除角色需级联清理 `sys_role_permission`。
9. 用户级例外可设失效时间、原因，并保留变更记录。

## 已知坑

- 新增接口忘记登记 Resolver = 线上 403；这是最常见的“本地能调、线上不行”原因。
- 前端菜单可见性由权限码硬编码决定，`sys_role_menu` 实际未驱动前端；改权限等于改菜单，要同步检查 `menu.ts`。
- **V107–V109 权限改造尚未合并 main**；涉及权限的改动先确认当前分支与生产迁移版本。
- 新增角色三件事缺一不可：迁移数据、`is_assignable`/可分配白名单、前端登录落地页 fallback。
- 角色权限变更后，用户可能需要重新登录才看到新菜单（以当前实现为准，改动前先验证）。
- 不要恢复 `acceptedRoles` / `bridgeRole` 之类的角色桥接，已一次性删除。

## 测试

```bash
mvn -Dtest=*Permission*Test test
mvn -Dtest=Sys*Test test
```

## 最后核对

2026-11-13 / feat/permission-code-model@9f40f22
