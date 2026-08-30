ALTER TABLE workflow
  ADD COLUMN request_key VARCHAR(64) NULL AFTER workflow_no,
  ADD UNIQUE KEY uk_workflow_request_key (request_key);
