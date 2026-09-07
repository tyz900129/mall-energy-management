CREATE TABLE IF NOT EXISTS device_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,group_name VARCHAR(100) NOT NULL,color VARCHAR(16) NOT NULL DEFAULT '#409EFF',sort_order INT NOT NULL DEFAULT 0,remark VARCHAR(255),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),UNIQUE KEY uk_device_group_name (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS device_group_assignment (
  platform_device_id VARCHAR(64) PRIMARY KEY,group_id BIGINT NOT NULL,assigned_by BIGINT,assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),KEY idx_dga_group (group_id),CONSTRAINT fk_dga_group FOREIGN KEY (group_id) REFERENCES device_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS device_energy_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,product_id VARCHAR(64) NOT NULL,energy_type VARCHAR(16) NOT NULL,identifier VARCHAR(64) NOT NULL,identifier_name VARCHAR(128) NOT NULL,value_mode VARCHAR(16) NOT NULL DEFAULT 'USAGE',enabled TINYINT NOT NULL DEFAULT 1,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),UNIQUE KEY uk_dem_product_energy (product_id,energy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS energy_usage_sync_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,batch_no VARCHAR(32) NOT NULL,start_date DATE NOT NULL,end_date DATE NOT NULL,group_id BIGINT,requested INT NOT NULL DEFAULT 0,succeeded INT NOT NULL DEFAULT 0,failed INT NOT NULL DEFAULT 0,status VARCHAR(16) NOT NULL,error_message TEXT,started_at DATETIME(3) NOT NULL,completed_at DATETIME(3),trigger_user_id BIGINT,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),UNIQUE KEY uk_eusb_batch_no (batch_no),KEY idx_eusb_started (started_at),KEY idx_eusb_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS energy_usage_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,platform_device_id VARCHAR(64) NOT NULL,usage_date DATE NOT NULL,energy_type VARCHAR(16) NOT NULL,identifier VARCHAR(64) NOT NULL,identifier_name VARCHAR(128),usage_value DECIMAL(20,6),meter_reading DECIMAL(20,6),value_mode VARCHAR(16) NOT NULL DEFAULT 'USAGE',sync_batch_id BIGINT NOT NULL,synced_at DATETIME(3) NOT NULL,raw_json JSON,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),UNIQUE KEY uk_eud_device_date_metric (platform_device_id,usage_date,identifier),KEY idx_eud_date (usage_date),KEY idx_eud_energy_date (energy_type,usage_date),KEY idx_eud_batch (sync_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS analysis_dimension (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dimension_code VARCHAR(64) NOT NULL,
  dimension_name VARCHAR(100) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_analysis_dimension_code (dimension_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用量分析维度';

CREATE TABLE IF NOT EXISTS analysis_dimension_value (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dimension_id BIGINT NOT NULL,
  value_code VARCHAR(64) NOT NULL,
  value_name VARCHAR(100) NOT NULL,
  color VARCHAR(16) NOT NULL DEFAULT '#409EFF',
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dimension_value_code (dimension_id,value_code),
  KEY idx_dimension_value_order (dimension_id,status,sort_order),
  CONSTRAINT fk_dimension_value_dimension FOREIGN KEY (dimension_id) REFERENCES analysis_dimension(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用量分析维度值';

CREATE TABLE IF NOT EXISTS device_dimension_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_device_id VARCHAR(64) NOT NULL,
  dimension_id BIGINT NOT NULL,
  dimension_value_id BIGINT NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE NULL,
  assigned_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_device_dimension_start (platform_device_id,dimension_id,effective_from),
  KEY idx_dda_dimension_period (dimension_id,effective_from,effective_to),
  KEY idx_dda_value_period (dimension_value_id,effective_from,effective_to),
  CONSTRAINT fk_dda_dimension FOREIGN KEY (dimension_id) REFERENCES analysis_dimension(id),
  CONSTRAINT fk_dda_value FOREIGN KEY (dimension_value_id) REFERENCES analysis_dimension_value(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分析维度归属历史';

INSERT INTO analysis_dimension(dimension_code,dimension_name,sort_order,status,remark)
VALUES ('MERCHANT_TYPE','商户类型',10,1,'用于区分不同经营主体的用量')
ON DUPLICATE KEY UPDATE dimension_name=VALUES(dimension_name),status=1;

INSERT INTO analysis_dimension_value(dimension_id,value_code,value_name,color,sort_order,status)
SELECT id,'CHAIN_MERCHANT','连营商户','#409EFF',10,1 FROM analysis_dimension WHERE dimension_code='MERCHANT_TYPE'
UNION ALL SELECT id,'SELF_OPERATED','自营商户','#67C23A',20,1 FROM analysis_dimension WHERE dimension_code='MERCHANT_TYPE'
UNION ALL SELECT id,'SUPERMARKET','超市','#E6A23C',30,1 FROM analysis_dimension WHERE dimension_code='MERCHANT_TYPE'
UNION ALL SELECT id,'HEADQUARTERS','总部','#F56C6C',40,1 FROM analysis_dimension WHERE dimension_code='MERCHANT_TYPE'
UNION ALL SELECT id,'HOTEL','酒店','#9B59B6',50,1 FROM analysis_dimension WHERE dimension_code='MERCHANT_TYPE'
ON DUPLICATE KEY UPDATE value_name=VALUES(value_name),color=VALUES(color),status=1;

INSERT INTO analysis_dimension(dimension_code,dimension_name,sort_order,status,remark)
VALUES ('DEVICE_GROUP','原设备分组',900,1,'由原有本地设备分组自动迁移')
ON DUPLICATE KEY UPDATE dimension_name=VALUES(dimension_name),status=1;

INSERT INTO analysis_dimension_value(dimension_id,value_code,value_name,color,sort_order,status,remark)
SELECT d.id,CONCAT('GROUP_',g.id),g.group_name,g.color,g.sort_order,1,g.remark
FROM device_group g JOIN analysis_dimension d ON d.dimension_code='DEVICE_GROUP'
ON DUPLICATE KEY UPDATE value_name=VALUES(value_name),color=VALUES(color);

INSERT INTO device_dimension_assignment(platform_device_id,dimension_id,dimension_value_id,effective_from)
SELECT a.platform_device_id,d.id,v.id,'1970-01-01'
FROM device_group_assignment a
JOIN analysis_dimension d ON d.dimension_code='DEVICE_GROUP'
JOIN analysis_dimension_value v ON v.dimension_id=d.id AND v.value_code=CONCAT('GROUP_',a.group_id)
ON DUPLICATE KEY UPDATE dimension_value_id=VALUES(dimension_value_id);
