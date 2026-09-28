ALTER TABLE construction_workflow_detail
  ADD COLUMN permit_no VARCHAR(32) NULL AFTER workflow_id,
  ADD COLUMN public_token VARCHAR(64) NULL AFTER permit_no,
  ADD COLUMN acceptance_requested_at DATETIME(3) NULL AFTER related_workflow_id,
  ADD COLUMN archived_at DATETIME(3) NULL AFTER completed_at;

CREATE UNIQUE INDEX uk_construction_permit_no ON construction_workflow_detail (permit_no);
CREATE UNIQUE INDEX uk_construction_public_token ON construction_workflow_detail (public_token);

CREATE TABLE construction_inspection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id BIGINT NOT NULL,
  inspection_type VARCHAR(24) NOT NULL,
  result VARCHAR(24) NOT NULL,
  problem_description VARCHAR(1000),
  rectification_requirement VARCHAR(1000),
  rectification_deadline DATETIME(3),
  rectification_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
  inspector_user_id BIGINT,
  inspector_name VARCHAR(64) NOT NULL,
  inspector_phone VARCHAR(32),
  inspected_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_construction_inspection_workflow (workflow_id, inspected_at),
  CONSTRAINT fk_construction_inspection_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE construction_acceptance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  actual_completion_time DATETIME(3) NOT NULL,
  application_note VARCHAR(1000),
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  result VARCHAR(24),
  result_note VARCHAR(1000),
  requested_by VARCHAR(64) NOT NULL,
  requested_at DATETIME(3) NOT NULL,
  inspected_by VARCHAR(64),
  inspected_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_construction_acceptance_round (workflow_id, round_no),
  KEY idx_construction_acceptance_status (status),
  CONSTRAINT fk_construction_acceptance_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order) VALUES
('construction.inspect.security','提交施工安保巡检','construction','商户施工管理','EDIT',215),
('construction.inspect.engineering','提交施工工程巡检','construction','商户施工管理','EDIT',216),
('construction.inspect.cleaning','提交施工保洁巡检','construction','商户施工管理','EDIT',217),
('construction.acceptance.request','提交施工验收申请','construction','商户施工管理','EDIT',218),
('construction.acceptance.manage','执行施工验收','construction','商户施工管理','MANAGE',219),
('construction.attachment.upload','上传施工巡检与验收照片','construction','商户施工管理','EDIT',220);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('construction.inspect.security','construction.inspect.engineering','construction.inspect.cleaning','construction.acceptance.request','construction.acceptance.manage','construction.attachment.upload');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.code IN ('construction.inspect.security','construction.inspect.engineering','construction.inspect.cleaning','construction.acceptance.request','construction.acceptance.manage','construction.attachment.upload');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'DEVICE_VERIFIER' AND p.code IN ('construction.view','construction.create','construction.inspect.engineering','construction.acceptance.manage','construction.attachment.upload');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT' AND p.code IN ('construction.acceptance.request','construction.attachment.upload');
