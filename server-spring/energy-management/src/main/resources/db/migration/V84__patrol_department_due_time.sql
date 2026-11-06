ALTER TABLE patrol_department
  ADD COLUMN due_time TIME NOT NULL DEFAULT '20:00:00' COMMENT '每日整改截止时间' AFTER sla_hours;
