ALTER TABLE energy_usage_sync_batch
  ADD COLUMN no_data_devices INT NOT NULL DEFAULT 0 AFTER failed;
