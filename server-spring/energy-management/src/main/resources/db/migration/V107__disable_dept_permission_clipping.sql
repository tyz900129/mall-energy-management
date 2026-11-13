-- V107：取消部门权限裁剪（权限模型改为「角色/岗位授权为唯一权威来源」）
--
-- 背景：
--   部门裁剪（sys_dept_permission_policy）是早期没有「按岗位配权限」能力时的临时收紧手段。
--   S4 起岗位即角色（sys_role），角色授权可以直接表达岗位权限，不再需要按部门二次白名单过滤。
--
-- 本迁移只停用策略行（enabled=0），保留历史记录便于追溯与回滚；不删除表、不删除行。
-- 代码侧同步：PermissionService 不再调用 DeptPermissionPolicy，DeptPermissionPolicy 标记 @Deprecated。
--
-- 回滚：见 docs 或随附回滚脚本
--   UPDATE sys_dept_permission_policy SET enabled=1 WHERE remark LIKE '%V107 停用%';

UPDATE sys_dept_permission_policy
SET enabled = 0,
    remark = LEFT(CONCAT(IFNULL(remark, ''), ' | 2026-11-12 V107 停用：角色/岗位授权为唯一来源，取消部门裁剪'), 255)
WHERE enabled = 1;
