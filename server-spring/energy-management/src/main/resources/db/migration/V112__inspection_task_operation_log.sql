-- 任务中心：任务操作日志（认领、改派、网页回执、H5 回执）
CREATE TABLE IF NOT EXISTS inspection_task_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL COMMENT '任务ID',
  action VARCHAR(32) NOT NULL COMMENT 'CLAIM/REASSIGN/SUBMIT/H5_SUBMIT',
  actor_type VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER/H5/SYSTEM',
  actor_name VARCHAR(64) NULL COMMENT '操作人',
  detail VARCHAR(500) NULL COMMENT '操作说明',
  ip VARCHAR(64) NULL COMMENT '来源IP',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_inspection_task_log_task (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务操作日志';
