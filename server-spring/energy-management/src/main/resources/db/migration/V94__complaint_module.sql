-- 服务管理（客诉管理）模块
-- 品牌/业态/楼层/商户复用主系统主数据；本模块仅补充非铺位品牌与客诉自有字典。

CREATE TABLE cs_brand (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  name             VARCHAR(128) NOT NULL COMMENT '品牌/品类名称',
  business_type    VARCHAR(64)  NULL COMMENT 'business_dictionary.BUSINESS_TYPE 的 item_value',
  floor_code       VARCHAR(32)  NULL,
  source           VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL=手工新增',
  active           TINYINT(1)   NOT NULL DEFAULT 1,
  remark           VARCHAR(512) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cs_brand_name (name, business_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-非铺位品牌补充';

CREATE TABLE cs_complaint (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_no      VARCHAR(24)   NOT NULL COMMENT 'CS-YYYYMM-NNN',
  occur_date        DATE          NOT NULL,
  stall_id          BIGINT        NULL COMMENT '关联铺位 stall.id',
  brand_id          BIGINT        NULL COMMENT 'cs_brand.id（非铺位主体时使用）',
  brand_name        VARCHAR(128)  NOT NULL COMMENT '品牌名称快照',
  merchant_id       BIGINT        NULL COMMENT 'merchant.id',
  merchant_name     VARCHAR(128)  NULL COMMENT '商户名称快照',
  business_type     VARCHAR(64)   NOT NULL COMMENT 'business_dictionary.BUSINESS_TYPE',
  business_category VARCHAR(64)   NULL COMMENT '细分品类',
  floor_code        VARCHAR(32)   NULL,
  category          VARCHAR(32)   NOT NULL COMMENT '客诉类别',
  channel           VARCHAR(32)   NULL,
  content           TEXT          NOT NULL,
  solution          TEXT          NULL,
  company_cost      DECIMAL(10,2) NOT NULL DEFAULT 0,
  merchant_cost     DECIMAL(10,2) NOT NULL DEFAULT 0,
  no_cost           TINYINT(1)    NOT NULL DEFAULT 0,
  on_site_handle    TINYINT(1)    NOT NULL DEFAULT 0,
  handle_days       INT           NULL,
  related_tags      VARCHAR(256)  NULL COMMENT '标签 JSON 数组',
  status            VARCHAR(16)   NOT NULL DEFAULT 'pending',
  satisfaction      VARCHAR(16)   NULL,
  closed_at         DATETIME      NULL,
  created_by        BIGINT        NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_complaint_no (complaint_no),
  KEY idx_status (status),
  KEY idx_occur_date (occur_date),
  KEY idx_business_type (business_type),
  KEY idx_stall (stall_id),
  KEY idx_brand (brand_id),
  KEY idx_category (category),
  KEY idx_floor (floor_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-客诉单';

CREATE TABLE cs_complaint_participant (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id     BIGINT      NOT NULL,
  user_id          BIGINT      NOT NULL COMMENT 'sys_user.id',
  user_name        VARCHAR(64) NOT NULL COMMENT '姓名快照',
  participant_type VARCHAR(16) NOT NULL COMMENT 'HANDLER=经办人 / MANAGER=责任商管',
  sort_order       INT         NOT NULL DEFAULT 0,
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_participant (complaint_id, user_id, participant_type),
  KEY idx_participant_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-客诉参与人';

CREATE TABLE cs_complaint_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT       NOT NULL,
  action        VARCHAR(32)  NOT NULL COMMENT 'create/update/status/close/import',
  from_status   VARCHAR(16)  NULL,
  to_status     VARCHAR(16)  NULL,
  operator_id   BIGINT       NULL,
  operator_name VARCHAR(64)  NULL,
  note          VARCHAR(512) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_log_complaint (complaint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-客诉流转日志';

CREATE TABLE cs_complaint_dict (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type  VARCHAR(32) NOT NULL COMMENT 'CATEGORY/CHANNEL/SATISFACTION/TAG/STATUS',
  item_value VARCHAR(64) NOT NULL,
  item_label VARCHAR(64) NOT NULL,
  color      VARCHAR(16) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled    TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uk_cs_dict (dict_type, item_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-字典';

INSERT IGNORE INTO cs_complaint_dict (dict_type, item_value, item_label, color, sort_order) VALUES
('CATEGORY','SERVICE','服务',NULL,1),
('CATEGORY','SKILL','员工技能',NULL,2),
('CATEGORY','RETURN','不满意退换货',NULL,3),
('CATEGORY','MEMBER_REFUND','会员退费',NULL,4),
('CATEGORY','PRICE','价格',NULL,5),
('CATEGORY','QUALITY','品质',NULL,6),
('CATEGORY','SAFETY','安全','#F56C6C',7),
('CATEGORY','FOREIGN_OBJECT','异物',NULL,8),
('CATEGORY','SIGNAGE','标示',NULL,9),
('CATEGORY','PUBLIC_OPINION','舆论',NULL,10),
('CATEGORY','WAGE_DISPUTE','员工工资纠纷',NULL,11),
('CATEGORY','OTHER','其他',NULL,12),
('CHANNEL','ON_SITE','服务台现场',NULL,1),
('CHANNEL','PHONE','电话',NULL,2),
('CHANNEL','MINI_PROGRAM','小程序后台',NULL,3),
('CHANNEL','REGULATOR','市场监管转办',NULL,4),
('CHANNEL','OTHER','其他',NULL,5),
('SATISFACTION','SATISFIED','满意','#67C23A',1),
('SATISFACTION','NEUTRAL','一般','#E6A23C',2),
('SATISFACTION','DISSATISFIED','不满意','#F56C6C',3),
('TAG','LATE_CHECKOUT','晚清',NULL,1),
('TAG','POS','POS',NULL,2),
('TAG','CONSTRUCTION','施工问题',NULL,3),
('TAG','OFFICIAL_LETTER','公函',NULL,4),
('TAG','SALES_ACHIEVEMENT','销售达成',NULL,5),
('TAG','AWARD','评优评先',NULL,6),
('TAG','RENT','租金',NULL,7),
('TAG','CONTRACT_NEGOTIATION','合同谈判',NULL,8),
('STATUS','pending','待处理','#E6A23C',1),
('STATUS','processing','处理中','#409EFF',2),
('STATUS','resolved','已解决','#67C23A',3),
('STATUS','closed','已关闭','#909399',4);

-- 业态字典补齐客诉需要的两项（已存在则跳过）
INSERT IGNORE INTO business_dictionary (dict_type, item_value, item_label, sort_order, status)
VALUES ('BUSINESS_TYPE','PROPERTY','物业',10,1),
       ('BUSINESS_TYPE','SELF_OPERATED','自营主力',11,1);

-- 权限码（模块：服务管理）
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description) VALUES
('complaint.view','客诉查询','complaint','服务管理','VIEW',2001,'查看客诉列表与详情'),
('complaint.create','客诉登记','complaint','服务管理','EDIT',2002,'登记客诉、上传现场照片'),
('complaint.update','客诉处理','complaint','服务管理','EDIT',2003,'编辑客诉、流转状态'),
('complaint.close','客诉关闭','complaint','服务管理','EDIT',2004,'关闭客诉并记录满意度'),
('complaint.delete','客诉删除','complaint','服务管理','DANGER',2005,'删除客诉单据'),
('complaint.import','客诉导入','complaint','服务管理','EDIT',2006,'导入历史客诉数据'),
('complaint.export','客诉导出','complaint','服务管理','VIEW',2007,'导出客诉明细与报表'),
('complaint.stats.view','客诉报表','complaint','服务管理','VIEW',2008,'查看客诉多维报表'),
('complaint.brand.manage','品牌补充维护','complaint','服务管理','EDIT',2009,'维护非铺位品牌')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

-- 客服员：登记、本人处理、导出与报表
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'CS_AGENT'
  AND p.code IN ('complaint.view','complaint.create','complaint.update','complaint.export','complaint.stats.view');

-- 客服主管：在客服员基础上增加关闭、导入、品牌维护
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'CS_MANAGER'
  AND p.code IN ('complaint.view','complaint.create','complaint.update','complaint.close',
                 'complaint.import','complaint.export','complaint.stats.view','complaint.brand.manage');

-- 商管人员：查询本人相关单据、处理与报表
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT'
  AND p.code IN ('complaint.view','complaint.update','complaint.stats.view');

-- 物业管理员与营运巡场主管可查询与报表
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('PROPERTY_MANAGER','OPS_PATROL_MANAGER')
  AND p.code IN ('complaint.view','complaint.stats.view');
