CREATE TABLE construction_inspection_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inspection_type VARCHAR(24) NOT NULL,
  name VARCHAR(100) NOT NULL,
  fields_json LONGTEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_construction_inspection_template_type (inspection_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE construction_inspection
  ADD COLUMN template_snapshot_json LONGTEXT NULL AFTER inspection_type,
  ADD COLUMN answers_json LONGTEXT NULL AFTER template_snapshot_json;

INSERT INTO construction_inspection_template(inspection_type,name,fields_json) VALUES
('SECURITY','安保巡检','[{"key":"personnel_access","label":"施工人员及出入管理是否符合要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":10},{"key":"hot_work","label":"动火及消防安全措施是否符合要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":20},{"key":"site_order","label":"现场秩序及封闭管理是否符合要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":false,"enabled":true,"sortOrder":30}]'),
('ENGINEERING','工程巡检','[{"key":"temporary_power","label":"临时用电是否安全规范","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":10},{"key":"facility_protection","label":"原有设施及公共区域保护是否完好","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":20},{"key":"construction_quality","label":"施工内容及工艺是否符合审批要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":30}]'),
('CLEANING','保洁巡检','[{"key":"waste_clearance","label":"施工垃圾是否及时清运","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":10},{"key":"public_cleanliness","label":"公共区域卫生是否符合要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":false,"enabled":true,"sortOrder":20},{"key":"dust_control","label":"扬尘及污染防护是否符合要求","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"enabled":true,"sortOrder":30}]');

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('construction.inspection.config.view','查看施工巡检配置','construction','施工流程管理','VIEW',325),
('construction.inspection.config.manage','管理施工巡检配置','construction','施工流程管理','MANAGE',326);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
  AND p.code IN ('construction.inspection.config.view','construction.inspection.config.manage');
