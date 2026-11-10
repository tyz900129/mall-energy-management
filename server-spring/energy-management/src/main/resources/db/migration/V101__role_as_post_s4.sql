-- S4：岗位即角色（Role-as-Post）
--
-- 岗位不再单独建表，由 sys_role 承载：
--   dept_id        岗位所属部门（通常挂在“班”层），用于组织树分组与默认分配
--   post_category  岗位类别：MANAGE/TECH/OPER/ADMIN，仅作分类标签
--   is_assignable  是否允许在用户管理里分配（取代代码中的 ASSIGNABLE_ROLE_CODES 白名单）
--   is_system      内置角色保护：不可删除、不可修改编码
--   headcount      编制数，0=不限
--
-- 说明：现有角色全部由历史迁移脚本创建，一律标记为内置角色；
-- 后续通过角色管理页新增的岗位 is_system=0，可删除、可改编码。

ALTER TABLE sys_role
  ADD COLUMN dept_id BIGINT NULL AFTER name,
  ADD COLUMN post_category VARCHAR(16) NOT NULL DEFAULT 'OPER' AFTER dept_id,
  ADD COLUMN is_assignable TINYINT NOT NULL DEFAULT 1 AFTER post_category,
  ADD COLUMN is_system TINYINT NOT NULL DEFAULT 0 AFTER is_assignable,
  ADD COLUMN headcount INT NOT NULL DEFAULT 0 AFTER is_system,
  ADD KEY idx_sys_role_dept (dept_id),
  ADD KEY idx_sys_role_assignable (is_assignable);

-- 现存角色均为迁移创建的预设角色
UPDATE sys_role SET is_system = 1;

-- 岗位类别
UPDATE sys_role SET post_category = 'ADMIN'  WHERE code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'AUDITOR');
UPDATE sys_role SET post_category = 'TECH'   WHERE code IN ('DEVICE_VERIFIER');
UPDATE sys_role SET post_category = 'MANAGE' WHERE code IN ('PROPERTY_MANAGER', 'OPS_PATROL_MANAGER', 'CS_MANAGER');
UPDATE sys_role SET post_category = 'OPER'   WHERE code IN ('APPLICANT', 'PLATFORM_OPERATOR', 'VIEWER', 'CS_AGENT');

-- 原 ASSIGNABLE_ROLE_CODES 白名单之外的角色不可分配
UPDATE sys_role SET is_assignable = 0 WHERE code IN ('PLATFORM_OPERATOR', 'AUDITOR');

-- 岗位归属部门（全局角色留空）
UPDATE sys_role r JOIN sys_department d ON d.code = 'PROP_DEPT'
SET r.dept_id = d.id WHERE r.code = 'PROPERTY_MANAGER';

UPDATE sys_role r JOIN sys_department d ON d.code = 'PROP_EQ'
SET r.dept_id = d.id WHERE r.code = 'DEVICE_VERIFIER';

UPDATE sys_role r JOIN sys_department d ON d.code = 'SM_DEPT'
SET r.dept_id = d.id WHERE r.code = 'OPS_PATROL_MANAGER';

UPDATE sys_role r JOIN sys_department d ON d.code = 'OPS_CS'
SET r.dept_id = d.id WHERE r.code IN ('CS_AGENT', 'CS_MANAGER');
