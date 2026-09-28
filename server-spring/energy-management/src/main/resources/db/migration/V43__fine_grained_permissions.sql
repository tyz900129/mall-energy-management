CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(32) NOT NULL DEFAULT 'VIEW',
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_permission_module (module_code, sort_order)
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
);

CREATE TABLE sys_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    effect VARCHAR(16) NOT NULL,
    expires_at DATETIME NULL,
    reason VARCHAR(255),
    granted_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_permission (user_id, permission_id),
    INDEX idx_user_permission_active (user_id, expires_at),
    CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
);

CREATE TABLE sys_permission_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operator_username VARCHAR(64),
    before_json TEXT,
    after_json TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_permission_log_user (user_id, created_at)
);

INSERT INTO sys_permission (code, name, module_code, module_name, action_type, sort_order) VALUES
('dashboard.view','查看工作台','dashboard','工作台','VIEW',101),
('workflow.view','查看流程','workflow','业务流程','VIEW',201),
('workflow.create','发起和提交流程','workflow','业务流程','EDIT',202),
('workflow.manage','处理流程及维护 OA 信息','workflow','业务流程','MANAGE',203),
('workflow.delete','删除和复位流程','workflow','业务流程','DANGER',204),
('verification.view','查看现场核验资料','verification','现场核验','VIEW',301),
('verification.execute','执行现场核验','verification','现场核验','EDIT',302),
('merchant.query','商户账户查询通道','merchant','经营基础资料','VIEW',401),
('merchant.menu','显示商户账户管理菜单','merchant','经营基础资料','VIEW',402),
('merchant.view','查看商户账户','merchant','经营基础资料','VIEW',403),
('merchant.manage','维护商户账户','merchant','经营基础资料','EDIT',404),
('stall.menu','显示铺位管理菜单','stall','经营基础资料','VIEW',410),
('stall.view','查看铺位','stall','经营基础资料','VIEW',411),
('stall.manage','维护铺位','stall','经营基础资料','EDIT',412),
('stall.delete','彻底删除铺位','stall','经营基础资料','DANGER',413),
('stall.import_export','导入导出铺位','stall','经营基础资料','EDIT',414),
('stall.engineering.view','查看铺位工程资料','stall_engineering','铺位工程资料','VIEW',421),
('stall.engineering.manage','维护铺位工程资料','stall_engineering','铺位工程资料','EDIT',422),
('metering.menu','显示计量设备绑定菜单','metering','计量设备绑定','VIEW',430),
('metering.view','查看铺位设备绑定','metering','计量设备绑定','VIEW',431),
('metering.manage','维护铺位设备绑定','metering','计量设备绑定','EDIT',432),
('energy.dashboard.view','查看用量数据大屏','energy','能源管理','VIEW',501),
('energy.analysis.view','查看用量分析','energy','能源管理','VIEW',502),
('energy.dimension.view','查看设备分析维度','energy','能源管理','VIEW',503),
('energy.dimension.manage','维护设备分析维度','energy','能源管理','EDIT',504),
('energy.sync.view','查看历史用量同步','energy','能源管理','VIEW',505),
('energy.sync.execute','执行历史用量同步','energy','能源管理','EDIT',506),
('energy.bill.view','查看水电账单','energy','能源管理','VIEW',507),
('energy.bill.manage','维护水电账单','energy','能源管理','EDIT',508),
('facility.power.view','查看配电拓扑','facility','设施设备','VIEW',601),
('facility.power.manage','维护配电拓扑','facility','设施设备','EDIT',602),
('facility.water.view','查看供水拓扑','facility','设施设备','VIEW',603),
('facility.water.manage','维护供水拓扑','facility','设施设备','EDIT',604),
('facility.drainage.view','查看排水拓扑','facility','设施设备','VIEW',605),
('facility.drainage.manage','维护排水拓扑','facility','设施设备','EDIT',606),
('facility.exhaust.view','查看排油烟拓扑','facility','设施设备','VIEW',607),
('facility.exhaust.manage','维护排油烟拓扑','facility','设施设备','EDIT',608),
('facility.fresh_air.view','查看补风机拓扑','facility','设施设备','VIEW',609),
('facility.fresh_air.manage','维护补风机拓扑','facility','设施设备','EDIT',610),
('exception.view','查看异常中心','operations','运维管理','VIEW',701),
('exception.manage','处理异常','operations','运维管理','EDIT',702),
('integration.config.view','查看源平台配置','integration','数据与集成','VIEW',801),
('integration.config.manage','维护源平台配置','integration','数据与集成','EDIT',802),
('integration.sync.view','查看同步任务和日志','integration','数据与集成','VIEW',803),
('integration.sync.execute','执行平台同步','integration','数据与集成','EDIT',804),
('system.user.view','查看用户','system','系统管理','VIEW',901),
('system.user.manage','维护用户和角色','system','系统管理','EDIT',902),
('system.user.permission.manage','配置个人权限','system','系统管理','DANGER',903),
('system.dictionary.view','查看基础字典','system','系统管理','VIEW',904),
('system.dictionary.manage','维护基础字典','system','系统管理','EDIT',905),
('system.webhook.view','查看 Webhook 配置','system','系统管理','VIEW',906),
('system.webhook.manage','维护 Webhook 配置','system','系统管理','EDIT',907),
('system.audit.view','查看操作审计','system','系统管理','VIEW',908),
('system.migration.manage','执行数据迁移','system','系统管理','DANGER',909);

-- 超级管理员默认拥有全部权限。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code = 'SUPER_ADMIN';

-- 系统管理员拥有除用户管理、个人授权和数据迁移之外的全部权限。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN' AND p.code NOT LIKE 'system.user.%' AND p.code <> 'system.migration.manage';

-- 只读用户保留原有菜单范围，不进入用户权限、字典、Webhook 和数据迁移。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'VIEWER' AND p.code IN (
  'dashboard.view','workflow.view','verification.view','merchant.query','merchant.menu','merchant.view',
  'stall.menu','stall.view','stall.engineering.view','metering.view',
  'energy.dashboard.view','energy.analysis.view','energy.dimension.view','energy.sync.view','energy.bill.view',
  'facility.power.view','facility.water.view','facility.drainage.view','facility.exhaust.view','facility.fresh_air.view',
  'exception.view','integration.config.view','integration.sync.view','system.audit.view'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'DEVICE_VERIFIER' AND p.code IN (
  'workflow.view','verification.view','verification.execute','merchant.menu','merchant.view','merchant.query',
  'stall.view','stall.engineering.view','metering.view','energy.dashboard.view',
  'facility.power.view','facility.water.view','facility.drainage.view','facility.exhaust.view','facility.fresh_air.view'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'APPLICANT' AND p.code IN (
  'workflow.view','workflow.create','verification.view','merchant.query','merchant.view','stall.view','metering.view'
);

-- 兼容历史角色数据；这些角色不再出现在角色分配界面中。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'AUDITOR' AND p.action_type = 'VIEW';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'PLATFORM_OPERATOR' AND p.code IN ('workflow.view','integration.config.view','integration.sync.view','integration.sync.execute','merchant.view','metering.view');
