-- S5：任务中心接入 owner_type=DEPT/ROLE/USER（岗位即角色）+ 归属快照 + 下发模式 + 认领
--
-- 1. inspection_plan：任务归属（USER/ROLE/DEPT）、下发模式（UNIT/PER_CHILD）、认领模式（NONE/CLAIM/AUTO）
-- 2. inspection_task：来源类型、归属快照、认领人、指派状态
-- 3. 快照口径（§12.7）：任务落库时固化归属，组织调整后历史与统计不反查当前部门树；
--    只有未完成任务允许管理员“重新归属”，审计由 AuditMutationFilter 自动写入 audit_log。
-- 4. owner_ancestors 为“含自身”的物化路径，如 /9/6/15/，用于认领时判断用户是否在部门子树内。

ALTER TABLE inspection_plan
  ADD COLUMN owner_type VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER assignee_name,
  ADD COLUMN owner_id BIGINT NULL AFTER owner_type,
  ADD COLUMN target_mode VARCHAR(16) NOT NULL DEFAULT 'UNIT' AFTER owner_id,
  ADD COLUMN claim_mode VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER target_mode,
  ADD KEY idx_inspection_plan_owner (owner_type, owner_id);

-- 存量计划：负责人即个人归属
UPDATE inspection_plan SET owner_id = assignee_user_id WHERE owner_id IS NULL AND assignee_user_id IS NOT NULL;

ALTER TABLE inspection_task
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'PLAN' AFTER status,
  ADD COLUMN owner_type VARCHAR(16) NULL AFTER source_type,
  ADD COLUMN owner_id BIGINT NULL AFTER owner_type,
  ADD COLUMN owner_name VARCHAR(128) NULL AFTER owner_id,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_name,
  ADD COLUMN owner_dept_name VARCHAR(128) NULL AFTER owner_dept_id,
  ADD COLUMN owner_ancestors VARCHAR(512) NULL AFTER owner_dept_name,
  ADD COLUMN claimed_by VARCHAR(64) NULL AFTER owner_ancestors,
  ADD COLUMN claimed_at DATETIME(3) NULL AFTER claimed_by,
  ADD COLUMN assignee_status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED' AFTER claimed_at,
  ADD KEY idx_inspection_task_source (source_type, scheduled_at),
  ADD KEY idx_inspection_task_owner (owner_type, owner_id, status),
  ADD KEY idx_inspection_task_owner_dept (owner_dept_id, status);

-- 存量任务：从计划继承归属；个人归属回填负责人姓名
UPDATE inspection_task k JOIN inspection_plan p ON p.id = k.plan_id
SET k.owner_type = p.owner_type,
    k.owner_id = p.owner_id,
    k.owner_name = CASE WHEN p.owner_type = 'USER' THEN COALESCE(p.assignee_name, k.assignee_name) ELSE NULL END;
