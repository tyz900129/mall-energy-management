CREATE TABLE device_group (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_name   VARCHAR(100) NOT NULL,
  color        VARCHAR(16) NOT NULL DEFAULT '#409EFF',
  sort_order   INT NOT NULL DEFAULT 0,
  remark       VARCHAR(255),
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_device_group_name (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE device_group_assignment (
  platform_device_id VARCHAR(64) PRIMARY KEY,
  group_id           BIGINT NOT NULL,
  assigned_by        BIGINT,
  assigned_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_dga_group (group_id),
  CONSTRAINT fk_dga_group FOREIGN KEY (group_id) REFERENCES device_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE device_energy_metric (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id         VARCHAR(64) NOT NULL,
  energy_type        VARCHAR(16) NOT NULL,
  identifier         VARCHAR(64) NOT NULL,
  identifier_name    VARCHAR(128) NOT NULL,
  value_mode         VARCHAR(16) NOT NULL DEFAULT 'USAGE',
  enabled            TINYINT NOT NULL DEFAULT 1,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dem_product_energy (product_id, energy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_usage_sync_batch (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no       VARCHAR(32) NOT NULL,
  start_date     DATE NOT NULL,
  end_date       DATE NOT NULL,
  group_id       BIGINT,
  requested      INT NOT NULL DEFAULT 0,
  succeeded      INT NOT NULL DEFAULT 0,
  failed         INT NOT NULL DEFAULT 0,
  status         VARCHAR(16) NOT NULL,
  error_message  TEXT,
  started_at     DATETIME(3) NOT NULL,
  completed_at   DATETIME(3),
  trigger_user_id BIGINT,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_eusb_batch_no (batch_no),
  KEY idx_eusb_started (started_at),
  KEY idx_eusb_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_usage_daily (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id VARCHAR(64) NOT NULL,
  usage_date         DATE NOT NULL,
  energy_type        VARCHAR(16) NOT NULL,
  identifier         VARCHAR(64) NOT NULL,
  identifier_name    VARCHAR(128),
  usage_value        DECIMAL(20,6),
  meter_reading      DECIMAL(20,6),
  value_mode         VARCHAR(16) NOT NULL DEFAULT 'USAGE',
  sync_batch_id      BIGINT NOT NULL,
  synced_at          DATETIME(3) NOT NULL,
  raw_json           JSON,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_eud_device_date_metric (platform_device_id, usage_date, identifier),
  KEY idx_eud_date (usage_date),
  KEY idx_eud_energy_date (energy_type, usage_date),
  KEY idx_eud_batch (sync_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
