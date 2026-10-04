-- 系统人员编号统一采用 access_person 自增主键；设备人员编号继续保留在 employee_no/映射表中。
UPDATE access_person SET person_no = CAST(id AS CHAR);

-- 将设备人员资料中已读取到的联系电话补入人员主档（不覆盖人工维护的数据）。
UPDATE access_person p
SET p.phone = (
  SELECT NULLIF(s.phone, '')
  FROM access_person_snapshot s
  WHERE s.employee_no=p.employee_no AND NULLIF(s.phone, '') IS NOT NULL
  ORDER BY s.scan_job_id DESC, s.id DESC LIMIT 1
)
WHERE (p.phone IS NULL OR p.phone='')
  AND EXISTS (SELECT 1 FROM access_person_snapshot s2 WHERE s2.employee_no=p.employee_no AND NULLIF(s2.phone, '') IS NOT NULL);

INSERT IGNORE INTO sys_permission(code, name, module_code, module_name, action_type, sort_order, description)
VALUES ('access.self.manage', '维护本人门禁资料', 'access', '门禁管理', 'MANAGE', 95, '申请人维护本人联系电话、身份证及所属单位信息');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r JOIN sys_permission p ON p.code='access.self.manage'
WHERE r.code IN ('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','VIEWER');
