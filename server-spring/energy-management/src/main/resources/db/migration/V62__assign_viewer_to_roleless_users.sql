-- Repair existing self-registered accounts created before VIEWER became the default role.
INSERT IGNORE INTO sys_user_role(user_id,role_id)
SELECT u.id,r.id
FROM sys_user u
JOIN sys_role r ON r.code='VIEWER'
LEFT JOIN sys_user_role ur ON ur.user_id=u.id
WHERE u.status=1 AND ur.user_id IS NULL;
