-- 角色体系固定为五类；旧角色下的用户及只读菜单权限平滑归并到 VIEWER。
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT ur.user_id, viewer.id
FROM sys_user_role ur
JOIN sys_role old_role ON old_role.id = ur.role_id
JOIN sys_role viewer ON viewer.code = 'VIEWER'
WHERE old_role.code IN ('PLATFORM_OPERATOR', 'AUDITOR');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT viewer.id, rm.menu_id
FROM sys_role_menu rm
JOIN sys_role old_role ON old_role.id = rm.role_id
JOIN sys_role viewer ON viewer.code = 'VIEWER'
WHERE old_role.code IN ('PLATFORM_OPERATOR', 'AUDITOR');

DELETE ur FROM sys_user_role ur
JOIN sys_role old_role ON old_role.id = ur.role_id
WHERE old_role.code IN ('PLATFORM_OPERATOR', 'AUDITOR');

DELETE rm FROM sys_role_menu rm
JOIN sys_role old_role ON old_role.id = rm.role_id
WHERE old_role.code IN ('PLATFORM_OPERATOR', 'AUDITOR');

DELETE FROM sys_role WHERE code IN ('PLATFORM_OPERATOR', 'AUDITOR');

UPDATE sys_role SET name = '只读用户', description = '只读访问系统业务数据' WHERE code = 'VIEWER';
