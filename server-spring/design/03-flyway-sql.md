# 第一阶段 Flyway SQL 草稿

> 命名规范：`Vn__<scope>.sql`。使用 `InnoDB`，`utf8mb4_unicode_ci`，主键 `BIGINT AUTO_INCREMENT`，时间字段 `DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)`。
> 所有时间字段由 MyBatis-Plus `@TableField(fill=...)` 自动填充，应用层不必赋值。

## V1__init_system.sql — 系统 + RBAC + 审计 + 初始数据

```sql
-- 用户
CREATE TABLE sys_user (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  username        VARCHAR(64)  NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,        -- BCrypt
  display_name    VARCHAR(64),
  email           VARCHAR(128),
  phone           VARCHAR(32),
  status          TINYINT      NOT NULL DEFAULT 1, -- 1=enabled 0=disabled
  last_login_at   DATETIME(3),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB;

-- 角色
CREATE TABLE sys_role (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(64)  NOT NULL,        -- SUPER_ADMIN / SYSTEM_ADMIN / ...
  name            VARCHAR(64)  NOT NULL,
  description     VARCHAR(255),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB;

-- 用户-角色
CREATE TABLE sys_user_role (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  role_id         BIGINT NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user_role_user (user_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB;

-- 菜单
CREATE TABLE sys_menu (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id       BIGINT       NOT NULL DEFAULT 0,
  code            VARCHAR(128) NOT NULL,        -- platform:config
  name            VARCHAR(64)  NOT NULL,
  path            VARCHAR(255),
  icon            VARCHAR(64),
  type            VARCHAR(16)  NOT NULL,        -- MENU / BUTTON
  sort_order      INT          NOT NULL DEFAULT 0,
  enabled         TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_menu_code (code),
  KEY idx_sys_menu_parent (parent_id)
) ENGINE=InnoDB;

CREATE TABLE sys_role_menu (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id         BIGINT NOT NULL,
  menu_id         BIGINT NOT NULL,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB;

-- 审计日志
CREATE TABLE audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator        VARCHAR(64),
  operator_id     BIGINT,
  operation       VARCHAR(64)  NOT NULL,        -- LOGIN / CONFIG_UPDATE / WORKFLOW_SUBMIT / ...
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
) ENGINE=InnoDB;

-- 系统配置
CREATE TABLE system_config (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key      VARCHAR(64)  NOT NULL,
  config_value    TEXT,
  description     VARCHAR(255),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB;

-- 初始数据：默认管理员
INSERT INTO sys_user (username, password_hash, display_name, status)
VALUES ('admin', '$2a$10$<BCrypt for admin123>', '系统管理员', 1);
-- 实际部署时由 init 脚本生成 BCrypt 哈希替换占位符

INSERT INTO sys_role (code, name, description) VALUES
  ('SUPER_ADMIN', '超级管理员', '拥有所有权限'),
  ('SYSTEM_ADMIN', '系统管理员', '平台配置、用户管理'),
  ('APPLICANT', '申请人', '创建流程、查看自己流程'),
  ('DEVICE_VERIFIER', '设备核验员', '现场核验'),
  ('PLATFORM_OPERATOR', '平台操作员', '源平台人工操作'),
  ('AUDITOR', '审计员', '只读全部'),
  ('VIEWER', '只读用户', '只读');

-- 初始菜单（按文档第 45 节）
-- 略，见后续 init-data.sql

-- 默认配置
INSERT INTO system_config (config_key, config_value, description) VALUES
  ('energy.daily.retention_days', '90', '日能耗保留天数'),
  ('platform.sync.default_cron', '0 0 2 * * ?', '默认同步 cron (02:00)'),
  ('webhook.retry.max', '3', 'Webhook 最大重试次数');
```

## V2__platform_tables.sql — 源平台配置 / 同步 / 镜像（核心，重点评审）

```sql
CREATE TABLE platform_config (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_name            VARCHAR(64)  NOT NULL DEFAULT 'LeoEnergy',
  base_url                 VARCHAR(255) NOT NULL,
  account_username         VARCHAR(64)  NOT NULL,
  password_ciphertext      TEXT         NOT NULL,    -- AES-256-GCM 密文（base64）
  password_iv              VARCHAR(64)  NOT NULL,    -- GCM IV（base64，12 字节）
  space_id                 VARCHAR(32)  NOT NULL,    -- 单项目 = 4801
  enabled                  TINYINT      NOT NULL DEFAULT 0,
  auto_sync                TINYINT      NOT NULL DEFAULT 0,
  sync_cron                VARCHAR(64)  NOT NULL DEFAULT '0 0 2 * * ?',
  connect_timeout_ms       INT          NOT NULL DEFAULT 10000,
  last_connect_at          DATETIME(3),
  last_connect_status      VARCHAR(32),
  last_sync_at             DATETIME(3),
  last_sync_status         VARCHAR(32),
  last_sync_batch_id       BIGINT,
  remark                   VARCHAR(255),
  created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE platform_sync_batch (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no            VARCHAR(32)  NOT NULL,         -- SYNC202608140001
  started_at          DATETIME(3)  NOT NULL,
  completed_at        DATETIME(3),
  status              VARCHAR(32)  NOT NULL,         -- RUNNING / SUCCESS / FAILED / PARTIAL_FAILED
  account_count       INT          NOT NULL DEFAULT 0,
  device_count        INT          NOT NULL DEFAULT 0,
  relation_count      INT          NOT NULL DEFAULT 0,
  error_message       TEXT,
  trigger_type        VARCHAR(32)  NOT NULL,         -- AUTO / MANUAL
  trigger_user_id     BIGINT,
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_batch_no (batch_no),
  KEY idx_batch_status (status),
  KEY idx_batch_started (started_at)
) ENGINE=InnoDB;

CREATE TABLE platform_account_current (
  id                          BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_account_id         VARCHAR(64)  NOT NULL,    -- 源平台 ID（字符串）
  account_number              VARCHAR(64)  NOT NULL,    -- 计费账户编号（字符串！避免大整数）
  account_name                VARCHAR(255),
  merchant_code               VARCHAR(6),                -- 6 位字符串
  merchant_code_source        VARCHAR(16),               -- AUTO_MATCHED / MANUAL_CONFIRMED / AMBIGUOUS / NOT_FOUND
  merchant_code_match_status  VARCHAR(16),
  user_name                   VARCHAR(64),
  contact_name                VARCHAR(64),
  mobile_phone                VARCHAR(32),
  balance                     DECIMAL(18,4),
  arrears                     DECIMAL(18,4),
  platform_status             VARCHAR(32),
  source_space_id             VARCHAR(32),
  sync_batch_id               BIGINT       NOT NULL,
  last_synced_at              DATETIME(3)  NOT NULL,
  raw_json                    JSON,
  created_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pac_account (account_number),
  KEY idx_pac_platform_id (platform_account_id),
  KEY idx_pac_merchant (merchant_code),
  KEY idx_pac_batch (sync_batch_id)
) ENGINE=InnoDB;

CREATE TABLE platform_device_current (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id       VARCHAR(64)  NOT NULL,
  device_name              VARCHAR(255),
  comm_address             VARCHAR(64),
  serial_number            VARCHAR(128),
  gateway_id               VARCHAR(64),
  gateway_name             VARCHAR(128),
  channel_id               VARCHAR(64),
  channel_name             VARCHAR(128),
  product_id               VARCHAR(64),
  product_name             VARCHAR(128),
  energy_type              VARCHAR(16),                -- WATER / ELECTRIC / GAS / ...
  energy_type_name         VARCHAR(64),
  device_system            VARCHAR(32),
  device_system_name       VARCHAR(64),
  comm_status              TINYINT,
  last_acq_time            DATETIME(3),
  sync_batch_id            BIGINT       NOT NULL,
  last_synced_at           DATETIME(3)  NOT NULL,
  raw_json                 JSON,
  created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pdc_device (platform_device_id),
  KEY idx_pdc_comm (comm_address),
  KEY idx_pdc_serial (serial_number),
  KEY idx_pdc_energy (energy_type)
) ENGINE=InnoDB;

CREATE TABLE platform_account_device_current (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_number           VARCHAR(64)  NOT NULL,
  platform_device_id       VARCHAR(64)  NOT NULL,
  sync_batch_id            BIGINT       NOT NULL,
  last_synced_at           DATETIME(3)  NOT NULL,
  created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_padc (account_number, platform_device_id),
  KEY idx_padc_device (platform_device_id),
  KEY idx_padc_batch (sync_batch_id)
) ENGINE=InnoDB;

-- 3 张快照表
CREATE TABLE platform_account_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,             -- 完整账户数组
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_pas_date (snapshot_date)
) ENGINE=InnoDB;

CREATE TABLE platform_device_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_pds_date (snapshot_date)
) ENGINE=InnoDB;

CREATE TABLE platform_account_device_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_pads_date (snapshot_date)
) ENGINE=InnoDB;
```

> V3-V8 概要（详细 SQL 在编码阶段展开；保留前向兼容，不破坏 V1-V2 已上线内容）：

## V3__stall.sql — 铺位 + 商户
- `merchant(id, merchant_code VARCHAR(6) UNIQUE, merchant_code_source, merchant_code_match_status, merchant_name, contact_name, contact_phone, source_platform_account_id)`
- `stall(id, stall_code VARCHAR(32) UNIQUE, stall_name, floor_code, floor_name, area_name, area, business_type, business_category, status, remark)`
- `stall_device(id, stall_id, platform_device_id, effective_from, effective_to, status, created_by, remark)`

## V4__workflow.sql — 流程
- `workflow`（含 status 枚举、expected_account_action、extra_data JSON）
- `workflow_device`（含 BIND/UNBIND/KEEP 快照字段）
- `workflow_expected_change`（含 verify_status）

## V5__verification.sql — 核验
- `field_verification_task`（含随机 token，token_expire_at）
- `field_verification_device`
- `attachment`（含 photo_type 枚举，不存 BLOB）

## V6__notification.sql — 通知
- `webhook_config`（url_ciphertext, secret_ciphertext 同 AES-GCM 加密）
- `webhook_log`

## V7__reconciliation.sql — 异常
- `data_exception`（severity 枚举、status 枚举）

## V8__energy.sql — 能耗
- `energy_daily`（唯一键 device_id+identifier+stat_date）
- `energy_monthly`
- `energy_anomaly_rule`（parameters_json）
- `energy_anomaly`
- `device_dimension*` 三表
