-- 服务管理：人员名单 + 参与人快照改造
-- 目的：经办人/责任商管不再强依赖系统账号，历史数据可直接导入；账号注销/离职后客诉仍按姓名快照正常展示。

CREATE TABLE cs_staff (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  staff_type     VARCHAR(16)  NOT NULL COMMENT 'HANDLER=经办人/客服员，MANAGER=责任商管',
  staff_name     VARCHAR(64)  NOT NULL COMMENT '姓名（客诉参与人快照来源）',
  department     VARCHAR(64)  NULL COMMENT '部门/岗位',
  phone          VARCHAR(32)  NULL,
  source_user_id BIGINT       NULL COMMENT '可选：关联 sys_user.id，仅用于铺位责任商管自动带出',
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  sort_order     INT          NOT NULL DEFAULT 0,
  remark         VARCHAR(256) NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cs_staff_name (staff_type, staff_name),
  KEY idx_cs_staff_user (source_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务管理-人员名单';

-- 参与人：允许无账号人员；新增名单关联与快照
ALTER TABLE cs_complaint_participant MODIFY user_id BIGINT NULL COMMENT 'sys_user.id（可空，仅账号关联）';
ALTER TABLE cs_complaint_participant ADD COLUMN staff_id BIGINT NULL COMMENT 'cs_staff.id（名单来源）' AFTER complaint_id;
ALTER TABLE cs_complaint_participant DROP INDEX uk_participant;
ALTER TABLE cs_complaint_participant ADD KEY idx_participant_complaint (complaint_id, participant_type);
ALTER TABLE cs_complaint_participant ADD KEY idx_participant_staff (staff_id);

-- 用现有系统账号初始化客服员名单
INSERT IGNORE INTO cs_staff (staff_type, staff_name, source_user_id, status, remark)
SELECT 'HANDLER', COALESCE(NULLIF(u.display_name, ''), u.username), u.id,
       CASE WHEN u.status = 1 THEN 'ACTIVE' ELSE 'INACTIVE' END, '系统账号初始化'
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
WHERE r.code IN ('CS_AGENT', 'CS_MANAGER');

-- 用铺位责任商管初始化责任商管名单
INSERT IGNORE INTO cs_staff (staff_type, staff_name, source_user_id, status, remark)
SELECT 'MANAGER', COALESCE(NULLIF(u.display_name, ''), u.username), u.id,
       CASE WHEN u.status = 1 THEN 'ACTIVE' ELSE 'INACTIVE' END, '责任商管初始化'
FROM operation_merchant_responsibility r
JOIN sys_user u ON u.id = r.responsible_user_id
WHERE r.status = 'ACTIVE';

-- 历史客诉参与人补录名单（含已注销账号的人员）
INSERT IGNORE INTO cs_staff (staff_type, staff_name, source_user_id, status, remark)
SELECT participant_type, user_name, user_id, 'ACTIVE', '历史客诉补录'
FROM cs_complaint_participant
WHERE user_name IS NOT NULL AND user_name <> '';

-- 回填历史参与人的名单 ID
UPDATE cs_complaint_participant p
JOIN cs_staff s ON s.staff_name = p.user_name AND s.staff_type = p.participant_type
SET p.staff_id = s.id
WHERE p.staff_id IS NULL;

-- 名单维护权限
INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description) VALUES
('complaint.staff.manage', '客诉人员名单维护', 'complaint', '服务管理', 'manage', 40, '维护客服员与责任商管名单')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_name = VALUES(module_name), description = VALUES(description);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'CS_MANAGER', 'CS_AGENT')
  AND p.code = 'complaint.staff.manage';
