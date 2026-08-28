CREATE TABLE business_dictionary (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_type   VARCHAR(32)  NOT NULL,
  item_value  VARCHAR(64)  NOT NULL,
  item_label  VARCHAR(64)  NOT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
  remark      VARCHAR(255),
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_business_dictionary_type_value (dict_type, item_value),
  KEY idx_business_dictionary_type_status (dict_type, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 将现有铺位数据自动纳入字典，保证升级后历史数据仍可选择。
INSERT IGNORE INTO business_dictionary(dict_type,item_value,item_label,sort_order)
SELECT 'FLOOR', floor_code, COALESCE(NULLIF(floor_name,''),floor_code), 0 FROM stall WHERE floor_code IS NOT NULL AND floor_code<>'';
INSERT IGNORE INTO business_dictionary(dict_type,item_value,item_label,sort_order)
SELECT 'AREA', area_name, area_name, 0 FROM stall WHERE area_name IS NOT NULL AND area_name<>'';
INSERT IGNORE INTO business_dictionary(dict_type,item_value,item_label,sort_order)
SELECT 'BUSINESS_TYPE', business_type, business_type, 0 FROM stall WHERE business_type IS NOT NULL AND business_type<>'';
INSERT IGNORE INTO business_dictionary(dict_type,item_value,item_label,sort_order)
SELECT 'BUSINESS_CATEGORY', business_category, business_category, 0 FROM stall WHERE business_category IS NOT NULL AND business_category<>'';

INSERT INTO sys_menu(parent_id,code,name,path,icon,type,sort_order,enabled)
SELECT id,'system:dictionary','基础字典','/system/dictionary','List','MENU',6,1 FROM sys_menu WHERE code='system';
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM sys_role r JOIN sys_menu m ON m.code='system:dictionary' WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN');
