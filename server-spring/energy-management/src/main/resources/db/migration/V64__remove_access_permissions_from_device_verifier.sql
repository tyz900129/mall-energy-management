-- Device verifiers focus on field verification, construction and inspections.
-- Access-control privileges must be granted explicitly per user when required.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.code = 'DEVICE_VERIFIER'
  AND p.module_code = 'access';

UPDATE sys_role
SET description = '现场设备核验、工程巡检及设施巡检；不含门禁管理权限'
WHERE code = 'DEVICE_VERIFIER';
