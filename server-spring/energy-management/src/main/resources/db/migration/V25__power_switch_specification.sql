CREATE TABLE power_switch_specification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  spec_code VARCHAR(64) NOT NULL,
  brand VARCHAR(100),
  switch_model VARCHAR(120) NOT NULL,
  pole_count VARCHAR(20) NOT NULL,
  rated_current_a DECIMAL(12,2) NOT NULL,
  breaking_capacity_ka DECIMAL(12,2),
  switch_type VARCHAR(40),
  status TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_power_switch_spec_code (spec_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供电端开关规格参数';

ALTER TABLE stall_engineering_cable
  ADD COLUMN power_switch_specification_id BIGINT NULL AFTER cable_specification_id,
  ADD COLUMN switch_quantity INT NOT NULL DEFAULT 1 AFTER quantity,
  ADD KEY idx_stall_cable_switch (power_switch_specification_id),
  ADD CONSTRAINT fk_stall_cable_switch FOREIGN KEY (power_switch_specification_id) REFERENCES power_switch_specification(id);
