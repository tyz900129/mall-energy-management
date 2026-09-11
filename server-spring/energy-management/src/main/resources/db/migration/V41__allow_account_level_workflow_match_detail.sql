-- ACCOUNT_DISABLE is an account-level expected change and therefore has no device id.
-- Keeping this column NOT NULL caused the match run to stop after device rows had
-- already been written, leaving the workflow status at WAITING_PLATFORM_CHANGE.
ALTER TABLE workflow_match_detail
    MODIFY COLUMN platform_device_id VARCHAR(64) NULL;
