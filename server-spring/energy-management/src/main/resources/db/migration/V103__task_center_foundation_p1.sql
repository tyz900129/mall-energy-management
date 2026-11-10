-- P1 任务中心地基：计划补字段 + 生成日志 + 日历任务类型/字典/权限
--
-- 说明：
--   1. inspection_plan 增加分类、责任部门快照、回执模式、拍照要求、提前推送与催办配置。
--   2. 新建 calendar_generation_log，记录每次生成尝试，支持按日排查与安全补生成。
--   3. 新增 inspection_type=CALENDAR 与默认回执模板，日历计划直接引用，避免改造现有 NOT NULL 约束。
--   4. 新增 4 个 calendar.* 权限码，并授予角色与部门策略白名单。

ALTER TABLE inspection_plan
  ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'OTHER' AFTER template_id,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_id,
  ADD COLUMN form_mode VARCHAR(16) NOT NULL DEFAULT 'CHECKLIST' AFTER owner_dept_id,
  ADD COLUMN require_photo TINYINT NOT NULL DEFAULT 0 AFTER form_mode,
  ADD COLUMN advance_minutes INT NOT NULL DEFAULT 0 AFTER require_photo,
  ADD COLUMN urge_minutes INT NOT NULL DEFAULT 30 AFTER advance_minutes,
  ADD KEY idx_inspection_plan_owner_dept (owner_dept_id);

CREATE TABLE IF NOT EXISTS calendar_generation_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  occur_date DATE NOT NULL,
  period_key VARCHAR(64) NOT NULL,
  task_count INT NOT NULL DEFAULT 0,
  result VARCHAR(16) NOT NULL COMMENT 'SUCCESS/SKIPPED/ERROR',
  message VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_calendar_generation (plan_id, occur_date, period_key),
  KEY idx_calendar_generation_date (occur_date, result),
  CONSTRAINT fk_calendar_generation_plan FOREIGN KEY (plan_id) REFERENCES inspection_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='任务生成日志：每次生成尝试一条，可按日查询与补生成';

-- 日历任务类型（占位，非设备）
INSERT INTO inspection_type (code, name, target_category, description, enabled)
VALUES ('CALENDAR', '日历任务', 'OTHER', '个人/岗位/部门任务，非设备类事项', 1)
ON DUPLICATE KEY UPDATE name = '日历任务', target_category = 'OTHER', description = '个人/岗位/部门任务，非设备类事项', enabled = 1;

-- 默认回执模板（P2 会按计划创建专属模板，这里保证日历计划有合法引用）
INSERT INTO inspection_template (inspection_type_id, name, version_no, fields_json, enabled)
SELECT t.id, '日历任务默认回执', 1, '[]', 1
FROM inspection_type t
WHERE t.code = 'CALENDAR'
  AND NOT EXISTS (
    SELECT 1 FROM inspection_template x
    WHERE x.inspection_type_id = t.id AND x.name = '日历任务默认回执'
  );

-- 任务分类字典
INSERT IGNORE INTO business_dictionary (dict_type, item_value, item_label, sort_order, status) VALUES
('CALENDAR_CATEGORY', 'DAILY', '日常事务', 10, 1),
('CALENDAR_CATEGORY', 'SAFETY', '安全消防', 20, 1),
('CALENDAR_CATEGORY', 'CLEANING', '保洁环境', 30, 1),
('CALENDAR_CATEGORY', 'OPERATION', '运营管理', 40, 1),
('CALENDAR_CATEGORY', 'MEETING', '会议培训', 50, 1),
('CALENDAR_CATEGORY', 'OTHER', '其他', 99, 1);

-- 权限码
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order) VALUES
('calendar.view', '查看任务日历', 'calendar', '日历任务', 'VIEW', 400),
('calendar.execute', '执行任务回执', 'calendar', '日历任务', 'EDIT', 401),
('calendar.manage', '管理任务计划', 'calendar', '日历任务', 'MANAGE', 402),
('calendar.report', '查看任务报表', 'calendar', '日历任务', 'VIEW', 403)
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = 'calendar', module_name = '日历任务', action_type = VALUES(action_type), sort_order = VALUES(sort_order);

-- 管理员全量授权
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN') AND p.module_code = 'calendar';

-- 业务角色默认：可看、可回执
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('APPLICANT', 'VIEWER', 'DEVICE_VERIFIER', 'PROPERTY_MANAGER', 'OPS_PATROL_MANAGER', 'CS_AGENT', 'CS_MANAGER')
  AND p.code IN ('calendar.view', 'calendar.execute');

-- 管理岗位：可管理计划与看报表
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('PROPERTY_MANAGER', 'OPS_PATROL_MANAGER')
  AND p.code IN ('calendar.manage', 'calendar.report');

-- 物业下属课室白名单补充 calendar.，否则会被部门策略裁掉
INSERT IGNORE INTO sys_dept_permission_policy (department_id, mode, include_pattern, enabled, remark)
SELECT d.id, 'PREFIX', 'calendar.', 1, CONCAT(d.name, '：日历任务')
FROM sys_department d
WHERE d.code IN ('PROP_EQ', 'PROP_SEC', 'PROP_CLN');
