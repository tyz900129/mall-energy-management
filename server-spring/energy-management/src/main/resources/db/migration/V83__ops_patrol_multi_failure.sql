-- =====================================================================
-- V83 营运巡场：一检查项多商户异常
-- 问题单关联商户账户（以「商户账户管理」的商户名称为准），并支持同一检查项登记多家商户
-- =====================================================================

ALTER TABLE ops_patrol_issue
  ADD COLUMN account_number VARCHAR(64) NULL COMMENT '商户账户号（平台账户 account_number）' AFTER merchant_id,
  ADD COLUMN merchant_code VARCHAR(16) NULL COMMENT '商户编码（6 位）' AFTER merchant_name,
  ADD COLUMN account_name_raw VARCHAR(255) NULL COMMENT '账户原始名称（3066-编码-名称）' AFTER merchant_code,
  ADD KEY idx_ops_patrol_issue_task_item (task_item_id),
  ADD KEY idx_ops_patrol_issue_account (task_item_id, account_number, stall_code);
