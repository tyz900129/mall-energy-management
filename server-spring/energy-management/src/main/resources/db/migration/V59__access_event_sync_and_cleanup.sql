ALTER TABLE access_device
  ADD COLUMN event_sync_cursor_at DATETIME(3) AFTER last_sync_at,
  ADD COLUMN last_event_sync_at DATETIME(3) AFTER event_sync_cursor_at,
  ADD COLUMN last_event_sync_status VARCHAR(24) AFTER last_event_sync_at,
  ADD COLUMN last_event_sync_message VARCHAR(500) AFTER last_event_sync_status;

ALTER TABLE access_event
  ADD COLUMN snapshot_mime_type VARCHAR(64) AFTER snapshot_path;

INSERT INTO sys_permission(code,name,module_code,module_name,action_type,sort_order) VALUES
('access.event.sync','同步门禁通行记录','access','门禁管理','EDIT',1020),
('access.test-data.delete','彻底删除门禁测试申请','access','门禁管理','DANGER',1021);

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='SUPER_ADMIN' AND p.code IN ('access.event.sync','access.test-data.delete');

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code='SYSTEM_ADMIN' AND p.code='access.event.sync';
