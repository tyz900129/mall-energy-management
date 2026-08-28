ALTER TABLE workflow ADD COLUMN uses_water TINYINT(1) NULL AFTER extra_data;

UPDATE workflow
SET uses_water = CASE
  WHEN JSON_UNQUOTE(JSON_EXTRACT(extra_data, '$.usesWater')) = 'true' THEN 1
  WHEN JSON_UNQUOTE(JSON_EXTRACT(extra_data, '$.usesWater')) = 'false' THEN 0
  ELSE NULL
END
WHERE workflow_type IN ('NEW_MERCHANT','EXISTING_MERCHANT_ADD_STALL')
  AND extra_data IS NOT NULL;

