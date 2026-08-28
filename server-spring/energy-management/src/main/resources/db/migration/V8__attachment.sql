CREATE TABLE attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_type VARCHAR(32) NOT NULL,
  business_id BIGINT NOT NULL,
  file_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_provider VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
  storage_path VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  photo_type VARCHAR(32) NOT NULL,
  uploaded_by VARCHAR(64),
  uploaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  captured_at DATETIME(3),
  remark VARCHAR(500),
  KEY idx_att_business (business_type,business_id),
  KEY idx_att_sha256 (sha256),
  KEY idx_att_photo_type (photo_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

