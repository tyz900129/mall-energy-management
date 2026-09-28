CREATE TABLE inspection_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(48) NOT NULL,
  name VARCHAR(96) NOT NULL,
  target_category VARCHAR(24) NOT NULL DEFAULT 'DEVICE',
  description VARCHAR(500),
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inspection_type_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  version_no INT NOT NULL DEFAULT 1,
  fields_json LONGTEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_inspection_template_type (inspection_type_id),
  CONSTRAINT fk_inspection_template_type FOREIGN KEY (inspection_type_id) REFERENCES inspection_type(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  inspection_type_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  frequency_type VARCHAR(24) NOT NULL,
  frequency_value INT NOT NULL DEFAULT 1,
  schedule_config_json VARCHAR(1000),
  start_date DATE NOT NULL,
  end_date DATE,
  execute_time TIME NOT NULL,
  overdue_minutes INT NOT NULL DEFAULT 1440,
  assignee_user_id BIGINT,
  assignee_name VARCHAR(64),
  webhook_config_ids VARCHAR(500),
  enabled TINYINT NOT NULL DEFAULT 1,
  last_generated_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_inspection_plan_enabled (enabled, start_date, end_date),
  CONSTRAINT fk_inspection_plan_type FOREIGN KEY (inspection_type_id) REFERENCES inspection_type(id),
  CONSTRAINT fk_inspection_plan_template FOREIGN KEY (template_id) REFERENCES inspection_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_plan_target (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(96) NOT NULL,
  target_code VARCHAR(96),
  target_name VARCHAR(255) NOT NULL,
  location_text VARCHAR(255),
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_plan_target (plan_id, target_type, target_id),
  CONSTRAINT fk_inspection_target_plan FOREIGN KEY (plan_id) REFERENCES inspection_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no VARCHAR(40) NOT NULL,
  plan_id BIGINT NOT NULL,
  inspection_type_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(96) NOT NULL,
  target_code VARCHAR(96),
  target_name VARCHAR(255) NOT NULL,
  location_text VARCHAR(255),
  period_key VARCHAR(32) NOT NULL,
  scheduled_at DATETIME(3) NOT NULL,
  due_at DATETIME(3) NOT NULL,
  assignee_user_id BIGINT,
  assignee_name VARCHAR(64),
  access_token VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  started_at DATETIME(3),
  submitted_at DATETIME(3),
  result_json LONGTEXT,
  result_summary VARCHAR(1000),
  completed_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_task_no (task_no),
  UNIQUE KEY uk_inspection_task_token (access_token),
  UNIQUE KEY uk_inspection_task_period (plan_id, target_type, target_id, period_key),
  KEY idx_inspection_task_status_due (status, due_at),
  KEY idx_inspection_task_assignee (assignee_user_id, status),
  CONSTRAINT fk_inspection_task_plan FOREIGN KEY (plan_id) REFERENCES inspection_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  field_key VARCHAR(96),
  field_label VARCHAR(128),
  level VARCHAR(24) NOT NULL DEFAULT 'WARNING',
  description VARCHAR(1000) NOT NULL,
  rectification_requirement VARCHAR(1000),
  responsible_user_id BIGINT,
  responsible_name VARCHAR(64),
  due_at DATETIME(3),
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  rectification_note VARCHAR(1000),
  rectified_by VARCHAR(64),
  rectified_at DATETIME(3),
  reviewed_by VARCHAR(64),
  reviewed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_inspection_issue_task (task_id),
  KEY idx_inspection_issue_status (status, due_at),
  CONSTRAINT fk_inspection_issue_task FOREIGN KEY (task_id) REFERENCES inspection_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_reminder_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_reminder (task_id,event_type),
  CONSTRAINT fk_inspection_reminder_task FOREIGN KEY (task_id) REFERENCES inspection_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (code,name,module_code,module_name,action_type,sort_order) VALUES
('inspection.dashboard.view','查看巡检数据大屏','inspection','设备巡检管理','VIEW',300),
('inspection.task.view','查看巡检任务','inspection','设备巡检管理','VIEW',301),
('inspection.task.execute','执行巡检任务','inspection','设备巡检管理','EDIT',302),
('inspection.type.manage','管理巡检类型','inspection','设备巡检管理','MANAGE',303),
('inspection.template.manage','管理巡检表单','inspection','设备巡检管理','MANAGE',304),
('inspection.plan.manage','管理巡检计划','inspection','设备巡检管理','MANAGE',305),
('inspection.issue.manage','处理巡检问题','inspection','设备巡检管理','EDIT',306),
('inspection.issue.review','复核巡检整改','inspection','设备巡检管理','MANAGE',307);

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN') AND p.module_code='inspection';

INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code IN ('inspection.dashboard.view','inspection.task.view','inspection.task.execute','inspection.issue.manage');
