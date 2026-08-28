CREATE TABLE workflow_match_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id BIGINT NOT NULL,
  sync_batch_id BIGINT NOT NULL,
  match_status VARCHAR(32) NOT NULL,
  matched_count INT NOT NULL DEFAULT 0,
  total_count INT NOT NULL DEFAULT 0,
  summary VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_workflow_match_batch (workflow_id, sync_batch_id),
  KEY idx_match_run_status (match_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workflow_match_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  match_run_id BIGINT NOT NULL,
  expected_change_id BIGINT NOT NULL,
  platform_device_id VARCHAR(64) NOT NULL,
  expected_account_number VARCHAR(64),
  actual_account_numbers VARCHAR(1000),
  match_status VARCHAR(32) NOT NULL,
  reason VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_match_detail_run (match_run_id),
  KEY idx_match_detail_device (platform_device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

