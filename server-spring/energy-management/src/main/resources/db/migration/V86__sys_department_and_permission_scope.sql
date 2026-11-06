-- 部门主数据：商管一处~四处用于展示与排班，物业/客服课用于权限裁剪
CREATE TABLE IF NOT EXISTS sys_department (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  dept_group VARCHAR(32) NOT NULL DEFAULT 'OPERATION',
  ops_dept_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_department_code (code),
  KEY idx_sys_department_group (dept_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO sys_department (code, name, dept_group, sort_order, enabled) VALUES
('SM01', '商管一处', 'OPERATION', 1, 1),
('SM02', '商管二处', 'OPERATION', 2, 1),
('SM03', '商管三处', 'OPERATION', 3, 1),
('SM04', '商管四处', 'OPERATION', 4, 1),
('OPS_CS', '客服课',   'SERVICE', 5, 1),
('PROP_EQ', '设备课',  'PROPERTY', 6, 1),
('PROP_SEC', '安保课', 'PROPERTY', 7, 1),
('PROP_CLN', '保洁课', 'PROPERTY', 8, 1);

-- 商管处与营运巡场处室建立关联（同一批 SM01~SM04）
UPDATE sys_department sd
JOIN ops_patrol_dept d ON d.code = sd.code
SET sd.ops_dept_id = d.id
WHERE sd.code IN ('SM01', 'SM02', 'SM03', 'SM04');

-- 用户归属部门：商管用于展示/排班，物业服务课用于权限裁剪
ALTER TABLE sys_user
  ADD COLUMN department_id BIGINT NULL AFTER status,
  ADD KEY idx_sys_user_department (department_id);

-- 客服课角色
INSERT INTO sys_role (code, name, description)
VALUES ('CS_AGENT', '客服员', '客诉受理与商户资料查看')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO sys_role (code, name, description)
VALUES ('CS_MANAGER', '客服主管', '客诉管理与客服排班')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- 商户日清导出权限
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('operation.clearance.export', '导出商户日清', 'operation', '运营管理', 'VIEW', 1012, '导出商户清场记录 Excel')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

-- 商户日清查看权限补齐：营运巡场主管、客服课、审计
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('OPS_PATROL_MANAGER', 'CS_AGENT', 'CS_MANAGER', 'AUDITOR')
  AND p.code = 'operation.clearance.view';

-- 商户日清导出权限授权
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'APPLICANT', 'OPS_PATROL_MANAGER', 'CS_MANAGER', 'AUDITOR')
  AND p.code = 'operation.clearance.export';

-- 客服课基础权限：商户资料与日清记录查看
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('CS_AGENT', 'CS_MANAGER')
  AND p.code IN ('dashboard.view', 'merchant.view', 'stall.view', 'operation.dashboard.view',
                 'operation.merchant.view', 'operation.clearance.view');

-- 客服主管额外权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'CS_MANAGER'
  AND p.code IN ('operation.clearance.export', 'operation.accountability.view');

-- 商管人员(APPLICANT)补营运巡场全部权限：营运巡场是商管主管/商管人员专属功能
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT'
  AND p.code LIKE 'ops.patrol.%';

-- 物业管理员(PROPERTY_MANAGER)补门禁认领权限：self/mine 分支（认领、我的门禁、我的访客、我的事件）
-- 门禁管理的 dashboard、persons、devices、visitors（管理端）、sync、write-mode 等不给 PM，单独授权
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'PROPERTY_MANAGER'
  AND p.code IN ('access.self.view', 'access.self.manage', 'access.event.view.self', 'visitor.view.own');
