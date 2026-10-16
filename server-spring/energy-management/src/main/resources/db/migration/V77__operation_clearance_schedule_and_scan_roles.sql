CREATE TABLE operation_clearance_setting (
  id TINYINT PRIMARY KEY,
  submit_start_time TIME NOT NULL,
  submit_end_time TIME NOT NULL,
  updated_by VARCHAR(64),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO operation_clearance_setting(id,submit_start_time,submit_end_time,updated_by)
VALUES(1,'21:00:00','09:00:00','system');

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order,description) VALUES
('operation.clearance.setting.manage','配置商户日清时段','operation','运营管理','EDIT',1011,'配置商户日清允许提交的开始和结束时间')
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN('SUPER_ADMIN','SYSTEM_ADMIN') AND p.code='operation.clearance.setting.manage';

-- 物业工作人员扫码时需要查看本铺位责任商管资料。
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='DEVICE_VERIFIER' AND p.code='operation.merchant.view';
