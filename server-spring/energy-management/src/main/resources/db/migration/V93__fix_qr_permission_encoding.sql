-- 修复二维码巡检权限名称乱码（历史写入时字符集错误导致双重编码），并统一模块名称。
UPDATE sys_permission SET module_name = '二维码巡检' WHERE module_code = 'QR_INSPECTION';
UPDATE sys_permission SET name = '仓库巡检提交' WHERE code = 'qr.inspection.submit.warehouse';
UPDATE sys_permission SET name = '联合巡检提交' WHERE code = 'qr.inspection.submit.joint';
UPDATE sys_permission SET name = '巡检模板配置' WHERE code = 'qr.inspection.config';
UPDATE sys_permission SET name = '巡检计划管理' WHERE code = 'qr.inspection.plan.manage';
UPDATE sys_permission SET name = '联合巡检发起' WHERE code = 'qr.inspection.task.create';
UPDATE sys_permission SET name = '巡检记录查看' WHERE code = 'qr.inspection.view';
UPDATE sys_permission SET name = '整改任务提交' WHERE code = 'qr.rectify';
