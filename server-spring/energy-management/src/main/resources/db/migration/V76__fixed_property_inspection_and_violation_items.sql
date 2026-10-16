CREATE TABLE operation_violation_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  violation_category VARCHAR(64) NOT NULL,
  violation_content VARCHAR(500) NOT NULL,
  incentive_measure VARCHAR(500),
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_operation_violation_enabled (enabled,sort_order),
  KEY idx_operation_violation_category (violation_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO operation_property_inspection_template(name,business_type,version_no,fields_json,enabled,remark,created_by)
VALUES('物业清场检查（固定表单）','SYSTEM_FIXED',1,JSON_ARRAY(),1,'系统固定表单，不在模板管理中维护','system')
ON DUPLICATE KEY UPDATE name=VALUES(name),enabled=1,remark=VALUES(remark);

ALTER TABLE operation_property_inspection
  ADD COLUMN inspector_phone VARCHAR(32) NULL AFTER inspector_name,
  ADD COLUMN stall_code_snapshot VARCHAR(64) NULL AFTER inspector_phone,
  ADD COLUMN floor_name_snapshot VARCHAR(64) NULL AFTER stall_code_snapshot,
  ADD COLUMN merchant_code_snapshot VARCHAR(32) NULL AFTER floor_name_snapshot,
  ADD COLUMN merchant_phone_snapshot VARCHAR(32) NULL AFTER merchant_name_snapshot,
  ADD COLUMN violation_items_json JSON NULL AFTER exception_description,
  ADD COLUMN photo_urls_json JSON NULL AFTER violation_items_json;

UPDATE operation_property_inspection i
JOIN stall s ON s.id=i.stall_id
LEFT JOIN merchant m ON m.id=s.current_merchant_id
SET i.stall_code_snapshot=s.stall_code,
    i.floor_name_snapshot=s.floor_name,
    i.merchant_code_snapshot=m.merchant_code,
    i.merchant_phone_snapshot=m.contact_phone
WHERE i.stall_code_snapshot IS NULL;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.violation.view','查看违规项目','operation','运营管理','VIEW',1033,'查看物业清场检查可选的违规项目'),
('operation.violation.manage','维护违规项目','operation','运营管理','EDIT',1034,'新增、修改、删除违规分类、违规内容和激励措施')
ON DUPLICATE KEY UPDATE name=VALUES(name),module_code=VALUES(module_code),module_name=VALUES(module_name),action_type=VALUES(action_type),sort_order=VALUES(sort_order),description=VALUES(description);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code IN('operation.violation.view','operation.violation.manage');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code='operation.violation.view';
