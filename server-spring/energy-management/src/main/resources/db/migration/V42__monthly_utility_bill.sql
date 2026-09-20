CREATE TABLE energy_utility_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_month DATE NOT NULL,
  electric_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '电费账单金额',
  electric_usage DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '电费账单用量',
  water_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '水费账单金额',
  water_usage DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '水费账单用量',
  created_by VARCHAR(100),
  updated_by VARCHAR(100),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_energy_utility_bill_month (bill_month),
  KEY idx_energy_utility_bill_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月度水电账单';
