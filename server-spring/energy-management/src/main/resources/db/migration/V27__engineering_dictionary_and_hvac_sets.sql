ALTER TABLE stall_engineering_profile
  ADD COLUMN exhaust_fan_code VARCHAR(64) NULL AFTER exhaust_duct_no,
  ADD COLUMN fresh_air_unit_code VARCHAR(64) NULL AFTER fresh_air_duct_no,
  ADD COLUMN natural_gas_used TINYINT NOT NULL DEFAULT 0 AFTER gas_condition_available,
  ADD COLUMN natural_gas_caliber_code VARCHAR(64) NULL AFTER gas_pipe_caliber,
  ADD COLUMN gas_shutoff_location_code VARCHAR(64) NULL AFTER auto_shutoff_valve;
CREATE TABLE stall_engineering_air_conditioner (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, stall_engineering_profile_id BIGINT NOT NULL, equipment_spec_code VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_hvac_profile (stall_engineering_profile_id), CONSTRAINT fk_hvac_profile FOREIGN KEY (stall_engineering_profile_id) REFERENCES stall_engineering_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='铺位空调设备逐台配置';
