ALTER TABLE workflow
  MODIFY COLUMN merchant_code VARCHAR(6) NULL;

CREATE TABLE construction_workflow_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id BIGINT NOT NULL,
  construction_start_time DATETIME(3) NOT NULL,
  construction_end_time DATETIME(3) NOT NULL,
  merchant_principal_name VARCHAR(64) NOT NULL,
  merchant_principal_phone VARCHAR(32) NOT NULL,
  construction_company_name VARCHAR(128) NOT NULL,
  construction_principal_name VARCHAR(64) NOT NULL,
  construction_principal_phone VARCHAR(32) NOT NULL,
  property_engineer_name VARCHAR(64) NOT NULL,
  property_engineer_phone VARCHAR(32) NOT NULL,
  commercial_manager_name VARCHAR(64) NOT NULL,
  commercial_manager_phone VARCHAR(32) NOT NULL,
  related_workflow_id BIGINT NULL,
  stall_status_before VARCHAR(32),
  stall_status_after VARCHAR(32),
  previous_current_merchant_id BIGINT NULL,
  completed_by VARCHAR(64),
  completed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_construction_workflow (workflow_id),
  KEY idx_construction_related (related_workflow_id),
  KEY idx_construction_period (construction_start_time, construction_end_time),
  CONSTRAINT fk_construction_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE,
  CONSTRAINT fk_construction_related FOREIGN KEY (related_workflow_id) REFERENCES workflow(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order) VALUES
('construction.view','查看商户施工流程','construction','商户施工管理','VIEW',211),
('construction.create','发起和提交施工流程','construction','商户施工管理','EDIT',212),
('construction.manage','完成和管理施工流程','construction','商户施工管理','MANAGE',213),
('construction.delete','删除和复位施工流程','construction','商户施工管理','DANGER',214);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN' AND p.module_code = 'construction';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.code IN ('construction.view','construction.create','construction.manage');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT' AND p.code IN ('construction.view','construction.create');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('VIEWER','AUDITOR') AND p.code = 'construction.view';
