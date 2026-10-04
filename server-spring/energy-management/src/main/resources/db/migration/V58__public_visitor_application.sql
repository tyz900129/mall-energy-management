ALTER TABLE visitor_appointment
  ADD COLUMN public_token VARCHAR(64) NULL AFTER appointment_no,
  ADD UNIQUE KEY uk_visitor_public_token(public_token);
