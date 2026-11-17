-- 任务中心：删除「物业管理员」角色，并为角色引入数据范围（ALL / DEPT_SUB / SELF）
-- 1. 删除「物业管理员」角色的菜单、权限、用户绑定与角色本体
SET @property_role = (SELECT id FROM sys_role WHERE code = 'PROPERTY_MANAGER' LIMIT 1);
DELETE FROM sys_role_menu WHERE role_id = @property_role;
DELETE FROM sys_role_permission WHERE role_id = @property_role;
DELETE FROM sys_user_role WHERE role_id = @property_role;
DELETE FROM sys_role WHERE id = @property_role;

-- 2. 角色数据范围：ALL 全部、DEPT_SUB 本部门及子部门、SELF 仅本人
ALTER TABLE sys_role ADD COLUMN data_scope VARCHAR(16) NOT NULL DEFAULT 'SELF'
  COMMENT '数据范围：ALL 全部 / DEPT_SUB 本部门及子部门 / SELF 仅本人';

UPDATE sys_role SET data_scope = 'ALL'
 WHERE code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN');

-- 处长、课长、班长为“领导”，可见本部门及子部门任务
UPDATE sys_role SET data_scope = 'DEPT_SUB'
 WHERE code IN ('OPS_PATROL_MANAGER', 'APPLICANT', 'CS_MANAGER',
                'WY_SB_KZ', 'WY_AB_KZ', 'WY_BJ_KZ',
                'WY_SB_BZ', 'WY_AB_BZ', 'WY_BJ_BZ');

-- 3. 任务创建与生成日志仅管理员和领导可用：回收普通岗位，补齐领导
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.code IN ('calendar.manage', 'calendar.report')
   AND r.data_scope = 'SELF';

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.code IN ('calendar.manage', 'calendar.report')
 WHERE r.data_scope = 'DEPT_SUB'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission x
                    WHERE x.role_id = r.id AND x.permission_id = p.id);
