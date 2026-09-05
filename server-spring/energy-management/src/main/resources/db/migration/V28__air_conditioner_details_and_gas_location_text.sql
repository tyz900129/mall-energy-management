ALTER TABLE stall_engineering_air_conditioner
  ADD COLUMN fan_coil_quantity INT NOT NULL DEFAULT 0 AFTER equipment_spec_code,
  ADD COLUMN hanging_cabinet_quantity INT NOT NULL DEFAULT 0 AFTER fan_coil_quantity,
  ADD COLUMN air_outlet_quantity INT NOT NULL DEFAULT 0 AFTER hanging_cabinet_quantity;

-- 设计负荷已改由各供配电回路的电缆规格自动汇总，不再单独维护。
UPDATE stall_engineering_profile SET load_power_kw = NULL;

-- 将原字典值转换为可直接编辑的文字，保留历史资料的可读性。
UPDATE stall_engineering_profile p
JOIN business_dictionary d ON d.dict_type='GAS_SHUTOFF_LOCATION' AND d.item_value=p.gas_shutoff_location_code
SET p.gas_shutoff_location_code=d.item_label
WHERE p.gas_shutoff_location_code IS NOT NULL AND p.gas_shutoff_location_code<>'';
