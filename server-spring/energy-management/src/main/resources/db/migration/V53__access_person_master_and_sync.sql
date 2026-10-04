ALTER TABLE access_person
  ADD COLUMN gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER person_name,
  ADD COLUMN id_number_ciphertext TEXT AFTER id_number,
  ADD COLUMN id_number_iv VARCHAR(64) AFTER id_number_ciphertext,
  ADD COLUMN id_number_last4 VARCHAR(4) AFTER id_number_iv,
  ADD COLUMN affiliation_type VARCHAR(24) NOT NULL DEFAULT 'EXTERNAL' AFTER organization_name,
  ADD COLUMN stall_id BIGINT AFTER affiliation_type,
  ADD COLUMN approval_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER review_status,
  ADD COLUMN approved_by VARCHAR(64) AFTER approval_status,
  ADD COLUMN approved_at DATETIME(3) AFTER approved_by,
  ADD COLUMN revoked_at DATETIME(3) AFTER approved_at,
  ADD COLUMN retention_until DATETIME(3) AFTER revoked_at;

ALTER TABLE visitor_appointment
  ADD COLUMN gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' AFTER visitor_name,
  ADD COLUMN id_number_ciphertext TEXT AFTER visitor_phone,
  ADD COLUMN id_number_iv VARCHAR(64) AFTER id_number_ciphertext,
  ADD COLUMN id_number_last4 VARCHAR(4) AFTER id_number_iv,
  ADD COLUMN affiliation_type VARCHAR(24) NOT NULL DEFAULT 'EXTERNAL' AFTER visitor_company,
  ADD COLUMN stall_id BIGINT AFTER affiliation_type,
  ADD COLUMN face_status VARCHAR(24) NOT NULL DEFAULT 'MISSING' AFTER stall_id;

CREATE TABLE access_person_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id BIGINT NOT NULL,
  document_type VARCHAR(24) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  encrypted TINYINT NOT NULL DEFAULT 1,
  sha256 VARCHAR(64) NOT NULL,
  quality_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  quality_message VARCHAR(500),
  ocr_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  ocr_result_json LONGTEXT,
  created_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_access_document_person(person_id,document_type),
  CONSTRAINT fk_access_document_person FOREIGN KEY(person_id) REFERENCES access_person(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_application (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  application_no VARCHAR(64) NOT NULL,
  application_type VARCHAR(24) NOT NULL,
  person_id BIGINT NOT NULL,
  appointment_id BIGINT,
  requested_valid_from DATETIME(3) NOT NULL,
  requested_valid_to DATETIME(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
  applicant_username VARCHAR(64) NOT NULL,
  approved_by VARCHAR(64),
  approved_at DATETIME(3),
  approval_note VARCHAR(500),
  revoked_at DATETIME(3),
  revoke_reason VARCHAR(500),
  snapshot_retention_until DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_application_no(application_no),
  KEY idx_access_application_status(status,created_at),
  KEY idx_access_application_person(person_id,created_at),
  CONSTRAINT fk_access_application_person FOREIGN KEY(person_id) REFERENCES access_person(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_device_sync_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  application_id BIGINT,
  person_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  operation_type VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3),
  device_response VARCHAR(1000),
  last_error VARCHAR(1000),
  started_at DATETIME(3),
  completed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_sync_batch_device(batch_no,device_id,operation_type),
  KEY idx_access_sync_status(status,next_retry_at),
  CONSTRAINT fk_access_sync_person FOREIGN KEY(person_id) REFERENCES access_person(id),
  CONSTRAINT fk_access_sync_device FOREIGN KEY(device_id) REFERENCES access_device(id),
  CONSTRAINT fk_access_sync_application FOREIGN KEY(application_id) REFERENCES access_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_person_retention_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id BIGINT,
  application_id BIGINT,
  snapshot_type VARCHAR(24) NOT NULL,
  snapshot_ciphertext LONGTEXT NOT NULL,
  snapshot_iv VARCHAR(64) NOT NULL,
  retained_until DATETIME(3) NOT NULL,
  purged_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_access_snapshot_retention(retained_until,purged_at),
  KEY idx_access_snapshot_person(person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_device_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  capability_code VARCHAR(64) NOT NULL,
  supported TINYINT NOT NULL DEFAULT 0,
  detail_json LONGTEXT,
  probed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_capability(device_id,capability_code),
  CONSTRAINT fk_access_capability_device FOREIGN KEY(device_id) REFERENCES access_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('access.person.approve','审批门禁人员申请','access','门禁管理','MANAGE',1016),
('access.person.sensitive.view','查看门禁完整敏感资料','access','门禁管理','DANGER',1017),
('access.person.document.manage','维护门禁证件与人脸资料','access','门禁管理','EDIT',1018),
('access.person.revoke','撤销门禁人员权限','access','门禁管理','DANGER',1019);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN ('access.person.approve','access.person.sensitive.view','access.person.document.manage','access.person.revoke');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='SYSTEM_ADMIN' AND p.code IN ('access.person.approve','access.person.document.manage','access.person.revoke');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code='access.person.approve';
