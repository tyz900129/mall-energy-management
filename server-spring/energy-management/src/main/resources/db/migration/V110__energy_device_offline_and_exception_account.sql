-- V110: 能源管理 —— 设备离线告警 + 平台异常账户通知

-- ============ 设备离线告警 ============

-- 单例配置表（仅一行 id=1）
CREATE TABLE IF NOT EXISTS energy_device_monitor_setting (
  id BIGINT PRIMARY KEY DEFAULT 1,
  enabled TINYINT NOT NULL DEFAULT 1,
  check_interval_minutes INT NOT NULL DEFAULT 120 COMMENT '巡检间隔（分钟）',
  consecutive_count INT NOT NULL DEFAULT 2 COMMENT '连续离线次数才告警',
  offline_threshold_minutes INT NOT NULL DEFAULT 30 COMMENT 'last_acq_time 超时阈值（分钟）',
  quiet_start TIME NULL COMMENT '静默时段开始（含）',
  quiet_end TIME NULL COMMENT '静默时段结束（不含）',
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT chk_setting_id CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO energy_device_monitor_setting (id) VALUES (1)
  ON DUPLICATE KEY UPDATE id = id;

-- 不监控清单（维护停用 / 已拆除 / 其他）
CREATE TABLE IF NOT EXISTS energy_device_offline_exclude (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id VARCHAR(128) NOT NULL COMMENT '平台设备ID',
  device_name VARCHAR(255) NULL,
  reason VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT 'MAINTENANCE/DECOMMISSIONED/OTHER',
  remark VARCHAR(255) NULL,
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_device (platform_device_id),
  KEY idx_exclude_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 告警事件（网关 / 端口 / 设备三级）
CREATE TABLE IF NOT EXISTS energy_device_alert_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  level VARCHAR(16) NOT NULL COMMENT 'GATEWAY/PORT/DEVICE',
  gateway_id VARCHAR(128) NULL,
  gateway_name VARCHAR(255) NULL,
  channel_id VARCHAR(128) NULL,
  channel_name VARCHAR(255) NULL,
  platform_device_id VARCHAR(128) NULL,
  device_name VARCHAR(255) NULL,
  scope_key VARCHAR(255) NOT NULL COMMENT '层级去重键（网关ID / 网关:端口 / 设备ID）',
  open_scope_key VARCHAR(255) NULL COMMENT '未恢复时的去重键，恢复后置 NULL',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RECOVERED',
  first_offline_at DATETIME(3) NOT NULL,
  last_acq_time DATETIME(3) NULL,
  consecutive_count INT NOT NULL DEFAULT 1,
  affected_count INT NOT NULL DEFAULT 1 COMMENT '受影响设备数',
  notified_at DATETIME(3) NULL,
  recovered_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_open_scope (level, open_scope_key),
  KEY idx_event_status (status),
  KEY idx_event_scope (level, scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 巡检运行记录
CREATE TABLE IF NOT EXISTS energy_device_alert_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  scanned_count INT NOT NULL DEFAULT 0,
  alerted_count INT NOT NULL DEFAULT 0,
  recovered_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_run_started (started_at),
  KEY idx_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ 平台异常账户通知 ============

-- 单例配置表（仅一行 id=1）
CREATE TABLE IF NOT EXISTS energy_exception_monitor_setting (
  id BIGINT PRIMARY KEY DEFAULT 1,
  enabled TINYINT NOT NULL DEFAULT 1,
  check_interval_minutes INT NOT NULL DEFAULT 120 COMMENT '巡检间隔（分钟）',
  quiet_start TIME NULL,
  quiet_end TIME NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT chk_exception_setting_id CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO energy_exception_monitor_setting (id) VALUES (1)
  ON DUPLICATE KEY UPDATE id = id;

-- 异常账户快照（来自平台 type=exception）
CREATE TABLE IF NOT EXISTS energy_exception_account (
  account_number VARCHAR(128) PRIMARY KEY,
  platform_account_id VARCHAR(128) NULL,
  account_name VARCHAR(255) NULL,
  user_name VARCHAR(255) NULL,
  mobile_phone VARCHAR(64) NULL,
  balance DECIMAL(18,4) NULL,
  exception_type VARCHAR(64) NULL,
  active TINYINT NOT NULL DEFAULT 1,
  excluded TINYINT NOT NULL DEFAULT 0,
  exclusion_remark VARCHAR(255) NULL,
  first_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at DATETIME(3) NULL,
  notified TINYINT NOT NULL DEFAULT 0,
  recovered_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_exception_active_excluded (active, excluded),
  KEY idx_exception_name (account_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 同步运行记录
CREATE TABLE IF NOT EXISTS energy_exception_sync_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  fetched_count INT NOT NULL DEFAULT 0,
  excluded_count INT NOT NULL DEFAULT 0,
  notified_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_exc_run_started (started_at),
  KEY idx_exc_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============ 权限码 ============

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('energy.offline.view','查看设备离线告警','energy','能源管理','VIEW',515),
('energy.offline.manage','维护设备离线告警规则与不监控清单','energy','能源管理','MANAGE',516),
('energy.exception.view','查看平台异常账户通知','energy','能源管理','VIEW',517),
('energy.exception.manage','同步异常账户及设置不推送账户','energy','能源管理','MANAGE',518);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
  AND p.code IN ('energy.offline.view','energy.offline.manage','energy.exception.view','energy.exception.manage');
