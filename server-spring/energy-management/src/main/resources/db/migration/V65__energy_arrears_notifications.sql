CREATE TABLE energy_arrears_account (
  account_number VARCHAR(128) PRIMARY KEY,
  platform_account_id VARCHAR(128) NULL,
  account_name VARCHAR(255) NULL,
  user_name VARCHAR(255) NULL,
  mobile_phone VARCHAR(64) NULL,
  balance DECIMAL(18,4) NULL,
  arrears DECIMAL(18,4) NULL,
  active TINYINT NOT NULL DEFAULT 1,
  excluded TINYINT NOT NULL DEFAULT 0,
  exclusion_remark VARCHAR(255) NULL,
  last_seen_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_arrears_active_excluded (active, excluded),
  KEY idx_arrears_name (account_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE energy_arrears_sync_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  fetched_count INT NOT NULL DEFAULT 0,
  excluded_count INT NOT NULL DEFAULT 0,
  notified_count INT NOT NULL DEFAULT 0,
  total_arrears DECIMAL(18,4) NULL,
  error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_arrears_run_started (started_at),
  KEY idx_arrears_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('energy.arrears.view','查看欠费通知','energy','能源管理','VIEW',509),
('energy.arrears.manage','同步欠费账户及设置不推送账户','energy','能源管理','MANAGE',510);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
  AND p.code IN ('energy.arrears.view','energy.arrears.manage');
