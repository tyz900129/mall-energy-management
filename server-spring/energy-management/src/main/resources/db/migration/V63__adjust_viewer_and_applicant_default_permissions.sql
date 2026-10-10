-- Keep default roles deliberately small. Additional access can be granted per user.

-- VIEWER: only the self-service access-control page (view, claim and maintain own profile).
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.code = 'VIEWER';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('access.self.view', 'access.self.manage')
WHERE r.code = 'VIEWER';

-- APPLICANT: utility-change workflow, merchant query, own access profile,
-- and read-only merchant-account management. stall.view is a supporting
-- permission required by the utility-change form's stall selector; it does
-- not expose the stall-management menu because stall.menu is not assigned.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.code = 'APPLICANT';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
  'workflow.view',
  'workflow.create',
  'merchant.query',
  'merchant.menu',
  'merchant.view',
  'stall.view',
  'access.self.view',
  'access.self.manage'
)
WHERE r.code = 'APPLICANT';

UPDATE sys_role
SET description = '仅可查看和维护本人的门禁资料'
WHERE code = 'VIEWER';

UPDATE sys_role
SET description = '水电费变更、商户账户查询、本人门禁及商户账户只读查看'
WHERE code = 'APPLICANT';
