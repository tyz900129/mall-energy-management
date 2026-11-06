-- 撤场施工流程会把铺位状态置为 EXIT_CONSTRUCTION（18 字符），
-- 但 stall.status 定义为 varchar(16)，导致提交施工申请报 Data too long for column 'status'。
ALTER TABLE stall MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE';
