CREATE TABLE energy_usage_monthly (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id VARCHAR(64) NOT NULL,
  usage_month DATE NOT NULL,
  energy_type VARCHAR(16) NOT NULL,
  identifier VARCHAR(64) NOT NULL,
  identifier_name VARCHAR(128),
  usage_value DECIMAL(20,6),
  sync_batch_id BIGINT NOT NULL,
  synced_at DATETIME(3) NOT NULL,
  raw_json JSON,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_eum_device_month_metric (platform_device_id,usage_month,identifier),
  KEY idx_eum_month (usage_month),
  KEY idx_eum_energy_month (energy_type,usage_month),
  KEY idx_eum_batch (sync_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备月累计用量';

CREATE TABLE energy_usage_monthly_dimension (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id VARCHAR(64) NOT NULL,
  usage_month DATE NOT NULL,
  energy_type VARCHAR(16) NOT NULL,
  identifier VARCHAR(64) NOT NULL,
  dimension_id BIGINT NOT NULL,
  dimension_value_id BIGINT NOT NULL,
  usage_value DECIMAL(20,6) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_eumd_device_month_metric_value (platform_device_id,usage_month,identifier,dimension_id,dimension_value_id),
  KEY idx_eumd_dimension_month (dimension_id,usage_month),
  KEY idx_eumd_value_month (dimension_value_id,usage_month),
  CONSTRAINT fk_eumd_dimension FOREIGN KEY (dimension_id) REFERENCES analysis_dimension(id),
  CONSTRAINT fk_eumd_value FOREIGN KEY (dimension_value_id) REFERENCES analysis_dimension_value(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清理日前用量前固化的月度维度拆分';

ALTER TABLE energy_usage_sync_batch
  ADD COLUMN monthly_points INT NOT NULL DEFAULT 0 AFTER failed,
  ADD COLUMN daily_points INT NOT NULL DEFAULT 0 AFTER monthly_points,
  ADD COLUMN purged_daily_points INT NOT NULL DEFAULT 0 AFTER daily_points;
