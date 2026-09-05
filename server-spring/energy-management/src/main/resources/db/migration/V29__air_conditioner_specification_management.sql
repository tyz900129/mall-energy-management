CREATE TABLE air_conditioner_specification (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,spec_code VARCHAR(64) NOT NULL,model VARCHAR(128) NOT NULL,equipment_type VARCHAR(32) NOT NULL,air_outlet_quantity INT NOT NULL DEFAULT 0,status TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0,remark VARCHAR(500),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_ac_spec_code (spec_code),KEY idx_ac_spec_status_sort (status,sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空调型号及固定参数';

INSERT INTO air_conditioner_specification(spec_code,model,equipment_type,air_outlet_quantity,status,sort_order,remark)
SELECT item_value,item_label,'OTHER',0,status,sort_order,'由原空调设备规格字典自动迁移' FROM business_dictionary WHERE dict_type='AIR_CONDITIONER_SPEC';

ALTER TABLE stall_engineering_air_conditioner ADD COLUMN air_conditioner_specification_id BIGINT NULL AFTER stall_engineering_profile_id;
UPDATE stall_engineering_air_conditioner a JOIN air_conditioner_specification s ON s.spec_code=a.equipment_spec_code SET a.air_conditioner_specification_id=s.id;
ALTER TABLE stall_engineering_air_conditioner ADD KEY idx_hvac_spec (air_conditioner_specification_id),ADD CONSTRAINT fk_hvac_spec FOREIGN KEY (air_conditioner_specification_id) REFERENCES air_conditioner_specification(id);
