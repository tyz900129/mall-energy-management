-- 值班排班：备班由单人扩展为多人
CREATE TABLE patrol_duty_backup (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  duty_id BIGINT NOT NULL,
  user_id BIGINT,
  user_name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_patrol_duty_backup (duty_id, user_name),
  KEY idx_patrol_duty_backup_duty (duty_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 历史单人备班数据迁移到新表（原字段保留，便于回滚旧版本）
INSERT IGNORE INTO patrol_duty_backup (duty_id, user_id, user_name, sort_order)
SELECT id, backup_user_id, TRIM(backup_user_name), 0
FROM patrol_duty
WHERE backup_user_name IS NOT NULL AND TRIM(backup_user_name) <> '';

-- 巡场问题分类字典（可在「巡场管理 - 问题分类」或「基础字典」中维护）
INSERT IGNORE INTO business_dictionary (dict_type, item_value, item_label, sort_order, status, remark)
VALUES
  ('PATROL_ISSUE_CATEGORY', '设施设备', '设施设备', 1, 1, '电梯、照明、给排水、空调等设备类问题'),
  ('PATROL_ISSUE_CATEGORY', '环境卫生', '环境卫生', 2, 1, '地面、垃圾、异味等保洁类问题'),
  ('PATROL_ISSUE_CATEGORY', '消防安全', '消防安全', 3, 1, '消防通道、器材、标识等消防类问题'),
  ('PATROL_ISSUE_CATEGORY', '秩序安全', '秩序安全', 4, 1, '车辆停放、人员秩序、治安类问题'),
  ('PATROL_ISSUE_CATEGORY', '商户管理', '商户管理', 5, 1, '占道经营、闭店、违规装修等商户类问题'),
  ('PATROL_ISSUE_CATEGORY', '其他', '其他', 9, 1, '未归入以上分类的其他问题');

-- 巡场菜单新增「问题分类」维护入口
INSERT IGNORE INTO sys_menu (parent_id, code, name, path, icon, type, sort_order, enabled)
SELECT t.id, 'patrol:categories', '问题分类', '/patrol/categories', 'collection', 'MENU', 5, 1
FROM sys_menu t WHERE t.code = 'patrol';

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN','PROPERTY_MANAGER') AND m.code = 'patrol:categories';
