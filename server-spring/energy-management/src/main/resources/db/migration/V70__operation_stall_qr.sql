-- 每个铺位一个永久二维码。商户或责任人变化时二维码不变。
ALTER TABLE stall ADD COLUMN operation_qr_token VARCHAR(40) NULL AFTER remark;
UPDATE stall SET operation_qr_token=REPLACE(UUID(),'-','') WHERE operation_qr_token IS NULL OR operation_qr_token='';
ALTER TABLE stall MODIFY operation_qr_token VARCHAR(40) NOT NULL, ADD UNIQUE KEY uk_stall_operation_qr_token(operation_qr_token);
