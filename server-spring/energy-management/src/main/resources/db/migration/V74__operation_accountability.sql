CREATE TABLE operation_accountability_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_no VARCHAR(40) NOT NULL,
 property_inspection_id BIGINT NOT NULL,
 stall_id BIGINT NOT NULL,
 period_date DATE NOT NULL,
 stall_code_snapshot VARCHAR(64) NOT NULL,
 merchant_name_snapshot VARCHAR(128),
 issue_description VARCHAR(1000) NOT NULL,
 responsible_name VARCHAR(64),
 responsible_phone VARCHAR(32),
 corrective_action VARCHAR(1000),
 deadline_at DATETIME(3),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 handling_result VARCHAR(1000),
 created_by VARCHAR(64),
 handled_by VARCHAR(64),
 resolved_at DATETIME(3),
 closed_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_operation_accountability_no(order_no),
 UNIQUE KEY uk_operation_accountability_inspection(property_inspection_id),
 KEY idx_operation_accountability_status(status,period_date),
 CONSTRAINT fk_operation_accountability_inspection FOREIGN KEY(property_inspection_id) REFERENCES operation_property_inspection(id),
 CONSTRAINT fk_operation_accountability_stall FOREIGN KEY(stall_id) REFERENCES stall(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.accountability.view','查看运营追责单','operation','运营管理','VIEW',1040,'查看和打印运营追责单'),
('operation.accountability.manage','处理运营追责单','operation','运营管理','EDIT',1041,'创建、处理及关闭运营追责单')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code IN('operation.accountability.view','operation.accountability.manage');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code IN('operation.accountability.view','operation.accountability.manage');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='APPLICANT' AND p.code='operation.accountability.view';
