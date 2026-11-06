-- 角色默认权限收口（按业务反馈调整）：
-- 1) 商管人员(APPLICANT)：去除检查项库编辑(ops.patrol.config)与处室与群配置(ops.patrol.dept)
-- 2) 营运巡场主管(OPS_PATROL_MANAGER)：补齐与商管人员一致的基础权限（保留其 ops.patrol 全部权限）
-- 3) 物业管理员(PROPERTY_MANAGER)：默认不给设备设施(facility.*)与能源管理(energy.*)
-- 4) 普通用户(VIEWER)：去除运营管理入口(operation.*)，避免菜单空壳
-- 5) 物业工作人员(DEVICE_VERIFIER)：仅保留现场核验与自己的巡检任务
--    （InspectionController.ownOnly: 有 task.execute 且非管理员时任务列表自动只看自己）
-- 6) 系统管理员(SYSTEM_ADMIN)：DB 授权对齐运行时短路口径（除 system.user.* 与 construction.delete）

-- 1) 商管人员：去除检查项库编辑与处室与群配置
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'APPLICANT' AND p.code IN ('ops.patrol.config', 'ops.patrol.dept');

-- 2) 营运巡场主管：补齐与商管人员一致的基础权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'OPS_PATROL_MANAGER'
  AND p.code IN (
    'access.self.view', 'access.self.manage',
    'merchant.menu', 'merchant.query', 'merchant.view', 'stall.view',
    'workflow.view', 'workflow.create',
    'operation.dashboard.view', 'operation.merchant.view', 'operation.merchant.manage',
    'operation.clearance.view', 'operation.clearance.template.view',
    'operation.accountability.view', 'operation.property.view'
  );

-- 3) 物业管理员：去除设备设施与能源管理
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'PROPERTY_MANAGER'
  AND (p.code LIKE 'facility.%' OR p.code LIKE 'energy.%');

-- 4) 普通用户：去除运营管理
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'VIEWER' AND p.code LIKE 'operation.%';

-- 5) 物业工作人员：仅保留现场核验与自己的巡检任务
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'DEVICE_VERIFIER'
  AND p.code NOT IN ('verification.view', 'verification.execute',
                     'inspection.task.view', 'inspection.task.execute');

-- 6) 系统管理员：对齐运行时短路口径（全部启用权限，除用户管理与施工删除）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.enabled = 1
  AND p.code NOT LIKE 'system.user.%'
  AND p.code <> 'construction.delete';
