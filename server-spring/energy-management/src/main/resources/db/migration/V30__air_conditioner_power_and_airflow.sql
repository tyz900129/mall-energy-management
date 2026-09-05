ALTER TABLE air_conditioner_specification
  ADD COLUMN rated_power_kw DECIMAL(10,2) NULL AFTER equipment_type,
  ADD COLUMN airflow_cmh INT NULL AFTER rated_power_kw;

-- 原资料没有功率和风量，保留型号并等待管理员补齐参数。
UPDATE air_conditioner_specification SET rated_power_kw=0,airflow_cmh=0 WHERE rated_power_kw IS NULL OR airflow_cmh IS NULL;
ALTER TABLE air_conditioner_specification MODIFY rated_power_kw DECIMAL(10,2) NOT NULL,MODIFY airflow_cmh INT NOT NULL;
