ALTER TABLE access_device
  ADD COLUMN login_username VARCHAR(100) NULL AFTER ip_address,
  ADD COLUMN password_ciphertext VARCHAR(1000) NULL AFTER login_username,
  ADD COLUMN password_iv VARCHAR(255) NULL AFTER password_ciphertext;
