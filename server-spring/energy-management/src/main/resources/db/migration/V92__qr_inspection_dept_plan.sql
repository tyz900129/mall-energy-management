-- V92 二维码巡检重构
-- 1) 周期巡检改为「部门月度计划」，到期推送卡片到部门群
-- 2) 联合巡检不预选铺位，现场扫码/搜索随机巡检
-- 3) 模板不再版本化，提交时把表单结构快照写入记录，历史数据保持不变

-- ============== 1. 周期计划：部门级月度计划 ==============
DROP TABLE IF EXISTS qr_inspection_plan;
CREATE TABLE qr_inspection_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene           VARCHAR(32) NOT NULL,       -- WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY
    dept_code       VARCHAR(32) NOT NULL,
    dept_id         BIGINT NOT NULL,
    template_id     BIGINT,                     -- 为空时按场景+角色自动匹配
    day_of_month    TINYINT NOT NULL DEFAULT 1, -- 每月几号（1-31，遇短月自动取月末）
    push_time       VARCHAR(5) NOT NULL DEFAULT '09:00',
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    last_run_date   DATE,
    next_due_at     DATETIME,
    remark          VARCHAR(255),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_next (next_due_at, enabled),
    KEY idx_dept (scene, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 2. 计划轮次（每推送一次生成一条） ==============
CREATE TABLE IF NOT EXISTS qr_inspection_plan_round (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         BIGINT NOT NULL,
    due_date        DATE NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / COMPLETED
    pushed_at       DATETIME,
    completed_at    DATETIME,
    record_count    INT NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan_date (plan_id, due_date),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 3. 巡检记录：轮次 + 表单快照 + 铺位快照 ==============
ALTER TABLE qr_inspection_record
    ADD COLUMN plan_round_id BIGINT NULL AFTER plan_id,
    ADD COLUMN template_snapshot JSON NULL AFTER template_version,
    ADD COLUMN stall_code VARCHAR(64) NULL AFTER stall_id,
    ADD COLUMN stall_name VARCHAR(200) NULL AFTER stall_code,
    ADD COLUMN dept_code VARCHAR(32) NULL AFTER submitter_dept,
    MODIFY COLUMN template_version INT NULL;

CREATE INDEX idx_round ON qr_inspection_record(plan_round_id);

-- ============== 4. 联合巡检：参与部门（不再预选铺位） ==============
ALTER TABLE qr_inspection_task
    ADD COLUMN participating_dept_codes VARCHAR(255) NULL AFTER template_version,
    MODIFY COLUMN template_version INT NULL;

-- ============== 5. 联合巡检实际巡检铺位 ==============
ALTER TABLE qr_inspection_task_target
    ADD COLUMN record_id BIGINT NULL,
    MODIFY COLUMN selected_dept_code VARCHAR(64) NULL;

CREATE INDEX idx_task_stall ON qr_inspection_task_target(task_id, stall_id);

-- ============== 6. 补齐 webhook 事件类型（商管二~四处群） ==============
UPDATE webhook_config
SET event_types = CASE
    WHEN event_types IS NULL OR event_types = '' THEN 'QR_INSPECTION_DUE,QR_INSPECTION_ISSUE,QR_JOINT_CLOSED'
    WHEN INSTR(event_types, 'QR_INSPECTION_DUE') = 0
        THEN CONCAT(event_types, ',QR_INSPECTION_DUE,QR_INSPECTION_ISSUE,QR_JOINT_CLOSED')
    ELSE event_types
END
WHERE id IN (13,14,15,16);
