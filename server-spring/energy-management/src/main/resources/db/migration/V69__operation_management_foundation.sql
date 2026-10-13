-- 运营管理第一阶段：角色展示名称、责任商管及交接记录。
UPDATE sys_role SET name='普通用户', description='本人门禁资料认领与查看' WHERE code='VIEWER';
UPDATE sys_role SET name='物业工作人员', description='现场核验、巡检及物业检查' WHERE code='DEVICE_VERIFIER';
UPDATE sys_role SET name='商管人员', description='业务申请、商户服务及运营管理' WHERE code='APPLICANT';

CREATE TABLE operation_merchant_responsibility (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stall_id BIGINT NOT NULL,
  responsible_user_id BIGINT NOT NULL,
  contact_phone VARCHAR(32),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  effective_from DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  remark VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_operation_responsibility_stall (stall_id),
  KEY idx_operation_responsibility_user (responsible_user_id,status),
  CONSTRAINT fk_operation_responsibility_stall FOREIGN KEY (stall_id) REFERENCES stall(id) ON DELETE CASCADE,
  CONSTRAINT fk_operation_responsibility_user FOREIGN KEY (responsible_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE operation_merchant_responsibility_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  responsibility_id BIGINT NOT NULL,
  stall_id BIGINT NOT NULL,
  from_user_id BIGINT,
  to_user_id BIGINT NOT NULL,
  transfer_type VARCHAR(20) NOT NULL DEFAULT 'ASSIGN',
  contact_phone VARCHAR(32),
  effective_from DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  effective_to DATETIME(3),
  remark VARCHAR(255),
  operated_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_operation_responsibility_history_stall (stall_id,created_at),
  KEY idx_operation_responsibility_history_user (to_user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.merchant.view','查看责任商管','operation','运营管理','VIEW',1001,'查看商户责任商管、联系方式及交接记录'),
('operation.merchant.manage','维护责任商管','operation','运营管理','EDIT',1002,'分配、承接或移交责任商管'),
('operation.dashboard.view','查看运营数据大屏','operation','运营管理','VIEW',1003,'后续运营数据大屏权限'),
('operation.clearance.submit','提交商户日清','operation','运营管理','EDIT',1004,'商户员工扫码提交三关一闭'),
('operation.clearance.review','执行物业日清检查','operation','运营管理','EDIT',1005,'物业工作人员执行日清检查')
ON DUPLICATE KEY UPDATE name=VALUES(name),module_code=VALUES(module_code),module_name=VALUES(module_name),action_type=VALUES(action_type),sort_order=VALUES(sort_order),description=VALUES(description);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code LIKE 'operation.%';

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='APPLICANT' AND p.code IN ('operation.merchant.view','operation.merchant.manage','operation.dashboard.view');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code IN ('operation.dashboard.view','operation.clearance.review');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='VIEWER' AND p.code='operation.clearance.submit';
