-- V109：修正 V108 —— 两个删除类权限码只授予 SUPER_ADMIN
--
-- 背景：V108 中「SYSTEM_ADMIN 全量授权」的 CROSS JOIN 先于按码授予执行，
-- 使 patrol.delete / ops.patrol.delete 被顺带授予 SYSTEM_ADMIN，超出授权口径。
-- 本脚本收回非 SUPER_ADMIN 的这两个删除码（幂等，可重复执行）。
--
-- 回滚：如确需恢复，重新执行 V108 中 SYSTEM_ADMIN 的 CROSS JOIN 段即可，
--       或从 /home/admin/energy-deploy/backups/pre-perm-20261112-pre-perm-v108.sql.gz 恢复。

DELETE rp FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
JOIN sys_role r ON r.id = rp.role_id
WHERE p.code IN ('patrol.delete', 'ops.patrol.delete')
  AND r.code <> 'SUPER_ADMIN';
