CREATE TABLE IF NOT EXISTS platform_account_local_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_number VARCHAR(64) NOT NULL,
  merchant_code VARCHAR(6),
  updated_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_palp_account (account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

