-- P4：日历任务移动回执（免登录 H5）
--
-- 说明：
--   1. access_token_expire_at：公开回执票据有效期；任务生成时写入（默认到期后 7 天）。
--      NULL 表示历史任务未设置有效期，按原逻辑只校验 token 存在。
--   2. submitter_name / submit_source / late_minutes：回执提交来源与迟交统计。
--      这三个字段在 InspectionService.submit 中已使用，但历史迁移未落库，这里补齐。
--   3. receipt_*：免登录回执的身份留痕（提交人填写、手机号、IP、UA），用于事后追溯。
--   4. token 唯一索引 V46 已建（uk_inspection_task_token），此处只补有效期索引。

ALTER TABLE inspection_task
  ADD COLUMN access_token_expire_at DATETIME(3) NULL AFTER access_token,
  ADD COLUMN submitter_name VARCHAR(64) NULL AFTER completed_by,
  ADD COLUMN submit_source VARCHAR(16) NULL AFTER submitter_name,
  ADD COLUMN late_minutes INT NOT NULL DEFAULT 0 AFTER submit_source,
  ADD COLUMN receipt_name VARCHAR(64) NULL AFTER late_minutes,
  ADD COLUMN receipt_phone VARCHAR(32) NULL AFTER receipt_name,
  ADD COLUMN receipt_ip VARCHAR(64) NULL AFTER receipt_phone,
  ADD COLUMN receipt_user_agent VARCHAR(255) NULL AFTER receipt_ip,
  ADD KEY idx_inspection_task_token_expire (access_token_expire_at);

-- 存量任务补默认有效期：已结束任务不补；未结束任务按到期时间 +7 天回填
UPDATE inspection_task
SET access_token_expire_at = DATE_ADD(due_at, INTERVAL 7 DAY)
WHERE access_token_expire_at IS NULL
  AND status NOT IN ('COMPLETED', 'CLOSED', 'CANCELLED');

-- 存量日历任务纠正来源：此前生成时统一写死 PLAN，导致二维码无法分流到移动回执页
UPDATE inspection_task k
JOIN inspection_type y ON y.id = k.inspection_type_id
SET k.source_type = 'CALENDAR'
WHERE y.code = 'CALENDAR' AND k.source_type = 'PLAN';
