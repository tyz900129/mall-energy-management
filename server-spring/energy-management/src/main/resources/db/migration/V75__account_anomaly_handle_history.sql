CREATE TABLE energy_account_anomaly_handle_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  result_id BIGINT NOT NULL,
  account_number VARCHAR(64) NOT NULL,
  bill_date DATE NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  remark VARCHAR(500) NOT NULL,
  handled_by VARCHAR(64) NOT NULL,
  handled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_anomaly_handle_log_result (result_id, handled_at),
  KEY idx_anomaly_handle_log_account (account_number, bill_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO energy_account_anomaly_handle_log(
  result_id, account_number, bill_date, from_status, to_status, remark, handled_by, handled_at
)
SELECT
  id,
  account_number,
  bill_date,
  'PENDING',
  handle_status,
  handle_remark,
  COALESCE(NULLIF(handled_by, ''), 'system'),
  COALESCE(handled_at, updated_at)
FROM energy_account_anomaly_result
WHERE handle_remark IS NOT NULL AND TRIM(handle_remark) <> '';
