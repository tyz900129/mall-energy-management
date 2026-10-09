-- VIEWER is the minimal self-service role. Extra access must be granted per user.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id=rp.role_id
JOIN sys_permission p ON p.id=rp.permission_id
WHERE r.code='VIEWER'
  AND p.code NOT IN ('access.self.view','access.self.manage');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('access.self.view','access.self.manage')
WHERE r.code='VIEWER';
