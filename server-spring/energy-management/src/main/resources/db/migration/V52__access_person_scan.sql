CREATE TABLE access_person_scan_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  device_total INT NOT NULL DEFAULT 0,
  device_completed INT NOT NULL DEFAULT 0,
  person_rows INT NOT NULL DEFAULT 0,
  unique_persons INT NOT NULL DEFAULT 0,
  common_persons INT NOT NULL DEFAULT 0,
  only_entry INT NOT NULL DEFAULT 0,
  only_exit INT NOT NULL DEFAULT 0,
  conflict_persons INT NOT NULL DEFAULT 0,
  message VARCHAR(1000),
  created_by VARCHAR(64),
  started_at DATETIME(3),
  completed_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_access_scan_status(status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_person_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scan_job_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  employee_no VARCHAR(100) NOT NULL,
  person_name VARCHAR(128),
  user_type VARCHAR(32),
  phone VARCHAR(64),
  valid_from DATETIME(3),
  valid_to DATETIME(3),
  face_count INT NOT NULL DEFAULT 0,
  card_count INT NOT NULL DEFAULT 0,
  raw_json LONGTEXT,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_access_snapshot_job_device_person(scan_job_id,device_id,employee_no),
  KEY idx_access_snapshot_compare(scan_job_id,employee_no),
  CONSTRAINT fk_access_snapshot_job FOREIGN KEY(scan_job_id) REFERENCES access_person_scan_job(id) ON DELETE CASCADE,
  CONSTRAINT fk_access_snapshot_device FOREIGN KEY(device_id) REFERENCES access_device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
