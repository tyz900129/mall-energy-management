ALTER TABLE workflow
  ADD COLUMN original_contact_phone VARCHAR(1000) NULL COMMENT '申请时原平台联系电话快照',
  ADD COLUMN update_sms_phone TINYINT(1) NULL COMMENT '是否申请修改短信通知号码';
