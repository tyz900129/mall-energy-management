CREATE TABLE operation_property_inspection_template (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(100) NOT NULL,business_type VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',version_no INT NOT NULL DEFAULT 1,
 fields_json JSON NOT NULL,enabled TINYINT NOT NULL DEFAULT 1,remark VARCHAR(255),created_by VARCHAR(64),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_operation_property_template_version(business_type,version_no),KEY idx_operation_property_template_active(business_type,enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO operation_property_inspection_template(name,business_type,version_no,fields_json,remark,created_by) VALUES('物业通用清场检查','DEFAULT',1,JSON_ARRAY(
 JSON_OBJECT('code','powerSafe','label','用电安全','type','BOOLEAN','required',true,'expected',true),JSON_OBJECT('code','waterSafe','label','用水已关闭','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','gasSafe','label','燃气已关闭','type','BOOLEAN','required',true,'expected',true),JSON_OBJECT('code','doorSafe','label','门窗已关闭','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','fireSafe','label','消防通道及消防设施正常','type','BOOLEAN','required',true,'expected',true),JSON_OBJECT('code','exceptionDescription','label','异常说明','type','TEXT','required',false)
),'默认物业复查模板','system');
CREATE TABLE operation_property_inspection (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,inspection_no VARCHAR(40) NOT NULL,stall_id BIGINT NOT NULL,period_date DATE NOT NULL,clearance_record_id BIGINT,
 template_id BIGINT NOT NULL,template_version INT NOT NULL,template_name_snapshot VARCHAR(100),answers_json JSON NOT NULL,inspector_user_id BIGINT NOT NULL,inspector_name VARCHAR(64),
 merchant_name_snapshot VARCHAR(128),status VARCHAR(20) NOT NULL,exception_description VARCHAR(500),inspected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_operation_property_stall_period(stall_id,period_date),KEY idx_operation_property_period(period_date,status),CONSTRAINT fk_operation_property_stall FOREIGN KEY(stall_id) REFERENCES stall(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.property.view','查看物业清场检查','operation','运营管理','VIEW',1030,'查看物业清场检查记录'),('operation.property.template.view','查看物业检查模板','operation','运营管理','VIEW',1031,'查看物业清场检查模板'),('operation.property.template.manage','维护物业检查模板','operation','运营管理','EDIT',1032,'维护物业清场检查模板')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code IN('operation.property.view','operation.property.template.view','operation.property.template.manage');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='DEVICE_VERIFIER' AND p.code IN('operation.property.view','operation.property.template.view');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='APPLICANT' AND p.code='operation.property.view';
