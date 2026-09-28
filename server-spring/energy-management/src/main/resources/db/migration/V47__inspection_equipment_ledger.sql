CREATE TABLE inspection_equipment_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(48) NOT NULL,
  name VARCHAR(96) NOT NULL,
  system_name VARCHAR(96),
  base_fields_json LONGTEXT NOT NULL,
  description VARCHAR(500),
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_equipment_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_equipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_code VARCHAR(64) NOT NULL,
  equipment_name VARCHAR(128) NOT NULL,
  category_id BIGINT NOT NULL,
  system_name VARCHAR(96),
  installation_location VARCHAR(255),
  floor_code VARCHAR(64),
  area_name VARCHAR(96),
  brand VARCHAR(96),
  model VARCHAR(128),
  specification VARCHAR(255),
  serial_number VARCHAR(128),
  commissioned_date DATE,
  responsible_department VARCHAR(96),
  responsible_person VARCHAR(64),
  responsible_phone VARCHAR(32),
  equipment_status VARCHAR(24) NOT NULL DEFAULT 'IN_USE',
  importance_level VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
  maintenance_company VARCHAR(128),
  maintenance_phone VARCHAR(32),
  next_maintenance_date DATE,
  attributes_json LONGTEXT NOT NULL,
  qr_token VARCHAR(64) NOT NULL,
  remark VARCHAR(1000),
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_equipment_code (equipment_code),
  UNIQUE KEY uk_inspection_equipment_qr (qr_token),
  KEY idx_inspection_equipment_category (category_id,enabled),
  KEY idx_inspection_equipment_location (floor_code,area_name),
  CONSTRAINT fk_inspection_equipment_category FOREIGN KEY (category_id) REFERENCES inspection_equipment_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection_category_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  inspection_kind VARCHAR(32) NOT NULL DEFAULT 'ROUTINE',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_inspection_category_template (category_id,template_id,inspection_kind),
  CONSTRAINT fk_inspection_ct_category FOREIGN KEY (category_id) REFERENCES inspection_equipment_category(id) ON DELETE CASCADE,
  CONSTRAINT fk_inspection_ct_template FOREIGN KEY (template_id) REFERENCES inspection_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE inspection_task ADD COLUMN equipment_snapshot_json LONGTEXT NULL AFTER location_text;

INSERT INTO inspection_equipment_category(code,name,system_name,base_fields_json,sort_order) VALUES
('TRANSFORMER','变压器','供配电系统','[{"key":"capacity","label":"容量","controlType":"TEXT","unit":"kVA"},{"key":"voltageLevel","label":"电压等级","controlType":"TEXT"},{"key":"connectionGroup","label":"接线组别","controlType":"TEXT"},{"key":"coolingMethod","label":"冷却方式","controlType":"TEXT"}]',10),
('POWER_CABINET','配电柜','供配电系统','[{"key":"cabinetNo","label":"柜号","controlType":"TEXT"},{"key":"voltageLevel","label":"电压等级","controlType":"TEXT"},{"key":"incomingSource","label":"进线来源","controlType":"TEXT"},{"key":"backupPower","label":"备用电源","controlType":"TEXT"}]',20),
('FIRE_HYDRANT','消防栓','消防系统','[{"key":"outletDiameter","label":"栓口口径","controlType":"TEXT"},{"key":"hoseLength","label":"水带长度","controlType":"TEXT","unit":"米"},{"key":"serviceArea","label":"服务区域","controlType":"TEXT"}]',30),
('AIR_CONDITIONER','空调设备','暖通空调系统','[{"key":"acType","label":"空调类型","controlType":"TEXT"},{"key":"coolingCapacity","label":"制冷量","controlType":"TEXT"},{"key":"power","label":"功率","controlType":"TEXT","unit":"kW"},{"key":"airVolume","label":"风量","controlType":"TEXT","unit":"m³/h"}]',40),
('WATER_SUPPLY','供水设备','供水系统','[{"key":"flow","label":"流量","controlType":"TEXT"},{"key":"head","label":"扬程","controlType":"TEXT"},{"key":"power","label":"功率","controlType":"TEXT","unit":"kW"},{"key":"pipeDiameter","label":"管径","controlType":"TEXT"}]',50),
('ELEVATOR','电梯','电梯系统','[{"key":"registrationCode","label":"设备注册代码","controlType":"TEXT"},{"key":"ratedLoad","label":"额定载重","controlType":"TEXT","unit":"kg"},{"key":"speed","label":"额定速度","controlType":"TEXT","unit":"m/s"},{"key":"floorStations","label":"层站数","controlType":"TEXT"}]',60);

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('inspection.equipment.view','查看设施设备台账','inspection','设备巡检管理','VIEW',308),
('inspection.equipment.manage','管理设施设备台账','inspection','设备巡检管理','MANAGE',309),
('inspection.category.manage','管理设施设备分类','inspection','设备巡检管理','MANAGE',310);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code IN ('inspection.equipment.view','inspection.equipment.manage','inspection.category.manage');

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code='inspection.equipment.view';

-- 第一版曾以能源平台水电表作为巡检对象；新台账独立运行，旧计划自动停用但历史记录保留。
UPDATE inspection_plan p SET p.enabled=0
WHERE EXISTS (SELECT 1 FROM inspection_plan_target t WHERE t.plan_id=p.id AND t.target_type='PLATFORM_DEVICE');
