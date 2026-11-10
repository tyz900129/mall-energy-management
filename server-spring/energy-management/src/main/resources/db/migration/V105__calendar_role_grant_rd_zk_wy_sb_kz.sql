-- 角色授权：中控岗（RD_ZK）、设备课长（WY_SB_KZ）开通日历任务查看与回执
--
-- 说明：
--   1. 两个角色当前均已有 inspection.* 执行/查看类权限，补 calendar.view / calendar.execute
--      属于同类岗位的自然延伸，不涉及计划管理与报表。
--   2. 部门裁剪：WY_SB_KZ 属 PROP_EQ、RD_ZK 属 PROP_EQ_WEAK（祖先链 /9/6/），
--      两者都会命中 V103 为 PROP_EQ 增加的 calendar. 前缀策略，无需单独加部门策略。
--   3. 后续新增角色请在「用户与权限 → 角色管理」的权限矩阵里勾选，不要继续在迁移里写死角色码。

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code IN ('RD_ZK', 'WY_SB_KZ')
  AND p.code IN ('calendar.view', 'calendar.execute');

-- 设备课长额外获得计划管理与报表（按业务确认）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'WY_SB_KZ'
  AND p.code IN ('calendar.manage', 'calendar.report');
