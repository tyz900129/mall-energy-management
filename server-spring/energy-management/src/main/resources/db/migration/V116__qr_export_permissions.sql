-- 批量导出二维码 + 商管自助下载权限码
-- 两个权限码：
--   stall.qr.batch.view    管理端批量导出铺位/巡检设备二维码（zip）
--   stall.qr.self.view     商管自助下载自己负责铺位的二维码

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('stall.qr.batch.view','批量导出铺位/巡检设备二维码','operation','运营管理','VIEW',520),
('stall.qr.self.view','商管自助下载负责铺位的二维码','operation','运营管理','VIEW',521);

-- 1. SUPER_ADMIN / SYSTEM_ADMIN：两个码都授
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN')
   AND p.code IN ('stall.qr.batch.view','stall.qr.self.view');

-- 2. PROPERTY_MANAGER（商管）：只授自助下载，不给批量导出
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.code = 'PROPERTY_MANAGER'
   AND p.code = 'stall.qr.self.view';
