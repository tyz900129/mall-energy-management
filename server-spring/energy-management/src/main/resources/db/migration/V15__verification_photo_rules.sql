ALTER TABLE workflow_device
  ADD COLUMN product_name_snapshot VARCHAR(128) NULL AFTER channel_name_snapshot,
  ADD COLUMN meter_phase_snapshot VARCHAR(16) NULL AFTER product_name_snapshot;

ALTER TABLE field_verification_device
  ADD COLUMN product_name_snapshot VARCHAR(128) NULL AFTER energy_type,
  ADD COLUMN meter_phase VARCHAR(16) NULL AFTER product_name_snapshot;

