-- Phase 3 异常中心
-- (1) 新建 data_exception 主表
-- (2) 在 platform_sync_batch 增加 exception_count 字段（前端同步日志页面要展示）
ALTER TABLE platform_sync_batch
  ADD COLUMN exception_count INT NULL DEFAULT 0 AFTER relation_count;

CREATE TABLE data_exception (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  exception_type     VARCHAR(64)  NOT NULL,                   -- DEVICE_MULTI_ACCOUNT / UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE / DEVICE_DISAPPEARED / SYNC_FAILED ...
  severity           VARCHAR(16)  NOT NULL,                   -- INFO / WARN / ERROR / CRITICAL
  status             VARCHAR(16)  NOT NULL DEFAULT 'OPEN',    -- OPEN / ACK / HANDLING / RESOLVED / IGNORED
  workflow_id        BIGINT       NULL,                       -- 关联流程（Phase 6+）
  stall_id           BIGINT       NULL,
  merchant_code      VARCHAR(6)   NULL,
  platform_device_id VARCHAR(64)  NULL,
  account_number     VARCHAR(64)  NULL,
  title              VARCHAR(255) NOT NULL,
  description        TEXT,
  payload_json       JSON         NULL,                       -- 详细原始数据
  detected_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  handler_id         BIGINT       NULL,
  handled_at         DATETIME(3)  NULL,
  handle_result      VARCHAR(255) NULL,
  sync_batch_id      BIGINT       NULL,
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_de_type (exception_type),
  KEY idx_de_severity (severity),
  KEY idx_de_status (status),
  KEY idx_de_device (platform_device_id),
  KEY idx_de_account (account_number),
  KEY idx_de_detected (detected_at),
  KEY idx_de_batch (sync_batch_id)
) ENGINE=InnoDB;

-- 去重：同一批次、同一 deviceId 的 Multi-Account 只保留一条
CREATE UNIQUE INDEX uk_de_type_dev_batch
  ON data_exception (exception_type, platform_device_id, sync_batch_id);

