-- =====================================================================
-- V82 营运巡场管理（一期）
-- 与「巡场管理」(patrol_*) 完全独立：表前缀 ops_patrol_*，权限 ops.patrol.*
-- 四个商管处室 × 两套表单（零售/餐饮）× 七个时段，按时间段推送检查任务
-- =====================================================================

-- ---------------------------------------------------------------- 处室
CREATE TABLE IF NOT EXISTS ops_patrol_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(16) NOT NULL,
  name VARCHAR(64) NOT NULL,
  form_type VARCHAR(16) NOT NULL COMMENT 'RETAIL 零售 / FOOD 餐饮',
  webhook_config_id BIGINT COMMENT '专属群机器人',
  leader_name VARCHAR(64) COMMENT '处长姓名',
  close_time VARCHAR(8) NOT NULL DEFAULT '21:30' COMMENT '闭店时间 HH:mm，清场段相对计算基准',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_dept_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 处室负责楼层（可配置，一对多）
CREATE TABLE IF NOT EXISTS ops_patrol_dept_floor (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id BIGINT NOT NULL,
  floor_code VARCHAR(32) NOT NULL,
  floor_name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ops_patrol_dept_floor (dept_id, floor_code),
  KEY idx_ops_patrol_dept_floor_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 时段
CREATE TABLE IF NOT EXISTS ops_patrol_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(16) NOT NULL,
  name VARCHAR(64) NOT NULL,
  relative_to_close TINYINT NOT NULL DEFAULT 0 COMMENT '1=相对闭店时间（用偏移），0=固定时间',
  start_time VARCHAR(8) COMMENT '固定开始 HH:mm',
  end_time VARCHAR(8) COMMENT '固定结束 HH:mm',
  offset_start_min INT COMMENT '相对闭店开始偏移（分钟）',
  offset_end_min INT COMMENT '相对闭店结束偏移（分钟）',
  is_handover TINYINT NOT NULL DEFAULT 0 COMMENT '1=交接时段（不生成交检任务，生成交接单）',
  push_advance_minutes INT NOT NULL DEFAULT 15 COMMENT '提前推送分钟数',
  urge_after_minutes INT NOT NULL DEFAULT 10 COMMENT '开始后未填报催办分钟数',
  remind_before_end_minutes INT NOT NULL DEFAULT 10 COMMENT '截止前提醒分钟数',
  overdue_after_minutes INT NOT NULL DEFAULT 30 COMMENT '结束后判逾期分钟数',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_slot_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 检查项
CREATE TABLE IF NOT EXISTS ops_patrol_check_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(16) NOT NULL,
  form_type VARCHAR(16) NOT NULL COMMENT 'RETAIL / FOOD',
  group_name VARCHAR(32) NOT NULL,
  content VARCHAR(255) NOT NULL,
  photo_required TINYINT NOT NULL DEFAULT 0,
  category VARCHAR(32) NOT NULL DEFAULT '商户管理' COMMENT '不合格时默认问题分类',
  due_hours INT NOT NULL DEFAULT 24 COMMENT '不合格问题整改时限（小时）',
  urgency VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / URGENT',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_item_code (code),
  KEY idx_ops_patrol_item_form (form_type, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 检查项 × 时段（多对多）
CREATE TABLE IF NOT EXISTS ops_patrol_item_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  UNIQUE KEY uk_ops_patrol_item_slot (item_id, slot_id),
  KEY idx_ops_patrol_item_slot_slot (slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 排班
CREATE TABLE IF NOT EXISTS ops_patrol_duty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  duty_date DATE NOT NULL,
  dept_id BIGINT NOT NULL,
  user_id BIGINT,
  user_name VARCHAR(64),
  backup_users_json VARCHAR(512) COMMENT '备班 [{id,name}]',
  remark VARCHAR(255),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_duty (duty_date, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 任务
CREATE TABLE IF NOT EXISTS ops_patrol_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_date DATE NOT NULL,
  dept_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  is_handover TINYINT NOT NULL DEFAULT 0,
  assignee_user_id BIGINT COMMENT '排班指定人（可空=通用任务）',
  assignee_name VARCHAR(64),
  entry_token VARCHAR(64) NOT NULL COMMENT '免登录填报 token（绑定处室+日期）',
  push_at DATETIME(3) COMMENT '计划推送时间',
  pushed_at DATETIME(3) COMMENT '实际推送时间',
  plan_start_at DATETIME(3) NOT NULL,
  plan_end_at DATETIME(3) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/DONE/OVERDUE/EXEMPT',
  urge_sent TINYINT NOT NULL DEFAULT 0,
  remind_sent TINYINT NOT NULL DEFAULT 0,
  started_at DATETIME(3),
  finished_at DATETIME(3),
  late_minutes INT NOT NULL DEFAULT 0,
  pass_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  skip_count INT NOT NULL DEFAULT 0,
  exempt_reason VARCHAR(255),
  submitter_id BIGINT COMMENT '实际提交人',
  submitter_name VARCHAR(64),
  submit_ip VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_task (task_date, dept_id, slot_id),
  UNIQUE KEY uk_ops_patrol_task_token (entry_token),
  KEY idx_ops_patrol_task_status (status, plan_end_at),
  KEY idx_ops_patrol_task_push (pushed_at, push_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务项（检查项快照 + 结果）
CREATE TABLE IF NOT EXISTS ops_patrol_task_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  item_id BIGINT,
  item_code VARCHAR(16) NOT NULL,
  item_content VARCHAR(255) NOT NULL,
  group_name VARCHAR(32) NOT NULL,
  photo_required TINYINT NOT NULL DEFAULT 0,
  category VARCHAR(32) NOT NULL DEFAULT '商户管理',
  due_hours INT NOT NULL DEFAULT 24,
  urgency VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  result VARCHAR(16) COMMENT 'PASS/FAIL/NA，NULL=未检',
  note VARCHAR(255),
  issue_id BIGINT,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ops_patrol_task_item (task_id, item_code),
  KEY idx_ops_patrol_task_item_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 问题单
CREATE TABLE IF NOT EXISTS ops_patrol_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_no VARCHAR(32) NOT NULL,
  task_id BIGINT NOT NULL,
  task_item_id BIGINT,
  dept_id BIGINT NOT NULL COMMENT '跟进处室',
  merchant_id BIGINT,
  merchant_name VARCHAR(128) COMMENT '店铺快照',
  stall_id BIGINT,
  stall_code VARCHAR(32),
  floor_name VARCHAR(64),
  category VARCHAR(32) NOT NULL,
  urgency VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  description VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RECTIFYING/REVIEWING/CLOSED',
  due_at DATETIME(3),
  owner_user_id BIGINT COMMENT '跟进人',
  owner_name VARCHAR(64),
  rectified_note VARCHAR(500),
  rectified_at DATETIME(3),
  reviewed_by VARCHAR(64),
  reviewed_at DATETIME(3),
  review_note VARCHAR(500),
  closed_at DATETIME(3),
  overdue_flag TINYINT NOT NULL DEFAULT 0,
  repeat_flag TINYINT NOT NULL DEFAULT 0,
  submitter_id BIGINT,
  submitter_name VARCHAR(64),
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_issue_no (issue_no),
  KEY idx_ops_patrol_issue_dept (dept_id, status),
  KEY idx_ops_patrol_issue_merchant (merchant_id),
  KEY idx_ops_patrol_issue_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 问题流转日志
CREATE TABLE IF NOT EXISTS ops_patrol_issue_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_status VARCHAR(16),
  to_status VARCHAR(16),
  operator VARCHAR(64),
  note VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_ops_patrol_issue_log (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 交接单
CREATE TABLE IF NOT EXISTS ops_patrol_handover (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  handover_date DATE NOT NULL,
  dept_id BIGINT NOT NULL,
  task_id BIGINT,
  from_user_id BIGINT,
  from_user_name VARCHAR(64),
  to_user_id BIGINT,
  to_user_name VARCHAR(64),
  unclosed_issue_ids VARCHAR(1024),
  unclosed_count INT NOT NULL DEFAULT 0,
  pass_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  confirmed TINYINT NOT NULL DEFAULT 0,
  confirmed_at DATETIME(3),
  confirmed_by VARCHAR(64),
  remark VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ops_patrol_handover (handover_date, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 字典
INSERT IGNORE INTO business_dictionary (dict_type, item_value, item_label, sort_order, status, remark)
VALUES
  ('OPS_PATROL_CATEGORY', '设施设备', '设施设备', 1, 1, '设备运行、POS 机、油烟燃气等设备类问题'),
  ('OPS_PATROL_CATEGORY', '环境卫生', '环境卫生', 2, 1, '卫生、垃圾、异味、食安类问题'),
  ('OPS_PATROL_CATEGORY', '消防安全', '消防安全', 3, 1, '消防通道、器材、燃气安全类问题'),
  ('OPS_PATROL_CATEGORY', '秩序安全', '秩序安全', 4, 1, '客流秩序、施工安全类问题'),
  ('OPS_PATROL_CATEGORY', '商户管理', '商户管理', 5, 1, '人员服务、陈列、营运规范类问题'),
  ('OPS_PATROL_CATEGORY', '其他', '其他', 9, 1, '未归入以上分类的其他问题');

-- ---------------------------------------------------------------- 角色与权限
INSERT INTO sys_role (code, name, description)
VALUES ('OPS_PATROL_MANAGER', '营运巡场主管', '营运巡场配置、排班、任务看板、问题复核与报表（数据范围：全部四处）')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description) VALUES
('ops.patrol.dept',    '处室与群配置',   'opsPatrol', '营运巡场管理', 'EDIT', 2101, '维护处室、负责楼层、闭店时间与群机器人'),
('ops.patrol.config',  '时段与检查项配置', 'opsPatrol', '营运巡场管理', 'EDIT', 2102, '维护时段参数、检查项库与干部排班'),
('ops.patrol.execute', '执行巡场任务',   'opsPatrol', '营运巡场管理', 'EDIT', 2103, '填报检查项、提交任务与交接确认'),
('ops.patrol.assign',  '问题分派跟进',   'opsPatrol', '营运巡场管理', 'EDIT', 2104, '指定跟进人、提交整改'),
('ops.patrol.review',  '问题复核闭环',   'opsPatrol', '营运巡场管理', 'EDIT', 2105, '复核整改结果并闭环或驳回'),
('ops.patrol.report',  '履职报表',       'opsPatrol', '营运巡场管理', 'VIEW', 2106, '查看与导出履职与问题报表')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN' AND p.code LIKE 'ops.patrol.%';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.code IN ('ops.patrol.dept','ops.patrol.report');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'OPS_PATROL_MANAGER' AND p.code LIKE 'ops.patrol.%';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT' AND p.code = 'ops.patrol.execute';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'AUDITOR' AND p.code = 'ops.patrol.report';

-- ---------------------------------------------------------------- 菜单
INSERT IGNORE INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
VALUES (0, 'opsPatrol', '营运巡场管理', '/ops-patrol', 'shop', 'MENU', 46, 1);

INSERT IGNORE INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, m.code, m.name, m.path, m.icon, m.type, m.sort_order, m.enabled
FROM (
  SELECT 'opsPatrol:depts'    AS code, '处室与群配置' AS name, '/ops-patrol/depts'    AS path, 'office'    AS icon, 'MENU' AS type, 1 AS sort_order, 1 AS enabled UNION ALL
  SELECT 'opsPatrol:slots',   '时段设置',   '/ops-patrol/slots',   'clock',     'MENU', 2, 1 UNION ALL
  SELECT 'opsPatrol:items',   '检查项库',   '/ops-patrol/items',   'list',      'MENU', 3, 1 UNION ALL
  SELECT 'opsPatrol:duties',  '干部排班',   '/ops-patrol/duties',  'calendar',  'MENU', 4, 1 UNION ALL
  SELECT 'opsPatrol:tasks',   '任务看板',   '/ops-patrol/tasks',   'grid',      'MENU', 5, 1 UNION ALL
  SELECT 'opsPatrol:issues',  '营运问题',   '/ops-patrol/issues',  'warning',   'MENU', 6, 1 UNION ALL
  SELECT 'opsPatrol:handovers','交接记录',  '/ops-patrol/handovers','files',    'MENU', 7, 1 UNION ALL
  SELECT 'opsPatrol:reports', '履职报表',   '/ops-patrol/reports', 'data-line', 'MENU', 8, 1
) m
CROSS JOIN (SELECT id FROM sys_menu WHERE code = 'opsPatrol') t;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN','OPS_PATROL_MANAGER')
  AND (m.code = 'opsPatrol' OR m.code LIKE 'opsPatrol:%');

-- ---------------------------------------------------------------- 处室种子
INSERT IGNORE INTO ops_patrol_dept (code, name, form_type, leader_name, close_time, sort_order) VALUES
('SM01', '商管一处', 'RETAIL', NULL, '21:30', 1),
('SM02', '商管二处', 'RETAIL', NULL, '21:30', 2),
('SM03', '商管三处', 'FOOD',   NULL, '21:30', 3),
('SM04', '商管四处', 'FOOD',   NULL, '21:30', 4);

-- ---------------------------------------------------------------- 时段种子
INSERT IGNORE INTO ops_patrol_slot
(code, name, relative_to_close, start_time, end_time, offset_start_min, offset_end_min, is_handover,
 push_advance_minutes, urge_after_minutes, remind_before_end_minutes, overdue_after_minutes, sort_order) VALUES
('S1', '午市巡查',   0, '12:00', '13:00', NULL, NULL, 0, 15, 10, 10, 30, 1),
('S2', '午后巡查',   0, '13:00', '14:00', NULL, NULL, 0, 15, 10, 10, 30, 2),
('S3', '班次交接',   0, '14:00', '14:30', NULL, NULL, 1, 10, 10, 10, 30, 3),
('S4', '晚市巡查',   0, '19:00', '20:30', NULL, NULL, 0, 15, 15, 10, 30, 4),
('S5', '闭店前巡查', 0, '20:30', '21:10', NULL, NULL, 0, 15, 10, 10, 30, 5),
('S6', '闭店巡查',   0, '21:10', '21:30', NULL, NULL, 0, 15, 10, 5,  30, 6),
('S7', '清场之后',   1, NULL, NULL, 0, 90, 0, 0, 30, 15, 60, 7);

-- ---------------------------------------------------------------- 检查项种子（餐饮 32 项）
INSERT IGNORE INTO ops_patrol_check_item (code, form_type, group_name, content, photo_required, category, due_hours, urgency, sort_order) VALUES
('F01','FOOD','秩序服务','客座区秩序良好，无占座、通道畅通',0,'秩序安全',24,'NORMAL',1),
('F02','FOOD','秩序服务','等位服务规范（叫号、等位区秩序、饮品供应）',0,'秩序安全',24,'NORMAL',2),
('F03','FOOD','秩序服务','前厅门迎状态（迎宾人员在位、仪容规范）',0,'秩序安全',24,'NORMAL',3),
('F04','FOOD','秩序服务','无提前收客、驱赶顾客现象',0,'商户管理',24,'NORMAL',4),
('F05','FOOD','秩序服务','收餐及时、翻台规范',0,'商户管理',24,'NORMAL',5),
('F06','FOOD','秩序服务','等位区卫生良好',0,'环境卫生',24,'NORMAL',6),
('F07','FOOD','食安后厨','后厨操作规范（生熟分开、留样、操作台整洁）',1,'环境卫生',24,'NORMAL',7),
('F08','FOOD','食安后厨','餐具消毒到位（消毒柜运行、餐具周转）',0,'环境卫生',24,'NORMAL',8),
('F09','FOOD','食安后厨','健康证在有效期内（抽查不少于 3 人）',1,'商户管理',48,'NORMAL',9),
('F10','FOOD','食安后厨','食材台账齐全（进货票据、保质期）',1,'商户管理',48,'NORMAL',10),
('F11','FOOD','食安后厨','后厨停止粗加工、食材封存',1,'环境卫生',12,'NORMAL',11),
('F12','FOOD','食安后厨','隔夜食材储存合规（冷藏、密封、标识）',1,'环境卫生',12,'NORMAL',12),
('F13','FOOD','环境设备','店铺基础营运卫生（地面、桌面、餐台）',0,'环境卫生',24,'NORMAL',13),
('F14','FOOD','环境设备','厨余垃圾规范存放（加盖、不外溢）',0,'环境卫生',24,'NORMAL',14),
('F15','FOOD','环境设备','厨余垃圾清运完成',0,'环境卫生',12,'NORMAL',15),
('F16','FOOD','环境设备','油烟设备运行正常、无明显油渍堆积',0,'设施设备',24,'NORMAL',16),
('F17','FOOD','环境设备','燃气、油烟设备安全巡检（报警器、阀门、线路）',1,'消防安全',8,'URGENT',17),
('F18','FOOD','环境设备','异味管控到位（排烟、下水返味）',0,'环境卫生',24,'NORMAL',18),
('F19','FOOD','环境设备','后勤通道卫生整洁、无堆放',0,'环境卫生',24,'NORMAL',19),
('F20','FOOD','环境设备','上货物品整理堆放规范',0,'秩序安全',24,'NORMAL',20),
('F21','FOOD','环境设备','餐后工具清洗到位',0,'环境卫生',24,'NORMAL',21),
('F22','FOOD','环境设备','下水道、隔油池清理完成',1,'设施设备',24,'NORMAL',22),
('F23','FOOD','安全消防','消防通道畅通、无堵塞',1,'消防安全',4,'URGENT',23),
('F24','FOOD','安全消防','消防安全专项：燃气总阀关闭、灭火器有效、通道畅通',1,'消防安全',4,'URGENT',24),
('F25','FOOD','安全消防','燃气、非必要设备电源预检查',0,'消防安全',8,'URGENT',25),
('F26','FOOD','营运管理','菜品、货品丰满度达标',0,'商户管理',24,'NORMAL',26),
('F27','FOOD','营运管理','门店业绩跟进（当日营收、目标达成）',0,'商户管理',24,'NORMAL',27),
('F28','FOOD','营运管理','上一时段问题复查（中午问题整改）',0,'商户管理',24,'NORMAL',28),
('F29','FOOD','营运管理','提前闭店情况核查',0,'商户管理',12,'NORMAL',29),
('F30','FOOD','营运管理','拟定次日早会内容',0,'商户管理',24,'NORMAL',30),
('F31','FOOD','营运管理','施工商户晚间清洁落地核查',1,'环境卫生',12,'NORMAL',31),
('F32','FOOD','营运管理','店铺加班情况反馈',0,'商户管理',24,'NORMAL',32);

-- 检查项种子（零售 28 项）
INSERT IGNORE INTO ops_patrol_check_item (code, form_type, group_name, content, photo_required, category, due_hours, urgency, sort_order) VALUES
('R01','RETAIL','人员服务','员工在岗率达标',0,'商户管理',24,'NORMAL',1),
('R02','RETAIL','人员服务','员工在岗状态规范（着工装、无脱岗、不扎堆玩手机）',0,'商户管理',24,'NORMAL',2),
('R03','RETAIL','人员服务','店铺门迎到位',0,'商户管理',24,'NORMAL',3),
('R04','RETAIL','人员服务','服务规范：接一待二、主动服务意识',0,'商户管理',24,'NORMAL',4),
('R05','RETAIL','仓库货品','仓库基础运营规范（分区、标识、通道畅通）',0,'商户管理',24,'NORMAL',5),
('R06','RETAIL','仓库货品','货品堆放规范（限高、离地、分类）',1,'商户管理',24,'NORMAL',6),
('R07','RETAIL','仓库货品','缺货及时补货、货架丰满',0,'商户管理',24,'NORMAL',7),
('R08','RETAIL','仓库货品','仓库门口无占道堆货',1,'秩序安全',24,'NORMAL',8),
('R09','RETAIL','仓库货品','贵重商品归位收纳',0,'商户管理',12,'NORMAL',9),
('R10','RETAIL','陈列企划','高峰后陈列复位整理',0,'商户管理',24,'NORMAL',10),
('R11','RETAIL','陈列企划','企划物料完好、无破损',0,'商户管理',24,'NORMAL',11),
('R12','RETAIL','陈列企划','公区运营跟进（卫生间、休息区、导视）',0,'环境卫生',24,'NORMAL',12),
('R13','RETAIL','陈列企划','试衣间、卫生间巡检',0,'环境卫生',24,'NORMAL',13),
('R14','RETAIL','安全施工','施工区域安全检查（围挡、用电、动火）',1,'秩序安全',8,'URGENT',14),
('R15','RETAIL','安全施工','施工商户晚间清洁工作核查',1,'环境卫生',12,'NORMAL',15),
('R16','RETAIL','安全施工','消防通道畅通、水晶卷帘下放',1,'消防安全',4,'URGENT',16),
('R17','RETAIL','营运闭店','后勤通道运营检查',0,'秩序安全',24,'NORMAL',17),
('R18','RETAIL','营运闭店','门店业绩跟进',0,'商户管理',24,'NORMAL',18),
('R19','RETAIL','营运闭店','上一时段问题复查（中午问题）',0,'商户管理',24,'NORMAL',19),
('R20','RETAIL','营运闭店','POS 机现金提醒、铺机到位',0,'设施设备',12,'NORMAL',20),
('R21','RETAIL','营运闭店','无提前打扫卫生、提前清场现象',1,'商户管理',12,'NORMAL',21),
('R22','RETAIL','营运闭店','提前闭店情况检查',0,'商户管理',12,'NORMAL',22),
('R23','RETAIL','营运闭店','拟定次日早会内容',0,'商户管理',24,'NORMAL',23),
('R24','RETAIL','营运闭店','POS 机审核、晚清图片审核',1,'设施设备',12,'NORMAL',24),
('R25','RETAIL','营运闭店','未闭店商户标准告知',0,'商户管理',12,'NORMAL',25),
('R26','RETAIL','营运闭店','店铺加班情况反馈',0,'商户管理',24,'NORMAL',26),
('R27','RETAIL','营运闭店','确认店内无滞留人员',1,'秩序安全',4,'URGENT',27),
('R28','RETAIL','营运闭店','消防通道畅通、无堵塞',1,'消防安全',4,'URGENT',28);

-- ---------------------------------------------------------------- 检查项 × 时段关联
INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S1'
WHERE i.code IN ('F01','F02','F03','F07','F08','F13','F14','F16','R01','R02','R05','R06','R07','R14');

INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S2'
WHERE i.code IN ('F09','F10','F19','F20','F21','F23','R03','R08','R10','R11','R17','R28');

INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S4'
WHERE i.code IN ('F01','F02','F13','F17','F18','F26','R02','R04','R12','R18');

INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S5'
WHERE i.code IN ('F04','F05','F06','F27','F28','R13','R19','R20');

INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S6'
WHERE i.code IN ('F11','F15','F25','F29','F30','R09','R21','R22','R23');

INSERT IGNORE INTO ops_patrol_item_slot (item_id, slot_id)
SELECT i.id, s.id FROM ops_patrol_check_item i JOIN ops_patrol_slot s ON s.code = 'S7'
WHERE i.code IN ('F12','F22','F24','F31','F32','R15','R16','R24','R25','R26','R27');
