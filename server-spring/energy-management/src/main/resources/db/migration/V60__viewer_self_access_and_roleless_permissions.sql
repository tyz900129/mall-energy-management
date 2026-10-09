-- A user with explicit personal permissions may work without a role template.
-- Keep the VIEWER role limited to the user's own access-control information.
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
  'access.self.view',
  'access.self.manage',
  'visitor.view.own',
  'access.event.view.self'
)
WHERE r.code='VIEWER';
