CREATE TABLE energy_balance_warning_rule (
  account_number VARCHAR(64) PRIMARY KEY,
  enabled TINYINT NOT NULL DEFAULT 1,
  warning_days DECIMAL(8,2) NULL,
  webhook_enabled TINYINT NOT NULL DEFAULT 1,
  ignore_start_date DATE NULL,
  ignore_end_date DATE NULL,
  remark VARCHAR(255) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO energy_balance_warning_rule(account_number,warning_days,remark)
VALUES('__DEFAULT__',3.00,'系统默认余额可用天数阈值');

CREATE TABLE energy_balance_warning_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_number VARCHAR(64) NOT NULL,
  account_name VARCHAR(255) NULL,
  analysis_date DATE NOT NULL,
  balance DECIMAL(18,4) NULL,
  seven_day_amount DECIMAL(18,4) NULL,
  daily_average DECIMAL(18,4) NULL,
  sample_days INT NOT NULL DEFAULT 0,
  available_days DECIMAL(12,2) NULL,
  warning_days DECIMAL(8,2) NOT NULL,
  analysis_status VARCHAR(24) NOT NULL,
  notified TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_energy_balance_warning_day (account_number,analysis_date),
  KEY idx_energy_balance_warning_rank (analysis_date,analysis_status,available_days)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_balance_warning_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  analysis_date DATE NOT NULL,
  account_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  warning_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_energy_balance_warning_run (started_at,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('energy.balance-warning.view','查看余额预警通知','energy','能源管理','VIEW',513),
('energy.balance-warning.manage','执行余额分析及维护阈值','energy','能源管理','MANAGE',514);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
  AND p.code IN ('energy.balance-warning.view','energy.balance-warning.manage');
