ALTER TABLE workflow
  ADD COLUMN requested_stall_business_type VARCHAR(64) NULL COMMENT '新商户开户申请设置的铺位业态',
  ADD COLUMN original_stall_business_type VARCHAR(64) NULL COMMENT '创建流程时铺位原业态，用于安全回退';
