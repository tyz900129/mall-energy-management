-- V111：重新补齐"菜单入口权限 → 页面内数据接口权限"的配套关系
-- 背景：V88 只对迁移执行时已拥有入口码的角色补齐了数据码，
--       之后新分配入口码的角色会出现"菜单可见但接口 403"的问题。
-- 本迁移复用 V88 的全部依赖规则，INSERT IGNORE 幂等，不会重复授权。

-- 1) 物业工作人员：商户施工申请需要 construction.create / construction.view / stall.view
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'DEVICE_VERIFIER'
  AND p.code IN ('construction.create', 'construction.view', 'stall.view');

-- 2) 商户账户管理入口(merchant.menu)需要 merchant.view
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.code = 'merchant.menu'
CROSS JOIN sys_permission p2
WHERE p2.code = 'merchant.view';

-- 3) 铺位管理入口(stall.menu)需要 stall.view + 楼层/业务类型字典
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.code = 'stall.menu'
CROSS JOIN sys_permission p2
WHERE p2.code IN ('stall.view', 'system.dictionary.view', 'workflow.view');

-- 4) 计量设备绑定入口(metering.menu)需要 metering.view + 铺位下拉
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.code = 'metering.menu'
CROSS JOIN sys_permission p2
WHERE p2.code IN ('metering.view', 'stall.view');

-- 5) 水电费变更入口(workflow.create)需要铺位下拉 + 业务类型字典
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.code = 'workflow.create'
CROSS JOIN sys_permission p2
WHERE p2.code IN ('stall.view', 'workflow.view');

-- 6) 日清/清场模板页需要业务类型字典
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id
  AND p1.code IN ('operation.clearance.template.view', 'operation.property.template.view')
CROSS JOIN sys_permission p2
WHERE p2.code = 'workflow.view';

-- 7) 设施设备拓扑页筛选需要铺位 + 字典
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p2.id
FROM sys_role_permission rp
JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.code LIKE 'facility.%.view'
CROSS JOIN sys_permission p2
WHERE p2.code IN ('stall.view', 'system.dictionary.view');
