CREATE TABLE webhook_config (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, provider VARCHAR(32) NOT NULL,
 webhook_url_ciphertext TEXT NOT NULL, webhook_url_iv VARCHAR(64) NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 1,
 event_types VARCHAR(500) NOT NULL DEFAULT 'FIELD_VERIFICATION_CREATED', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), KEY idx_wc_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE webhook_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, workflow_id BIGINT, event_type VARCHAR(64) NOT NULL, provider VARCHAR(32) NOT NULL,
 webhook_config_id BIGINT, request_payload JSON, response_payload TEXT, http_status INT, send_status VARCHAR(16) NOT NULL,
 retry_count INT NOT NULL DEFAULT 0, next_retry_at DATETIME(3), sent_at DATETIME(3), error_message VARCHAR(500),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 KEY idx_wl_workflow (workflow_id), KEY idx_wl_retry (send_status,retry_count,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

