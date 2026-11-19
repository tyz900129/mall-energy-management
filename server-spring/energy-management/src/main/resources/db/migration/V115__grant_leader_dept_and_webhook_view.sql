-- 领导角色补授任务日历部门树（system.dept.view）与巡检通知配置（system.webhook.view）查看权限。
-- 背景：任务日历页面无条件加载部门树，巡检计划需要读取 Webhook 配置；
--       原权限仅授予超级管理员、系统管理员，领导角色打开页面会提示缺少权限。

-- 1. 领导角色：任务日历部门筛选所需。
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.code = 'system.dept.view'
 WHERE r.code IN ('OPS_PATROL_MANAGER','APPLICANT','CS_MANAGER',
                  'WY_SB_KZ','WY_AB_KZ','WY_BJ_KZ',
                  'WY_SB_BZ','WY_AB_BZ','WY_BJ_BZ')
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission x
                    WHERE x.role_id = r.id AND x.permission_id = p.id);

-- 2. 设备课长、设备班长：巡检计划中的 Webhook 通知配置。
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.code = 'system.webhook.view'
 WHERE r.code IN ('WY_SB_KZ','WY_SB_BZ')
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission x
                    WHERE x.role_id = r.id AND x.permission_id = p.id);
