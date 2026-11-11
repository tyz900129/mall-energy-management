-- 客诉更新导入：新增「强制修正已关闭单据费用」权限
--
-- 背景：
--   更新导入默认拒绝 resolved/closed 单据。历史数据修正场景需要放开状态保护，
--   但仅限费用三列（company_cost / merchant_cost / no_cost），不得改动状态、
--   关闭时间、处理天数与参与人。
--
-- 说明：
--   1. 该权限为高风险（DANGER），仅授予超级管理员与系统管理员；
--      客服主管（CS_MANAGER）需在「用户与权限 → 角色管理」中按需勾选，不在迁移里写死。
--   2. 接口侧要求同时具备 complaint.import 与 complaint.import.force，
--      仅具备前者时返回 403。
--   3. 迁移版本 V106，衔接 V105，不跳号。

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('complaint.import.force', '客诉导入-已关闭单据修正', 'complaint', '服务管理', 'DANGER', 2010,
        '更新导入时允许修正已解决/已关闭单据的费用（仅费用三列，不改状态、关闭时间、处理天数与参与人）')
ON DUPLICATE KEY UPDATE name = VALUES(name), module_code = VALUES(module_code), module_name = VALUES(module_name),
  action_type = VALUES(action_type), sort_order = VALUES(sort_order), description = VALUES(description);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
  AND p.code = 'complaint.import.force';
