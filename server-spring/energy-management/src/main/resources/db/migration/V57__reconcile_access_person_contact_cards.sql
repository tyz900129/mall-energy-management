-- 海康存量数据中大量 employeeNo 实际使用手机号，补入人员联系电话字段。
UPDATE access_person
SET phone=employee_no
WHERE (phone IS NULL OR phone='')
  AND employee_no REGEXP '^1[0-9]{10}$';

ALTER TABLE access_card_snapshot
  ADD COLUMN mobile_format TINYINT NOT NULL DEFAULT 0 AFTER id_number_format;

CREATE INDEX idx_access_card_mobile ON access_card_snapshot(scan_job_id,mobile_format,employee_no);
