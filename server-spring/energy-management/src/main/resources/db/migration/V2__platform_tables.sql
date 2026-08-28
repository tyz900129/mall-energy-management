-- =====================================================================
-- V2__platform_tables.sql
-- Phase 2 — 源平台配置 / 同步批次 / 当前镜像 / 历史快照
-- 仅追加（idempotent INSERT / CREATE）。所有外部字符串 ID 用 VARCHAR 避免精度丢失。
-- =====================================================================

-- 单行配置（id=1）—— 故意不带 UNIQUE 让管理员能在破坏时手动修
CREATE TABLE platform_config (
  id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_name            VARCHAR(64)  NOT NULL DEFAULT 'LeoEnergy',
  base_url                 VARCHAR(255) NOT NULL,
  account_username         VARCHAR(64)  NOT NULL,
  password_ciphertext      TEXT         NOT NULL,
  password_iv              VARCHAR(64)  NOT NULL,
  space_id                 VARCHAR(32)  NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_sync_batch (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no            VARCHAR(32)  NOT NULL,
  started_at          DATETIME(3)  NOT NULL,
  completed_at        DATETIME(3),
  status              VARCHAR(32)  NOT NULL,
  account_count       INT          NOT NULL DEFAULT 0,
  device_count        INT          NOT NULL DEFAULT 0,
  relation_count      INT          NOT NULL DEFAULT 0,
  error_message       TEXT,
  trigger_type        VARCHAR(32)  NOT NULL,
  trigger_user_id     BIGINT,
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_batch_no (batch_no),
  KEY idx_batch_status (status),
  KEY idx_batch_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_account_current (
  id                          BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_account_id         VARCHAR(64)  NOT NULL,
  account_number              VARCHAR(64)  NOT NULL,
  account_name                VARCHAR(255),
  merchant_code               VARCHAR(6),
  merchant_code_source        VARCHAR(16),
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  energy_type              VARCHAR(16),
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3 张快照表（按天；snapshot_date 唯一 → UPSERT 语义）
CREATE TABLE platform_account_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pas_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_device_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pds_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_account_device_snapshot (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_date DATE         NOT NULL,
  sync_batch_id BIGINT       NOT NULL,
  payload       JSON         NOT NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_pads_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始化 platform_config 占位（id=1）；password_ciphertext/iv 启动时由
-- PlatformConfigService.ensureExists() 写占位。Flyway 仅保证表存在。
INSERT INTO platform_config (id, platform_name, base_url, account_username, password_ciphertext, password_iv, space_id, enabled, sync_cron, connect_timeout_ms, remark)
VALUES (1, 'LeoEnergy', 'https://app.leotechnology.cn', 'w3066', '__PENDING__', '__PENDING__', '4801', 0, '0 0 2 * * ?', 10000, 'Initialized by Flyway; password to be set via /api/v1/platform/config');

-- 同步日志菜单（sys_menu）—— 让 SYSTEM_ADMIN 能看到同步日志
-- MySQL 禁止 INSERT 的 VALUES 中对同一张表做 SELECT，用派生表 (SELECT id FROM (SELECT id ...) AS t) 强制物化
INSERT INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, 'platform:sync:log', '同步日志详情', '/platform/sync/log', 'sync-log', 'MENU', 3, 1
FROM (SELECT id FROM sys_menu WHERE code='platform') AS t;

-- 分布式锁占位表：单行 id=1；通过 SELECT ... FOR UPDATE 实现同步互斥
CREATE TABLE IF NOT EXISTS sync_lock (
  id           BIGINT PRIMARY KEY,
  holder       VARCHAR(64),
  acquired_at  DATETIME(3),
  note         VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sync_lock (id, holder) VALUES (1, NULL)
  ON DUPLICATE KEY UPDATE id = id;

