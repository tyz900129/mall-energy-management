-- 特殊区域正常巡检留档：为提交记录增加公开照片访问令牌
ALTER TABLE patrol_submission
  ADD COLUMN photo_token VARCHAR(64) NULL AFTER submitter_name,
  ADD UNIQUE KEY uk_patrol_submission_photo_token (photo_token);
