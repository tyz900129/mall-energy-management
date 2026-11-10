-- S2：部门权限裁剪策略化（替代 PermissionService 中的硬编码）
--
-- 语义（对应方案 §12.6）：
--   1. 一行 = 一条白名单规则；mode=PREFIX 按前缀匹配、mode=EXACT 按权限码精确匹配。
--   2. 单部门内多行取并集：任一 include 命中即保留；任一 exclude 命中即剔除。
--   3. 解析顺序：从用户所属部门沿 ancestors 向上，取"最近一个存在启用策略行的部门"，
--      整组生效（不与祖先策略合并）；没有策略行 = 不裁剪（商管处/客服课保持现状）。
--   4. 因此新建的班（强电班/弱电班/暖通班）自动继承设备课的裁剪范围，不再越权。
--
-- 本迁移把原有 3 条硬编码规则（PROP_EQ / PROP_SEC / PROP_CLN）等值迁入，行为不变。
-- 依赖：V98 已为 sys_department 补齐 ancestors。

CREATE TABLE IF NOT EXISTS sys_dept_permission_policy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  department_id BIGINT NOT NULL COMMENT '所属部门（sys_department.id）',
  mode VARCHAR(16) NOT NULL DEFAULT 'PREFIX' COMMENT 'PREFIX=前缀匹配 / EXACT=权限码精确匹配',
  include_pattern VARCHAR(128) NOT NULL COMMENT '命中的权限码前缀或完整权限码',
  exclude_pattern VARCHAR(128) NULL COMMENT '命中后剔除的前缀（优先级高于 include）',
  enabled TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dept_policy (department_id, mode, include_pattern),
  KEY idx_dept_policy_dept (department_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='部门权限裁剪策略：白名单式，按组织树最近祖先继承';

-- 迁移原硬编码规则
INSERT IGNORE INTO sys_dept_permission_policy (department_id, mode, include_pattern, exclude_pattern, enabled, remark)
SELECT d.id, v.mode, v.include_pattern, v.exclude_pattern, 1, v.remark
FROM sys_department d
JOIN (
  -- 设备课：巡检、能源、巡场（排除配置）、门禁认领、施工访问
  SELECT 'PROP_EQ' AS dept_code, 'PREFIX' AS mode, 'inspection.' AS include_pattern, CAST(NULL AS CHAR(128)) AS exclude_pattern, '设备课：设备巡检' AS remark
  UNION ALL SELECT 'PROP_EQ', 'PREFIX', 'energy.', NULL, '设备课：能源管理'
  UNION ALL SELECT 'PROP_EQ', 'PREFIX', 'patrol.', 'patrol.config', '设备课：巡场（排除配置）'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'access.self.view', NULL, '设备课：门禁认领'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'access.self.manage', NULL, '设备课：门禁认领'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'access.event.view.self', NULL, '设备课：门禁认领'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'visitor.view.own', NULL, '设备课：本人访客'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'stall.view', NULL, '设备课：铺位查看'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'construction.create', NULL, '设备课：施工申请'
  UNION ALL SELECT 'PROP_EQ', 'EXACT', 'construction.view', NULL, '设备课：施工查看'

  -- 安保课：巡场、放行复核、物业查看、门禁认领、施工访问
  UNION ALL SELECT 'PROP_SEC', 'PREFIX', 'patrol.', 'patrol.config', '安保课：巡场（排除配置）'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'access.self.view', NULL, '安保课：门禁认领'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'access.self.manage', NULL, '安保课：门禁认领'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'access.event.view.self', NULL, '安保课：门禁认领'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'visitor.view.own', NULL, '安保课：本人访客'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'operation.clearance.review', NULL, '安保课：清场复核'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'operation.property.view', NULL, '安保课：物业查看'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'stall.view', NULL, '安保课：铺位查看'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'construction.create', NULL, '安保课：施工申请'
  UNION ALL SELECT 'PROP_SEC', 'EXACT', 'construction.view', NULL, '安保课：施工查看'

  -- 保洁课：商户查看、巡场、门禁认领、施工访问
  UNION ALL SELECT 'PROP_CLN', 'PREFIX', 'patrol.', 'patrol.config', '保洁课：巡场（排除配置）'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'access.self.view', NULL, '保洁课：门禁认领'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'access.self.manage', NULL, '保洁课：门禁认领'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'access.event.view.self', NULL, '保洁课：门禁认领'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'visitor.view.own', NULL, '保洁课：本人访客'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'operation.merchant.view', NULL, '保洁课：商户查看'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'stall.view', NULL, '保洁课：铺位查看'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'construction.create', NULL, '保洁课：施工申请'
  UNION ALL SELECT 'PROP_CLN', 'EXACT', 'construction.view', NULL, '保洁课：施工查看'
) v ON v.dept_code = d.code;
