CREATE TABLE energy_usage_sync_exclusion (
  platform_device_id VARCHAR(64) PRIMARY KEY,
  device_name VARCHAR(255),
  reason VARCHAR(255),
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_euse_enabled (enabled,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用量同步设备过滤名单';

ALTER TABLE energy_usage_sync_batch
  ADD COLUMN excluded_devices INT NOT NULL DEFAULT 0 AFTER requested;
