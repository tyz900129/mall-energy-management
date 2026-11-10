-- S1：组织架构树化（部 / 处·课 / 班）+ 主数据补齐
-- 原则：现有 8 条记录的 id、code、name、dept_group 一律不动；只追加列与新节点。
-- ancestors 物化路径（不含自身）：根节点为 "/"，子节点为 <父ancestors><父id>/
-- 例：物业部(9)="/"、设备课(6)="/9/"、强电班(15)="/9/6/"

ALTER TABLE sys_department
  ADD COLUMN parent_id BIGINT NULL AFTER code,
  ADD COLUMN ancestors VARCHAR(512) NOT NULL DEFAULT '' AFTER parent_id,
  ADD COLUMN depth TINYINT NOT NULL DEFAULT 0 AFTER ancestors,
  ADD COLUMN dept_level VARCHAR(16) NOT NULL DEFAULT 'SECTION' AFTER depth,
  ADD COLUMN code_locked TINYINT NOT NULL DEFAULT 0 AFTER dept_level,
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER enabled,
  ADD COLUMN deleted_at DATETIME(3) NULL AFTER status,
  ADD KEY idx_sys_department_parent (parent_id, sort_order),
  ADD KEY idx_sys_department_ancestors (ancestors),
  ADD KEY idx_sys_department_status (status);

-- 现有 8 条：先按根处理，随后按实际层级回填
UPDATE sys_department SET ancestors = '/', depth = 0, dept_level = 'SECTION',
  code_locked = 1, status = CASE WHEN enabled = 1 THEN 'ACTIVE' ELSE 'DISABLED' END;

-- ============ 根节点（部） ============
INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PROP_DEPT', '物业部', 'PROPERTY', NULL, '/', 10, 1, 0, 'GROUP', 1, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PROP_DEPT');

INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'SM_DEPT', '商管部', 'OPERATION', NULL, '/', 20, 1, 0, 'GROUP', 1, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'SM_DEPT');

INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PLAN_DEPT', '企划部', 'OPERATION', NULL, '/', 30, 1, 0, 'GROUP', 1, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PLAN_DEPT');

-- 企划部：上级 → 本级（不折叠）
INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PLAN_DEPT_LOCAL', '企划部（本级）', 'OPERATION', p.id, CONCAT(p.ancestors, p.id, '/'), 31, 1, p.depth + 1, 'SECTION', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'PLAN_DEPT' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PLAN_DEPT_LOCAL');

-- ============ 商管部：餐饮处 / 零售处 ============
INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'SM_CATER_G', '餐饮处', 'OPERATION', p.id, CONCAT(p.ancestors, p.id, '/'), 21, 1, p.depth + 1, 'SECTION', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'SM_DEPT' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'SM_CATER_G');

INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'SM_RETAIL_G', '零售处', 'OPERATION', p.id, CONCAT(p.ancestors, p.id, '/'), 22, 1, p.depth + 1, 'SECTION', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'SM_DEPT' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'SM_RETAIL_G');

-- 商管四处挂到餐饮处、一二处挂到零售处（只改层级列，code/id 不动）
UPDATE sys_department c JOIN sys_department p ON p.code = 'SM_CATER_G'
SET c.parent_id = p.id, c.depth = p.depth + 1, c.ancestors = CONCAT(p.ancestors, p.id, '/'), c.dept_level = 'SECTION'
WHERE c.code IN ('SM03', 'SM04');

UPDATE sys_department c JOIN sys_department p ON p.code = 'SM_RETAIL_G'
SET c.parent_id = p.id, c.depth = p.depth + 1, c.ancestors = CONCAT(p.ancestors, p.id, '/'), c.dept_level = 'SECTION'
WHERE c.code IN ('SM01', 'SM02');

-- 客服课挂到商管部
UPDATE sys_department c JOIN sys_department p ON p.code = 'SM_DEPT'
SET c.parent_id = p.id, c.depth = p.depth + 1, c.ancestors = CONCAT(p.ancestors, p.id, '/'), c.dept_level = 'SECTION'
WHERE c.code = 'OPS_CS';

-- ============ 物业部：设备课 / 安保课 / 保洁课 ============
UPDATE sys_department c JOIN sys_department p ON p.code = 'PROP_DEPT'
SET c.parent_id = p.id, c.depth = p.depth + 1, c.ancestors = CONCAT(p.ancestors, p.id, '/'), c.dept_level = 'SECTION'
WHERE c.code IN ('PROP_EQ', 'PROP_SEC', 'PROP_CLN');

-- 设备课下 3 个班（安保课/保洁课的班与岗位后期随业务新建，此处不建）
INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PROP_EQ_STRONG', '强电班', 'PROPERTY', p.id, CONCAT(p.ancestors, p.id, '/'), 61, 1, p.depth + 1, 'TEAM', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'PROP_EQ' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PROP_EQ_STRONG');

INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PROP_EQ_WEAK', '弱电班', 'PROPERTY', p.id, CONCAT(p.ancestors, p.id, '/'), 62, 1, p.depth + 1, 'TEAM', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'PROP_EQ' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PROP_EQ_WEAK');

INSERT INTO sys_department (code, name, dept_group, parent_id, ancestors, sort_order, enabled, depth, dept_level, code_locked, status)
SELECT 'PROP_EQ_HVAC', '暖通班', 'PROPERTY', p.id, CONCAT(p.ancestors, p.id, '/'), 63, 1, p.depth + 1, 'TEAM', 1, 'ACTIVE'
FROM sys_department p
WHERE p.code = 'PROP_EQ' AND NOT EXISTS (SELECT 1 FROM sys_department WHERE code = 'PROP_EQ_HVAC');
