-- =====================================================================
-- V1__init_system.sql
-- Phase 1 system / RBAC / audit tables + initial data.
-- The default admin user is created with a placeholder hash; the
-- AdminBootstrap runner re-hashes it on first boot using the configured
-- default password (env ENERGY_DEFAULT_ADMIN_PASSWORD, default admin123).
-- =====================================================================

-- 用户
CREATE TABLE sys_user (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  username        VARCHAR(64)  NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(64),
  email           VARCHAR(128),
  phone           VARCHAR(32),
  status          TINYINT      NOT NULL DEFAULT 1,
  last_login_at   DATETIME(3),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色
CREATE TABLE sys_role (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  description     VARCHAR(255),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户-角色
CREATE TABLE sys_user_role (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  role_id         BIGINT NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user_role_user (user_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 菜单
CREATE TABLE sys_menu (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id       BIGINT       NOT NULL DEFAULT 0,
  code            VARCHAR(128) NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  path            VARCHAR(255),
  icon            VARCHAR(64),
  type            VARCHAR(16)  NOT NULL,
  sort_order      INT          NOT NULL DEFAULT 0,
  enabled         TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_menu_code (code),
  KEY idx_sys_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role_menu (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id         BIGINT NOT NULL,
  menu_id         BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审计日志
CREATE TABLE audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator        VARCHAR(64),
  operator_id     BIGINT,
  operation       VARCHAR(64)  NOT NULL,
  business_type   VARCHAR(64),
  business_id     VARCHAR(64),
  before_data     JSON,
  after_data      JSON,
  ip              VARCHAR(64),
  user_agent      VARCHAR(512),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_audit_operator (operator),
  KEY idx_audit_business (business_type, business_id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 系统配置
CREATE TABLE system_config (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key      VARCHAR(64)  NOT NULL,
  config_value    TEXT,
  description     VARCHAR(255),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 初始数据
-- =====================================================================

-- 默认 admin 用户（密码占位符，启动时 AdminBootstrap 会重新 BCrypt）
INSERT INTO sys_user (username, password_hash, display_name, status)
VALUES ('admin', '$2a$10$__BOOTSTRAP__PLACEHOLDER______________________________', '系统管理员', 1);

-- 角色
INSERT INTO sys_role (code, name, description) VALUES
  ('SUPER_ADMIN',      '超级管理员',     '拥有所有权限'),
  ('SYSTEM_ADMIN',     '系统管理员',     '平台配置、用户管理'),
  ('APPLICANT',        '申请人',         '创建流程、查看自己流程'),
  ('DEVICE_VERIFIER',  '设备核验员',     '现场核验'),
  ('PLATFORM_OPERATOR','平台操作员',     '源平台人工操作'),
  ('AUDITOR',          '审计员',         '只读全部'),
  ('VIEWER',           '只读用户',       '只读');

-- admin -> SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.code = 'SUPER_ADMIN';

-- 一级菜单
INSERT INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled) VALUES
  (0, 'dashboard',         '工作台',       '/dashboard',         'dashboard',    'MENU', 1, 1),
  (0, 'system',            '系统管理',     '/system',            'system',       'MENU', 90, 1),
  (0, 'platform',          '源平台',       '/platform',          'platform',     'MENU', 80, 1),
  (0, 'stall',             '铺位管理',     '/stall',             'stall',        'MENU', 70, 1),
  (0, 'workflow',          '流程中心',     '/workflow',          'workflow',     'MENU', 60, 1),
  (0, 'verification',      '现场核验',     '/verification',      'verification', 'MENU', 50, 1),
  (0, 'reconciliation',    '数据对账',     '/reconciliation',    'reconcile',    'MENU', 40, 1),
  (0, 'analysis',          '能耗分析',     '/analysis',          'analysis',     'MENU', 30, 1);

-- 二级菜单：系统管理
-- 注意：MySQL 禁止在 INSERT 的 VALUES 中对同一张表做 SELECT，
-- 通过派生表 (SELECT ... FROM (SELECT ...) AS t) 强制物化以绕过限制
INSERT INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, m.code, m.name, m.path, m.icon, m.type, m.sort_order, m.enabled
FROM (
  SELECT 'system:user'   AS code, '用户管理' AS name, '/system/user'   AS path, 'user'   AS icon, 'MENU' AS type, 1 AS sort_order, 1 AS enabled UNION ALL
  SELECT 'system:role',         '角色管理',   '/system/role',   'role',     'MENU', 2, 1 UNION ALL
  SELECT 'system:menu',         '菜单管理',   '/system/menu',   'menu',     'MENU', 3, 1 UNION ALL
  SELECT 'system:config',       '系统配置',   '/system/config', 'config',   'MENU', 4, 1 UNION ALL
  SELECT 'system:audit',        '审计日志',   '/system/audit',  'audit',    'MENU', 5, 1
) m
CROSS JOIN (SELECT id FROM sys_menu WHERE code='system') t;

-- 二级菜单：源平台
INSERT INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, m.code, m.name, m.path, m.icon, m.type, m.sort_order, m.enabled
FROM (
  SELECT 'platform:config' AS code, '源平台配置' AS name, '/platform/config' AS path, 'platform' AS icon, 'MENU' AS type, 1 AS sort_order, 1 AS enabled UNION ALL
  SELECT 'platform:sync',         '同步日志',         '/platform/sync',         'sync',     'MENU', 2, 1
) m
CROSS JOIN (SELECT id FROM sys_menu WHERE code='platform') t;

-- SUPER_ADMIN 拥有所有菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m WHERE r.code = 'SUPER_ADMIN';

-- SYSTEM_ADMIN：系统管理 + 源平台配置
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m
WHERE r.code = 'SYSTEM_ADMIN'
  AND m.code IN ('dashboard','system','system:user','system:role','system:menu','system:config','system:audit',
                 'platform','platform:config','platform:sync');

-- AUDITOR：只读全部
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m
WHERE r.code = 'AUDITOR'
  AND m.code IN ('dashboard','platform','platform:sync','stall','workflow','verification','reconciliation','analysis');

-- VIEWER：仅工作台
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m
WHERE r.code = 'VIEWER' AND m.code = 'dashboard';

-- 系统配置初始值
INSERT INTO system_config (config_key, config_value, description) VALUES
  ('energy.daily.retention_days', '90',         '日能耗保留天数'),
  ('platform.sync.default_cron',  '0 0 2 * * ?','默认同步 cron (02:00)'),
  ('webhook.retry.max',           '3',          'Webhook 最大重试次数'),
  ('security.jwt.expire-minutes', '720',        'JWT 过期分钟数');

