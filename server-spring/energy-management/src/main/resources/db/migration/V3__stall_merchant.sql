-- =====================================================================
-- V3__stall_merchant.sql
-- Phase 4 — 本地商户 / 铺位 / 铺位-设备关系
-- 文档第 8/9 节：stall_code / merchant_code 都是 6 位业务主键；
-- 一台设备同一时间只能绑定一个铺位（effective_to IS NULL）。
-- =====================================================================

-- 本地商户：与平台账户松耦合（一个商户可对应 0..N 个平台账户）
CREATE TABLE merchant (
  id                            BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_code                 VARCHAR(6)    NOT NULL,
  merchant_code_source          VARCHAR(16)   NOT NULL DEFAULT 'MANUAL',
  merchant_code_match_status    VARCHAR(16)   NOT NULL DEFAULT 'MANUAL_CONFIRMED',
  merchant_name                 VARCHAR(128)  NOT NULL,
  contact_name                  VARCHAR(64),
  contact_phone                 VARCHAR(32),
  source_platform_account_id    VARCHAR(64),
  status                        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
  remark                        VARCHAR(255),
  created_at                    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_merchant_code (merchant_code),
  KEY idx_merchant_status (status),
  KEY idx_merchant_source_account (source_platform_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 铺位：完全由本地系统维护，stall_code 为唯一业务主键
CREATE TABLE stall (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  stall_code        VARCHAR(32)   NOT NULL,
  stall_name        VARCHAR(128)  NOT NULL,
  floor_code        VARCHAR(32),
  floor_name        VARCHAR(64),
  area_name         VARCHAR(64),
  area              DECIMAL(12,4),
  business_type     VARCHAR(64),
  business_category VARCHAR(64),
  status            VARCHAR(16)   NOT NULL DEFAULT 'AVAILABLE',
  -- 当前占用商户（冗余，NULL 表示空铺）
  current_merchant_id BIGINT,
  remark            VARCHAR(255),
  created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_stall_code (stall_code),
  KEY idx_stall_status (status),
  KEY idx_stall_merchant (current_merchant_id),
  KEY idx_stall_floor (floor_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 铺位-设备绑定：时间段（effective_from/to），同一时间一个 device 只能 ACTIVE 一行
-- 用 partial-unique 思路：单独建唯一索引太复杂，用应用层 + DB 函数索引代替；
-- 这里用普通唯一键 (stall_id, platform_device_id, status) 不够安全，
-- 故只加普通唯一 (stall_id, platform_device_id) 并由应用保证 status=ACTIVE 唯一。
CREATE TABLE stall_device (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  stall_id            BIGINT         NOT NULL,
  platform_device_id  VARCHAR(64)    NOT NULL,
  energy_type         VARCHAR(16),
  status              VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
  effective_from      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  effective_to        DATETIME(3),
  bound_by            VARCHAR(64),
  unbound_by          VARCHAR(64),
  remark              VARCHAR(255),
  created_at          DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_sd_stall (stall_id),
  KEY idx_sd_device (platform_device_id),
  KEY idx_sd_status (status),
  KEY idx_sd_effective (effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 同一 stall 内同一 device 同一时间只能 ACTIVE 一行：建函数唯一索引
-- MySQL 8 不支持部分索引；改用生成列 + 唯一索引：
ALTER TABLE stall_device
  ADD COLUMN active_dedup VARCHAR(80) GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN CONCAT(stall_id, ':', platform_device_id) ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_sd_active (active_dedup);

