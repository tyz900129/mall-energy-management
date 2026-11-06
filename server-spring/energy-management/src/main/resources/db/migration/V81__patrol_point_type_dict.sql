-- 点位类型字典化：可在「巡场管理 - 分类字典 - 点位类型」中维护
INSERT IGNORE INTO business_dictionary (dict_type, item_value, item_label, sort_order, status, remark)
VALUES
  ('PATROL_POINT_TYPE', '公共区域', '公共区域', 1, 1, '大堂、走廊、中庭等公共部位'),
  ('PATROL_POINT_TYPE', '设备间', '设备间', 2, 1, '配电房、水泵房、空调机房等设备用房'),
  ('PATROL_POINT_TYPE', '消防通道', '消防通道', 3, 1, '疏散通道、安全出口、消防车道'),
  ('PATROL_POINT_TYPE', '停车场', '停车场', 4, 1, '地下车库、地面停车区');

-- 历史点位类型编码转换为中文名称，保持与字典一致
UPDATE patrol_point SET point_type = '公共区域' WHERE point_type = 'PUBLIC_AREA';
UPDATE patrol_point SET point_type = '设备间' WHERE point_type = 'EQUIPMENT_ROOM';
UPDATE patrol_point SET point_type = '消防通道' WHERE point_type = 'FIRE_PASS';
UPDATE patrol_point SET point_type = '停车场' WHERE point_type = 'PARKING';
UPDATE patrol_point SET point_type = '公共区域' WHERE point_type IS NULL OR TRIM(point_type) = '';

-- 菜单改名，页面同时维护问题分类与点位类型
UPDATE sys_menu SET name = '分类字典' WHERE code = 'patrol:categories';
