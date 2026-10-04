ALTER TABLE access_person
  ADD COLUMN claimed_at DATETIME NULL AFTER system_user_id,
  ADD COLUMN claimed_username VARCHAR(64) NULL AFTER claimed_at;

CREATE TABLE access_person_claim_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id BIGINT NULL,
  username VARCHAR(64) NOT NULL,
  phone_last4 VARCHAR(4),
  id_number_last4 VARCHAR(4),
  claim_result VARCHAR(24) NOT NULL,
  failure_reason VARCHAR(255),
  client_ip VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_access_claim_username(username,created_at),
  KEY idx_access_claim_person(person_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
