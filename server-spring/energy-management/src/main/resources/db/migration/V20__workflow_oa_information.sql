ALTER TABLE workflow
  ADD COLUMN oa_process_no VARCHAR(100) NULL AFTER uses_water,
  ADD COLUMN completion_remark VARCHAR(1000) NULL AFTER oa_process_no;

