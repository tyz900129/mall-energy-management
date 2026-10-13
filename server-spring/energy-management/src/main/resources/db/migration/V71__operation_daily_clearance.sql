CREATE TABLE operation_clearance_record (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 record_no VARCHAR(40) NOT NULL,
 stall_id BIGINT NOT NULL,
 period_date DATE NOT NULL,
 period_start DATETIME(3) NOT NULL,
 period_end DATETIME(3) NOT NULL,
 submitter_user_id BIGINT NOT NULL,
 submitter_name VARCHAR(64),
 merchant_name_snapshot VARCHAR(128),
 manager_user_id BIGINT,
 manager_name_snapshot VARCHAR(64),
 electricity_off TINYINT NOT NULL,
 water_off TINYINT NOT NULL,
 gas_off TINYINT NOT NULL,
 door_closed TINYINT NOT NULL,
 exception_description VARCHAR(500),
 status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
 submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_operation_clearance_stall_period(stall_id,period_date),
 KEY idx_operation_clearance_period(period_date,status),
 KEY idx_operation_clearance_manager(manager_user_id,period_date),
 CONSTRAINT fk_operation_clearance_stall FOREIGN KEY(stall_id) REFERENCES stall(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.clearance.view','查看商户日清','operation','运营管理','VIEW',1010,'查看本人或本人负责商户的日清记录')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER') AND p.code='operation.clearance.view';
