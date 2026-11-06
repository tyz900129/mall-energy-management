-- V91 二维码巡检（仓库周期 + 联合巡检）完整模型
-- 场景：WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY / JOINT

-- ============== 部门群绑定 ==============
-- sys_department 没有 webhook_config_id，新增关联表（一个部门可绑多个群，适配双端推送）
CREATE TABLE IF NOT EXISTS sys_department_webhook (
    department_id   BIGINT NOT NULL,
    webhook_config_id BIGINT NOT NULL,
    bind_type       VARCHAR(32) NOT NULL DEFAULT 'PRIMARY', -- PRIMARY 主群 / CC 抄送群
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (department_id, webhook_config_id, bind_type),
    KEY idx_webhook (webhook_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 模板（场景 + 角色/部门维度） ==============
CREATE TABLE IF NOT EXISTS qr_inspection_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene           VARCHAR(32) NOT NULL, -- WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY / JOINT
    name            VARCHAR(100) NOT NULL,
    role_group      VARCHAR(32),          -- COMMERCIAL / PROPERTY（联合巡检可为空）
    dept_code       VARCHAR(32),          -- 可按部门进一步细分，共用模板时可为空
    version         INT NOT NULL DEFAULT 1,
    fields_json     JSON NOT NULL,         -- [{code,label,controlType,options,normalValue,requirePhotoOnAbnormal,required,enabled,sortOrder,category,responsibleDeptCode}]
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    remark          VARCHAR(255),
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_scene (scene, role_group, dept_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 周期计划（仓库专用，每仓一条） ==============
CREATE TABLE IF NOT EXISTS qr_inspection_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene           VARCHAR(32) NOT NULL, -- WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY
    stall_id        BIGINT NOT NULL,
    department_id   BIGINT NOT NULL,       -- 责任部门（推送对象）
    frequency_type  VARCHAR(16) NOT NULL DEFAULT 'WEEKLY', -- DAILY / WEEKLY / MONTHLY
    frequency_value VARCHAR(64),           -- 如 WEEKLY: "3,5"（每周三四）；MONTHLY: "5,20"（每月5/20号）；DAILY: "9,15"（每天9/15点）
    next_due_at     DATETIME NOT NULL,
    deadline_hours  INT NOT NULL DEFAULT 24, -- 到期后多少小时视为逾期
    grace_hours     INT NOT NULL DEFAULT 0,  -- 推送后宽限期
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    last_run_at     DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_next (next_due_at, enabled),
    KEY idx_stall (stall_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 联合巡检任务 ==============
CREATE TABLE IF NOT EXISTS qr_inspection_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no         VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    start_at        DATETIME NOT NULL,
    end_at          DATETIME NOT NULL, -- 收集截止时间
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / CLOSED
    template_id     BIGINT NOT NULL,
    template_version INT NOT NULL,
    created_by      VARCHAR(64),
    created_by_name VARCHAR(64),
    closed_by       VARCHAR(64),
    closed_at       DATETIME,
    remark          VARCHAR(500),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 同时期只允许 1 个进行中的联合巡检
    CONSTRAINT one_active_joint UNIQUE (status, id) 
      -- 上面这个约束不直接生效（MySQL 不能用函数唯一），由服务层校验
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 用 generated column + 唯一索引实现"同时期仅 1 个 ACTIVE"
ALTER TABLE qr_inspection_task ADD COLUMN active_flag TINYINT(1) GENERATED ALWAYS AS (IF(status='ACTIVE',1,NULL)) STORED;
CREATE UNIQUE INDEX uk_joint_active ON qr_inspection_task(active_flag);

-- 抽检清单
CREATE TABLE IF NOT EXISTS qr_inspection_task_target (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    stall_id        BIGINT NOT NULL,
    selected_dept_code VARCHAR(32), -- 抽检所属业务部门（商管处/保洁课等）
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / SUBMITTED / MISSED
    submitted_at    DATETIME,
    submitted_by    VARCHAR(64),
    KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 巡检记录 ==============
CREATE TABLE IF NOT EXISTS qr_inspection_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene           VARCHAR(32) NOT NULL,
    plan_id         BIGINT,           -- 周期巡检有值
    task_id         BIGINT,           -- 联合巡检有值
    stall_id        BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    template_version INT NOT NULL,
    role_group      VARCHAR(32),
    submitter_id    BIGINT,
    submitter_name  VARCHAR(64),
    submitter_phone VARCHAR(32),
    submitter_dept  VARCHAR(32),
    status          VARCHAR(16) NOT NULL DEFAULT 'NORMAL', -- NORMAL / ABNORMAL
    answers_json    JSON NOT NULL,
    photos_json     JSON,
    submitted_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plan (plan_id),
    KEY idx_task (task_id),
    KEY idx_stall_time (stall_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== 整改任务（按部门聚合，一次巡检一条） ==============
CREATE TABLE IF NOT EXISTS qr_inspection_issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       BIGINT,
    task_id         BIGINT,
    department_id   BIGINT NOT NULL,
    dept_code       VARCHAR(32),
    dept_name       VARCHAR(64),
    stall_id        BIGINT,
    summary_text    VARCHAR(500),      -- 按部门聚合后的问题摘要
    items_json      JSON,               -- [{fieldCode,fieldLabel,category,abnormalValue,photoUrl}]
    access_token    VARCHAR(64) NOT NULL UNIQUE,
    deadline_at     DATETIME NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / FEEDBACK / CLOSED / OVERDUE
    feedback_text   TEXT,
    feedback_photos_json JSON,
    feedback_by     VARCHAR(64),
    feedback_by_name VARCHAR(64),
    feedback_at     DATETIME,
    closed_at       DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_dept (department_id, status),
    KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============== webhook 事件字典 ==============
INSERT INTO business_dictionary (dict_type, item_value, item_label, sort_order, status) VALUES
('WEBHOOK_EVENT','QR_INSPECTION_DUE','二维码巡检到期提醒',10,1),
('WEBHOOK_EVENT','QR_INSPECTION_ISSUE','二维码巡检整改任务',20,1),
('WEBHOOK_EVENT','QR_JOINT_CLOSED','联合巡检截止通知',30,1),
('WEBHOOK_EVENT','QR_INSPECTION_REMEDY_OVERDUE','二维码整改逾期提醒',40,1);

-- ============== 预置部门-webhook 主绑定（启用 webhook_config 中的群） ==============
-- PROP_EQ 设备课 → 群10 设备课值班巡场整改群
INSERT INTO sys_department_webhook (department_id, webhook_config_id, bind_type, enabled) VALUES (6,10,'PRIMARY',1);
-- PROP_SEC 安保课 → 群11
INSERT INTO sys_department_webhook (department_id, webhook_config_id, bind_type, enabled) VALUES (7,11,'PRIMARY',1);
-- PROP_CLN 保洁课 → 群12
INSERT INTO sys_department_webhook (department_id, webhook_config_id, bind_type, enabled) VALUES (8,12,'PRIMARY',1);
-- SM01~SM04 → 群13/14/15/16（其中14-16目前 enabled=0，管理员可在通知配置里启）
INSERT INTO sys_department_webhook (department_id, webhook_config_id, bind_type, enabled) VALUES (1,13,'PRIMARY',1),(2,14,'PRIMARY',1),(3,15,'PRIMARY',1),(4,16,'PRIMARY',1);

-- ============== 权限码 + 默认模板 ==============
-- 先插权限码
INSERT INTO sys_permission (code, name, module_code, module_name, description, enabled) VALUES
('qr.inspection.submit.warehouse','仓库巡检提交','QR_INSPECTION','二维码巡检','周期巡检扫码提交',1),
('qr.inspection.submit.joint','联合巡检提交','QR_INSPECTION','二维码巡检','联合巡检任务提交',1),
('qr.inspection.config','巡检模板配置','QR_INSPECTION','二维码巡检','场景A/B/C模板编辑',1),
('qr.inspection.plan.manage','巡检计划管理','QR_INSPECTION','二维码巡检','周期计划维护',1),
('qr.inspection.task.create','联合巡检发起','QR_INSPECTION','二维码巡检','发起/截止联合巡检任务',1),
('qr.inspection.view','巡检记录查看','QR_INSPECTION','二维码巡检','记录/整改/报告查询',1),
('qr.rectify','整改任务提交','QR_INSPECTION','二维码巡检','部门群里点链接整改提交',1);

-- 权限码 id 映射（拿 role_permission 用）
SET @p_submit_wh = (SELECT id FROM sys_permission WHERE code='qr.inspection.submit.warehouse');
SET @p_submit_jt = (SELECT id FROM sys_permission WHERE code='qr.inspection.submit.joint');
SET @p_config    = (SELECT id FROM sys_permission WHERE code='qr.inspection.config');
SET @p_plan      = (SELECT id FROM sys_permission WHERE code='qr.inspection.plan.manage');
SET @p_task      = (SELECT id FROM sys_permission WHERE code='qr.inspection.task.create');
SET @p_view      = (SELECT id FROM sys_permission WHERE code='qr.inspection.view');
SET @p_rectify   = (SELECT id FROM sys_permission WHERE code='qr.rectify');

-- 角色 id
SET @r_sa = (SELECT id FROM sys_role WHERE code='SUPER_ADMIN');
SET @r_sy = (SELECT id FROM sys_role WHERE code='SYSTEM_ADMIN');
SET @r_ap = (SELECT id FROM sys_role WHERE code='APPLICANT');
SET @r_opm = (SELECT id FROM sys_role WHERE code='OPS_PATROL_MANAGER');
SET @r_pm = (SELECT id FROM sys_role WHERE code='PROPERTY_MANAGER');
SET @r_dv = (SELECT id FROM sys_role WHERE code='DEVICE_VERIFIER');
SET @r_au = (SELECT id FROM sys_role WHERE code='AUDITOR');

-- 授权：超级/系统管理员全拿
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(@r_sa,@p_submit_wh),(@r_sa,@p_submit_jt),(@r_sa,@p_config),(@r_sa,@p_plan),(@r_sa,@p_task),(@r_sa,@p_view),(@r_sa,@p_rectify),
(@r_sy,@p_submit_wh),(@r_sy,@p_submit_jt),(@r_sy,@p_config),(@r_sy,@p_plan),(@r_sy,@p_task),(@r_sy,@p_view),(@r_sy,@p_rectify);

-- 商管人员 + 商管主管：仓库商管巡检 + 联合巡检提交 + 整改
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(@r_ap,@p_submit_wh),(@r_ap,@p_submit_jt),(@r_ap,@p_view),(@r_ap,@p_rectify),
(@r_opm,@p_submit_wh),(@r_opm,@p_submit_jt),(@r_opm,@p_view),(@r_opm,@p_rectify),(@r_opm,@p_task);

-- 物业管理员 + 物业工作人员：仓库物业巡检 + 联合巡检提交 + 整改
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
(@r_pm,@p_submit_wh),(@r_pm,@p_submit_jt),(@r_pm,@p_view),(@r_pm,@p_rectify),(@r_pm,@p_task),
(@r_dv,@p_submit_wh),(@r_dv,@p_submit_jt),(@r_dv,@p_view),(@r_dv,@p_rectify);

-- 审计：查看
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (@r_au,@p_view);

-- ============== 默认模板（3 份） ==============
-- A. 仓库商管检查表
INSERT INTO qr_inspection_template (scene, name, role_group, version, fields_json, enabled, remark) VALUES
('WAREHOUSE_COMMERCIAL','仓库商管检查表（默认）','COMMERCIAL',1,
'[
  {"code":"fireExtinguisher","label":"灭火器配置与有效性","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":10,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"firePassage","label":"消防通道是否畅通","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":20,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"foodExpiry","label":"食品有效期是否临近","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":30,"category":"食品有效期","responsibleDeptCode":"SM01"},
  {"code":"electricalStandard","label":"用电是否符合规范（私拉电线/大功率）","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":40,"category":"用电安全","responsibleDeptCode":"SM01"},
  {"code":"warehouseClean","label":"仓库是否整洁","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":50,"category":"仓库卫生","responsibleDeptCode":"PROP_CLN"},
  {"code":"exceptionDesc","label":"异常说明","controlType":"TEXT","required":false,"sortOrder":90,"category":"通用","responsibleDeptCode":""}
]',1,'默认模板，超管可在后台调整 category 与责任部门绑定关系');

-- B. 仓库物业巡检表（共用一张，覆盖设备+安保维度）
INSERT INTO qr_inspection_template (scene, name, role_group, version, fields_json, enabled, remark) VALUES
('WAREHOUSE_PROPERTY','仓库物业巡检表（默认）','PROPERTY',1,
'[
  {"code":"fireExtinguisherMaintain","label":"灭火器压力/有效期","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":10,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"fireAlarm","label":"烟感/报警系统","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":20,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"electricalCircuit","label":"配电箱/线路是否完好","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":30,"category":"用电安全","responsibleDeptCode":"PROP_EQ"},
  {"code":"groundWaterproof","label":"地面是否有积水/渗水","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":40,"category":"设施设备","responsibleDeptCode":"PROP_EQ"},
  {"code":"doorLock","label":"门窗是否完好上锁","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":50,"category":"安保防护","responsibleDeptCode":"PROP_SEC"},
  {"code":"trashAccumulation","label":"是否有杂物堆积","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":60,"category":"仓库卫生","responsibleDeptCode":"PROP_CLN"},
  {"code":"exceptionDesc","label":"异常说明","controlType":"TEXT","required":false,"sortOrder":90,"category":"通用","responsibleDeptCode":""}
]',1,'物业部共用，按扫码人部门归属自动匹配，category→整改责任部门可配置');

-- C. 联合巡检统一表
INSERT INTO qr_inspection_template (scene, name, role_group, version, fields_json, enabled, remark) VALUES
('JOINT','联合巡检统一表（默认）',NULL,1,
'[
  {"code":"merchantElectrical","label":"商户内用电是否规范","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":10,"category":"用电安全","responsibleDeptCode":"SM01"},
  {"code":"warehouseFoodExpiry","label":"仓库食品有效期","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":20,"category":"食品有效期","responsibleDeptCode":"SM01"},
  {"code":"warehouseFireEquipment","label":"仓库基础消防设施","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":30,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"warehouseSanitation","label":"仓库环境卫生","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":40,"category":"仓库卫生","responsibleDeptCode":"PROP_CLN"},
  {"code":"publicAreaFirePassage","label":"公共区域消防通道","controlType":"NORMAL_ABNORMAL","required":true,"normalValue":"NORMAL","requirePhotoOnAbnormal":true,"sortOrder":50,"category":"消防安全","responsibleDeptCode":"PROP_SEC"},
  {"code":"exceptionDesc","label":"异常说明","controlType":"TEXT","required":false,"sortOrder":90,"category":"通用","responsibleDeptCode":""}
]',1,'参与部门共同填报，同一铺位各维度可由对应部门负责整改');
