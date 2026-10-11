ALTER TABLE energy_arrears_account
  ADD COLUMN arrears_started_at VARCHAR(64) NULL AFTER arrears;
