-- A physical device may belong to at most one active stall at a time.
-- V3 used CONCAT(stall_id, ':', platform_device_id), which only prevented
-- duplicate rows inside the same stall and did not protect cross-stall races.
ALTER TABLE stall_device
  DROP INDEX uk_sd_active,
  MODIFY COLUMN active_dedup VARCHAR(64) GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN platform_device_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_sd_active (active_dedup);
