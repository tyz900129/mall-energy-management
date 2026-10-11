CREATE TABLE energy_account_daily_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_number VARCHAR(64) NOT NULL,
  platform_account_id VARCHAR(64) NOT NULL,
  source_bill_id VARCHAR(64) NULL,
  bill_date DATE NOT NULL,
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  amount DECIMAL(18,4) NOT NULL,
  source_updated_at DATETIME(3) NULL,
  fetched_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_energy_account_bill_day (account_number,bill_date),
  KEY idx_energy_account_bill_platform (platform_account_id),
  KEY idx_energy_account_bill_date (bill_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_account_anomaly_rule (
  account_number VARCHAR(64) PRIMARY KEY,
  enabled TINYINT NOT NULL DEFAULT 1,
  rise_threshold_pct DECIMAL(8,2) NULL,
  fall_threshold_pct DECIMAL(8,2) NULL,
  minimum_difference DECIMAL(18,2) NULL,
  webhook_enabled TINYINT NOT NULL DEFAULT 1,
  ignore_start_date DATE NULL,
  ignore_end_date DATE NULL,
  remark VARCHAR(255) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO energy_account_anomaly_rule(account_number,rise_threshold_pct,fall_threshold_pct,minimum_difference,remark)
VALUES('__DEFAULT__',50.00,50.00,20.00,'系统默认阈值');

CREATE TABLE energy_account_anomaly_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_number VARCHAR(64) NOT NULL,
  account_name VARCHAR(255) NULL,
  bill_date DATE NOT NULL,
  actual_amount DECIMAL(18,4) NULL,
  baseline_average DECIMAL(18,4) NULL,
  sample_days INT NOT NULL DEFAULT 0,
  difference_amount DECIMAL(18,4) NULL,
  difference_pct DECIMAL(10,2) NULL,
  direction VARCHAR(16) NULL,
  severity VARCHAR(16) NULL,
  analysis_status VARCHAR(24) NOT NULL,
  handle_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  handle_remark VARCHAR(500) NULL,
  handled_by VARCHAR(64) NULL,
  handled_at DATETIME(3) NULL,
  notified TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_energy_account_anomaly_day (account_number,bill_date),
  KEY idx_energy_account_anomaly_rank (bill_date,analysis_status,difference_pct),
  KEY idx_energy_account_anomaly_handle (handle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_account_anomaly_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  target_date DATE NOT NULL,
  account_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  anomaly_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_energy_account_anomaly_run (started_at,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('energy.anomaly.view','查看账户异动监控','energy','能源管理','VIEW',511),
('energy.anomaly.manage','执行异动分析及维护阈值','energy','能源管理','MANAGE',512);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
  AND p.code IN ('energy.anomaly.view','energy.anomaly.manage');
