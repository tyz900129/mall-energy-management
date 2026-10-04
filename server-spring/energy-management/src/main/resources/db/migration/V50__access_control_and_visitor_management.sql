CREATE TABLE access_point (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  point_code VARCHAR(64) NOT NULL,
  point_name VARCHAR(100) NOT NULL,
  location VARCHAR(255),
  floor_code VARCHAR(64),
  description VARCHAR(500),
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_point_code(point_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  access_point_id BIGINT NOT NULL,
  device_code VARCHAR(64) NOT NULL,
  device_name VARCHAR(100) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  model VARCHAR(64),
  serial_number VARCHAR(128),
  ip_address VARCHAR(64),
  http_port INT NOT NULL DEFAULT 80,
  sdk_port INT NOT NULL DEFAULT 8000,
  firmware_version VARCHAR(128),
  isapi_enabled TINYINT NOT NULL DEFAULT 0,
  online_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
  last_connected_at DATETIME(3),
  last_sync_at DATETIME(3),
  read_only_mode TINYINT NOT NULL DEFAULT 1,
  enabled TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_device_code(device_code),
  UNIQUE KEY uk_access_device_serial(serial_number),
  KEY idx_access_device_point(access_point_id,direction),
  CONSTRAINT fk_access_device_point FOREIGN KEY(access_point_id) REFERENCES access_point(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_person (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_no VARCHAR(64) NOT NULL,
  system_user_id BIGINT,
  person_name VARCHAR(64) NOT NULL,
  person_type VARCHAR(32) NOT NULL DEFAULT 'LONG_TERM',
  employee_no VARCHAR(64),
  phone VARCHAR(32),
  id_type VARCHAR(24),
  id_number VARCHAR(64),
  organization_name VARCHAR(128),
  department_name VARCHAR(128),
  merchant_code VARCHAR(32),
  valid_from DATETIME(3),
  valid_to DATETIME(3),
  face_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
  source_type VARCHAR(24) NOT NULL DEFAULT 'DEVICE_LEGACY',
  managed_by_system TINYINT NOT NULL DEFAULT 0,
  person_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
  last_passed_at DATETIME(3),
  review_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
  remark VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_person_no(person_no),
  KEY idx_access_person_user(system_user_id),
  KEY idx_access_person_phone(phone),
  KEY idx_access_person_expiry(valid_to,person_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_person_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  device_person_no VARCHAR(64),
  device_employee_no VARCHAR(64),
  device_person_name VARCHAR(64),
  device_phone VARCHAR(32),
  device_valid_from DATETIME(3),
  device_valid_to DATETIME(3),
  face_present TINYINT NOT NULL DEFAULT 0,
  card_no VARCHAR(64),
  sync_status VARCHAR(24) NOT NULL DEFAULT 'DEVICE_LEGACY',
  raw_snapshot_json LONGTEXT,
  first_discovered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_read_at DATETIME(3),
  last_error VARCHAR(1000),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_person_device(person_id,device_id),
  KEY idx_access_pd_device_person(device_id,device_person_no),
  CONSTRAINT fk_access_pd_person FOREIGN KEY(person_id) REFERENCES access_person(id),
  CONSTRAINT fk_access_pd_device FOREIGN KEY(device_id) REFERENCES access_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE visitor_appointment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_no VARCHAR(64) NOT NULL,
  visitor_person_id BIGINT,
  visitor_name VARCHAR(64) NOT NULL,
  visitor_phone VARCHAR(32) NOT NULL,
  visitor_company VARCHAR(128),
  host_username VARCHAR(64),
  host_name VARCHAR(64) NOT NULL,
  host_department VARCHAR(128),
  visit_reason VARCHAR(500) NOT NULL,
  valid_from DATETIME(3) NOT NULL,
  valid_to DATETIME(3) NOT NULL,
  access_point_ids_json VARCHAR(1000),
  vehicle_no VARCHAR(32),
  companion_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
  applicant_username VARCHAR(64) NOT NULL,
  approved_by VARCHAR(64),
  approved_at DATETIME(3),
  approval_note VARCHAR(500),
  terminated_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_visitor_appointment_no(appointment_no),
  KEY idx_visitor_applicant(applicant_username,created_at),
  KEY idx_visitor_host(host_username,created_at),
  KEY idx_visitor_status(status,valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  person_id BIGINT,
  device_person_no VARCHAR(64),
  event_serial_no VARCHAR(128),
  event_time DATETIME(3) NOT NULL,
  direction VARCHAR(16),
  verify_mode VARCHAR(32),
  result VARCHAR(24) NOT NULL,
  event_type VARCHAR(64),
  snapshot_path VARCHAR(500),
  raw_event_json LONGTEXT,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_event_device_serial(device_id,event_serial_no),
  KEY idx_access_event_time(event_time),
  KEY idx_access_event_person(person_id,event_time),
  CONSTRAINT fk_access_event_device FOREIGN KEY(device_id) REFERENCES access_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_person_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id BIGINT NOT NULL,
  review_type VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  reason VARCHAR(500),
  decision VARCHAR(24),
  decision_note VARCHAR(500),
  reviewed_by VARCHAR(64),
  reviewed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_access_review_status(status,review_type),
  CONSTRAINT fk_access_review_person FOREIGN KEY(person_id) REFERENCES access_person(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('access.dashboard.view','查看门禁数据大屏','access','门禁管理','VIEW',1001),
('access.device.view','查看门禁设备','access','门禁管理','VIEW',1002),
('access.device.manage','维护门禁设备','access','门禁管理','EDIT',1003),
('access.person.view','查看长期人员','access','门禁管理','VIEW',1004),
('access.person.manage','维护长期人员','access','门禁管理','EDIT',1005),
('access.person.review','复核长期未通行人员','access','门禁管理','MANAGE',1006),
('access.self.view','查看本人门禁资料','access','门禁管理','VIEW',1007),
('visitor.create','提交访客预约','access','门禁管理','EDIT',1008),
('visitor.view.own','查看本人相关预约','access','门禁管理','VIEW',1009),
('visitor.view.all','查看全部访客预约','access','门禁管理','VIEW',1010),
('visitor.approve','审批访客预约','access','门禁管理','MANAGE',1011),
('access.event.view.self','查看本人通行记录','access','门禁管理','VIEW',1012),
('access.event.view.all','查看全部通行记录','access','门禁管理','VIEW',1013),
('access.sync.import','只读导入设备存量人员','access','门禁管理','EDIT',1014),
('access.sync.write','向门禁设备写入人员资料','access','门禁管理','DANGER',1015);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='SUPER_ADMIN' AND p.module_code='access';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='SYSTEM_ADMIN' AND p.code IN
('access.dashboard.view','access.device.view','access.device.manage','access.person.view','access.person.manage','access.person.review','access.self.view','visitor.create','visitor.view.own','visitor.view.all','visitor.approve','access.event.view.self','access.event.view.all','access.sync.import');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='APPLICANT' AND p.code IN
('access.self.view','visitor.create','visitor.view.own','access.event.view.self');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='VIEWER' AND p.code IN
('access.self.view','visitor.view.own','access.event.view.self');
