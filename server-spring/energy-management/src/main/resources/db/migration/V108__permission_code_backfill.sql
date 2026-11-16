-- V108：权限码补齐 + 角色授权显式化（承接 V107 取消部门裁剪）
--
-- 目标：
--   1. 新增 9 个权限码，按「改造前靠角色名通过的角色」逐一授予，做到接口级能力不退化。
--   2. 把超级管理员/系统管理员原先的代码内特判（全量权限 / 全量减 system.user.* 与 construction.delete）
--      改为显式角色授权，使「有效权限 = 角色权限 ∪ 用户 ALLOW − 用户 DENY」严格成立。
--   3. 两个删除码只授予 SUPER_ADMIN。
--
-- 回滚：G:\hik\权限码化_V108_回滚_20261112.sql

-- ---------------------------------------------------------------- 新增权限码
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('complaint.view.all', '客诉-查看全部单据', 'complaint', '服务管理', 'VIEW', 2020,
        '不受「仅本人责任商管」数据范围限制，可查看全部客诉单据'),
       ('operation.dashboard.view.all', '运营大屏-查看全部处室', 'operation', '运营管理', 'VIEW', 2021,
        '运营数据大屏按全部处室统计（原 DEVICE_VERIFIER 等角色的全局视角）'),
       ('operation.clearance.view.all', '商户日清-查看全部数据', 'operation', '运营管理', 'VIEW', 2022,
        '商户日清列表/导出不受处室数据范围限制'),
       ('workflow.view.all', '流程-查看全部', 'workflow', '流程中心', 'VIEW', 2023,
        '流程中心可查看全部流程，而非仅本人发起/参与'),
       ('patrol.delete', '巡场-删除记录', 'patrol', '巡场管理', 'DANGER', 2024,
        '删除巡场提交记录与巡场问题（仅超级管理员）'),
       ('ops.patrol.delete', '商管巡场-删除任务', 'opsPatrol', '商管巡场', 'DANGER', 2025,
        '删除商管巡场任务（仅超级管理员）'),
       ('dashboard.stats.base.view', '工作台-查看基础统计', 'dashboard', '工作台', 'VIEW', 2026,
        '查看工作台统计卡片（原仅超级管理员/系统管理员/只读用户）'),
       ('operation.form.image.view', '运营表单-查看图片', 'operation', '运营管理', 'VIEW', 2027,
        '查看运营表单/H5 上传的图片（登录用户可用）'),
       ('operation.form.image.upload', '运营表单-上传图片', 'operation', '运营管理', 'EDIT', 2028,
        '上传运营表单/H5 表单图片')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

-- ---------------------------------------------------------------- 管理员显式授权（替代代码内特判）
-- 超级管理员：全部启用权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN' AND p.enabled = 1;

-- 系统管理员：全部启用权限，但排除 system.user.* 与 construction.delete（保持原代码特判口径）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.enabled = 1
  AND p.code NOT LIKE 'system.user.%'
  AND p.code <> 'construction.delete';

-- ---------------------------------------------------------------- 按改造前角色名口径逐一授予
-- 客诉数据范围：原 scopeUserId() 对 SUPER_ADMIN/SYSTEM_ADMIN/CS_AGENT/CS_MANAGER 直接放行，
-- PROPERTY_MANAGER / OPS_PATROL_MANAGER 不具备 APPLICANT 角色也按全局范围；APPLICANT 仍为本人范围。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'CS_AGENT', 'CS_MANAGER', 'PROPERTY_MANAGER', 'OPS_PATROL_MANAGER')
  AND p.code = 'complaint.view.all';

-- 运营大屏全局视角：原 Controller 内 ROLE_SUPER_ADMIN/ROLE_SYSTEM_ADMIN/ROLE_DEVICE_VERIFIER
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'DEVICE_VERIFIER')
  AND p.code = 'operation.dashboard.view.all';

-- 商户日清全量范围：原 allScope() 的 7 个角色
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'DEVICE_VERIFIER', 'OPS_PATROL_MANAGER', 'CS_AGENT', 'CS_MANAGER', 'AUDITOR')
  AND p.code = 'operation.clearance.view.all';

-- 流程全量范围：原 ownOnly() 中 elevated 的 5 个角色
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'DEVICE_VERIFIER', 'PLATFORM_OPERATOR', 'AUDITOR')
  AND p.code = 'workflow.view.all';

-- 删除类权限：仅超级管理员
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('patrol.delete', 'ops.patrol.delete');

-- 工作台统计：原 @PreAuthorize 的 SUPER_ADMIN/SYSTEM_ADMIN/VIEWER
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'VIEWER')
  AND p.code = 'dashboard.stats.base.view';

-- 运营表单图片：查看原为 isAuthenticated()，故授予全部角色
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE p.code = 'operation.form.image.view';

-- 运营表单图片：上传原为 SUPER_ADMIN/SYSTEM_ADMIN/APPLICANT/DEVICE_VERIFIER/VIEWER
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'APPLICANT', 'DEVICE_VERIFIER', 'VIEWER')
  AND p.code = 'operation.form.image.upload';
