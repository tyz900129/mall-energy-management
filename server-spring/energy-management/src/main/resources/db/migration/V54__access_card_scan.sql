CREATE TABLE access_card_scan_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  device_total INT NOT NULL DEFAULT 0,
  device_completed INT NOT NULL DEFAULT 0,
  card_rows INT NOT NULL DEFAULT 0,
  unique_cards INT NOT NULL DEFAULT 0,
  common_cards INT NOT NULL DEFAULT 0,
  only_entry INT NOT NULL DEFAULT 0,
  only_exit INT NOT NULL DEFAULT 0,
  id_number_cards INT NOT NULL DEFAULT 0,
  conflict_cards INT NOT NULL DEFAULT 0,
  message VARCHAR(1000),
  created_by VARCHAR(64),
  started_at DATETIME(3),
  completed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_card_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scan_job_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  employee_no VARCHAR(64) NOT NULL,
  card_no_hash VARCHAR(64) NOT NULL,
  card_no_ciphertext TEXT NOT NULL,
  card_no_iv VARCHAR(64) NOT NULL,
  card_no_last4 VARCHAR(4),
  card_length INT NOT NULL DEFAULT 0,
  id_number_format TINYINT NOT NULL DEFAULT 0,
  card_type VARCHAR(32),
  raw_json LONGTEXT,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_card_scan_device_hash(scan_job_id,device_id,card_no_hash),
  KEY idx_access_card_scan_employee(scan_job_id,employee_no),
  CONSTRAINT fk_access_card_scan_job FOREIGN KEY(scan_job_id) REFERENCES access_card_scan_job(id),
  CONSTRAINT fk_access_card_scan_device FOREIGN KEY(device_id) REFERENCES access_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
