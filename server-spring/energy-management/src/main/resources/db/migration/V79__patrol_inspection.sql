-- 干部值班巡场：点位、整改部门、值班排班、每日轮次、点位提交、问题单、照片、流转日志

CREATE TABLE patrol_point (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  area VARCHAR(64) NOT NULL,
  floor VARCHAR(64),
  location_text VARCHAR(255),
  point_type VARCHAR(32) NOT NULL DEFAULT 'PUBLIC_AREA',
  check_items_json TEXT,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_point_code (code),
  KEY idx_patrol_point_area (area, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  webhook_config_id BIGINT,
  cc_webhook_ids VARCHAR(255),
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  sla_hours INT NOT NULL DEFAULT 24,
  access_token VARCHAR(64),
  token_expires_at DATETIME(3),
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_department_code (code),
  UNIQUE KEY uk_patrol_department_token (access_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_duty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  duty_date DATE NOT NULL,
  user_id BIGINT,
  user_name VARCHAR(64) NOT NULL,
  backup_user_id BIGINT,
  backup_user_name VARCHAR(64),
  remark VARCHAR(255),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_duty_date_user (duty_date, user_name),
  KEY idx_patrol_duty_date (duty_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_round (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  round_date DATE NOT NULL,
  entry_token VARCHAR(64) NOT NULL,
  pushed_at DATETIME(3),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_round_date (round_date),
  UNIQUE KEY uk_patrol_round_token (entry_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  round_id BIGINT NOT NULL,
  point_id BIGINT NOT NULL,
  submitter_id BIGINT,
  submitter_name VARCHAR(64),
  result VARCHAR(16) NOT NULL,
  note VARCHAR(1000),
  check_results_json TEXT,
  client_ip VARCHAR(64),
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_patrol_submission_round_point (round_id, point_id),
  KEY idx_patrol_submission_time (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_no VARCHAR(32) NOT NULL,
  submission_id BIGINT,
  round_id BIGINT,
  point_id BIGINT,
  point_name VARCHAR(100),
  area VARCHAR(64),
  floor VARCHAR(64),
  department_id BIGINT,
  category VARCHAR(32),
  urgency VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  description VARCHAR(1000),
  status VARCHAR(16) NOT NULL DEFAULT 'UNASSIGNED',
  due_at DATETIME(3),
  assigned_by VARCHAR(64),
  assigned_at DATETIME(3),
  feedback_token VARCHAR(64) NOT NULL,
  submitter_name VARCHAR(64),
  submitted_at DATETIME(3),
  rectified_by VARCHAR(64),
  rectified_at DATETIME(3),
  rectification_note VARCHAR(1000),
  reviewed_by VARCHAR(64),
  reviewed_at DATETIME(3),
  review_note VARCHAR(500),
  closed_at DATETIME(3),
  overdue_flag TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_issue_no (issue_no),
  UNIQUE KEY uk_patrol_issue_token (feedback_token),
  KEY idx_patrol_issue_status (status, created_at),
  KEY idx_patrol_issue_department (department_id, status),
  KEY idx_patrol_issue_point (point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_type VARCHAR(16) NOT NULL,
  business_id BIGINT NOT NULL,
  file_name VARCHAR(255),
  file_path VARCHAR(255) NOT NULL,
  mime_type VARCHAR(64),
  file_size BIGINT,
  sha256 VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_patrol_attachment_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrol_issue_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_department_id BIGINT,
  to_department_id BIGINT,
  from_status VARCHAR(16),
  to_status VARCHAR(16),
  note VARCHAR(500),
  operator VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_patrol_issue_log_issue (issue_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 固定三个整改部门
INSERT INTO patrol_department (code, name, sla_hours, access_token, token_expires_at, sort_order)
VALUES
  ('EQUIPMENT', '设备课', 24, LOWER(SUBSTRING(MD5(CONCAT(RAND(), UUID())), 1, 32)), DATE_ADD(NOW(), INTERVAL 90 DAY), 1),
  ('SECURITY',  '安保课', 8,  LOWER(SUBSTRING(MD5(CONCAT(RAND(), UUID())), 1, 32)), DATE_ADD(NOW(), INTERVAL 90 DAY), 2),
  ('CLEANING',  '保洁课', 24, LOWER(SUBSTRING(MD5(CONCAT(RAND(), UUID())), 1, 32)), DATE_ADD(NOW(), INTERVAL 90 DAY), 3)
ON DUPLICATE KEY UPDATE name = VALUES(name), sla_hours = VALUES(sla_hours);

-- 角色：物业管理员
INSERT INTO sys_role (code, name, description)
VALUES ('PROPERTY_MANAGER', '物业管理员', '物业工作人员权限，并增加干部巡场提交与复核')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT pm.id, rp.permission_id
FROM sys_role pm
JOIN sys_role dv ON dv.code = 'DEVICE_VERIFIER'
JOIN sys_role_permission rp ON rp.role_id = dv.id
WHERE pm.code = 'PROPERTY_MANAGER';

-- 权限码
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description) VALUES
('patrol.submit',        '提交巡场记录',   'patrol', '巡场管理', 'EDIT', 2001, '进入巡场入口并提交点位巡检结果'),
('patrol.point.view',    '查看巡场配置',   'patrol', '巡场管理', 'VIEW', 2002, '查看巡场点位、整改部门与排班'),
('patrol.config',        '配置巡场',       'patrol', '巡场管理', 'EDIT', 2003, '维护点位、整改部门、排班并补发入口'),
('patrol.issue.view',    '查看巡场问题',   'patrol', '巡场管理', 'VIEW', 2004, '查看巡场记录与问题台账'),
('patrol.issue.assign',  '分派巡场问题',   'patrol', '巡场管理', 'EDIT', 2005, '指定、补充或变更问题整改部门'),
('patrol.issue.review',  '复核巡场问题',   'patrol', '巡场管理', 'EDIT', 2006, '复核整改结果并闭环或驳回')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code LIKE 'patrol.%';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'PROPERTY_MANAGER' AND p.code LIKE 'patrol.%';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'DEVICE_VERIFIER' AND p.code IN ('patrol.submit','patrol.point.view','patrol.issue.view');

-- 菜单
INSERT IGNORE INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
VALUES (0, 'patrol', '巡场管理', '/patrol', 'checklist', 'MENU', 45, 1);

INSERT IGNORE INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, m.code, m.name, m.path, m.icon, m.type, m.sort_order, m.enabled
FROM (
  SELECT 'patrol:points' AS code, '巡场点位' AS name, '/patrol/points' AS path, 'location' AS icon, 'MENU' AS type, 1 AS sort_order, 1 AS enabled UNION ALL
  SELECT 'patrol:departments', '整改部门', '/patrol/departments', 'office', 'MENU', 2, 1 UNION ALL
  SELECT 'patrol:duties', '值班排班', '/patrol/duties', 'calendar', 'MENU', 3, 1 UNION ALL
  SELECT 'patrol:issues', '巡场问题', '/patrol/issues', 'warning', 'MENU', 4, 1
) m
CROSS JOIN (SELECT id FROM sys_menu WHERE code = 'patrol') t;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN','PROPERTY_MANAGER')
  AND (m.code = 'patrol' OR m.code LIKE 'patrol:%');

-- 示例点位，便于上线即可体验（如已有同编码点位则跳过）
INSERT IGNORE INTO patrol_point (code, name, area, floor, location_text, point_type, check_items_json, sort_order) VALUES
('A-1F-01', '大堂入口',   'A 区', '1F',  'A 区 1F 大堂主入口',   'PUBLIC_AREA', '[{"label":"地面整洁无杂物"},{"label":"门头灯箱正常"}]', 1),
('A-1F-02', '消防通道',   'A 区', '1F',  'A 区 1F 东侧消防通道', 'FIRE_PASS',   '[{"label":"通道畅通无堆放"},{"label":"防火门可正常闭合"}]', 2),
('A-1F-03', '电梯厅',     'A 区', '1F',  'A 区 1F 客梯厅',       'PUBLIC_AREA', '[{"label":"电梯运行正常"},{"label":"轿厢与地面整洁"}]', 3),
('B-B1-01', '车库入口',   'B 区', 'B1',  'B 区 B1 车库入口',     'PARKING',     '[{"label":"道闸运行正常"},{"label":"照明正常"}]', 4),
('B-B1-02', '设备间',     'B 区', 'B1',  'B 区 B1 设备间',       'EQUIPMENT_ROOM', '[{"label":"设备运行无异常声响"},{"label":"无渗漏水"}]', 5);
