-- 所有账户（含停用）多绑定仅作为资料提示。
UPDATE data_exception
SET severity='INFO', title=REPLACE(title, '设备绑定到', '设备历史关联到')
WHERE exception_type='DEVICE_MULTI_ACCOUNT';

-- 旧检测器对多账户设备进行单值比较产生的错误降级为提示。
UPDATE data_exception de
JOIN (
  SELECT platform_device_id
  FROM platform_account_device_current
  GROUP BY platform_device_id
  HAVING COUNT(DISTINCT account_number)>1
) multi ON multi.platform_device_id=de.platform_device_id
SET de.severity='INFO'
WHERE de.exception_type='UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE';

-- 按当前数据立即创建“一个以上启用计费账户”的警告。
INSERT IGNORE INTO data_exception
  (exception_type,severity,status,platform_device_id,title,description,detected_at,sync_batch_id)
SELECT 'DEVICE_MULTI_ENABLED_ACCOUNT','WARN','OPEN',r.platform_device_id,
       CONCAT('设备绑定到 ',COUNT(DISTINCT r.account_number),' 个启用计费账户'),
       CONCAT('同一设备只能绑定一个启用计费账户；当前启用账户数：',COUNT(DISTINCT r.account_number)),
       CURRENT_TIMESTAMP(3),(SELECT MAX(id) FROM platform_sync_batch)
FROM platform_account_device_current r
JOIN platform_account_current a ON a.account_number=r.account_number
WHERE a.platform_status IS NULL OR a.platform_status NOT IN ('0','DISABLED','INACTIVE')
GROUP BY r.platform_device_id
HAVING COUNT(DISTINCT r.account_number)>1;
