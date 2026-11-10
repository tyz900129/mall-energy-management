-- S3：组织架构管理（树 CRUD / 移动 / 停用 / 软删除）
--
-- 1. dept_move_log：移动层级审计（谁在什么时候把哪个部门从哪移到哪）
-- 2. 新增组织管理权限码：system.dept.view / system.dept.manage
--
-- 说明：部门删除一律软删除（deleted_at 置位 + 停用），不物理删除，
-- 因为 sys_user / ops_patrol_* / qr_inspection_plan / sys_department_webhook 等按 id 引用。

CREATE TABLE IF NOT EXISTS dept_move_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  department_id BIGINT NOT NULL COMMENT '被移动的部门',
  from_parent_id BIGINT NULL,
  to_parent_id BIGINT NULL,
  from_ancestors VARCHAR(512) NOT NULL,
  to_ancestors VARCHAR(512) NOT NULL,
  operator_username VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_dept_move_log_dept (department_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门层级移动审计';

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('system.dept.view', '查看组织架构', 'system', '系统管理', 'VIEW', 890, '组织架构树查看')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('system.dept.manage', '维护组织架构', 'system', '系统管理', 'EDIT', 891, '新增/编辑/移动/停用/删除部门')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);
