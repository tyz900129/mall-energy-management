CREATE TABLE operation_clearance_template (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL,
 business_type VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
 version_no INT NOT NULL DEFAULT 1,
 fields_json JSON NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 remark VARCHAR(255),
 created_by VARCHAR(64),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_operation_clearance_template_version(business_type,version_no),
 KEY idx_operation_clearance_template_active(business_type,enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO operation_clearance_template(name,business_type,version_no,fields_json,remark,created_by) VALUES
('通用三关一闭模板','DEFAULT',1,JSON_ARRAY(
 JSON_OBJECT('code','electricityOff','label','非必要用电已关闭','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','waterOff','label','水源阀门已关闭','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','gasOff','label','燃气阀门已关闭','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','doorClosed','label','门窗已关闭锁好','type','BOOLEAN','required',true,'expected',true),
 JSON_OBJECT('code','exceptionDescription','label','异常说明','type','TEXT','required',false)
),'默认兜底模板','system');

ALTER TABLE operation_clearance_record
 ADD COLUMN template_id BIGINT NULL AFTER manager_name_snapshot,
 ADD COLUMN template_version INT NULL AFTER template_id,
 ADD COLUMN template_name_snapshot VARCHAR(100) NULL AFTER template_version,
 ADD COLUMN answers_json JSON NULL AFTER template_name_snapshot;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.clearance.template.view','查看日清模板','operation','运营管理','VIEW',1020,'查看按业态配置的日清表单模板'),
('operation.clearance.template.manage','维护日清模板','operation','运营管理','EDIT',1021,'新增、修改和停用日清表单模板')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code IN('operation.clearance.template.view','operation.clearance.template.manage');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='APPLICANT' AND p.code='operation.clearance.template.view';
