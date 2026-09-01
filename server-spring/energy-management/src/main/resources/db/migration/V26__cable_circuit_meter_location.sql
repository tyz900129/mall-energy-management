ALTER TABLE stall_engineering_cable ADD KEY idx_stall_cable_profile (stall_engineering_profile_id);

ALTER TABLE stall_engineering_cable
  DROP INDEX uk_profile_cable_spec,
  ADD COLUMN meter_location_code VARCHAR(64) NULL AFTER switch_quantity,
  ADD KEY idx_stall_cable_meter_location (meter_location_code);
