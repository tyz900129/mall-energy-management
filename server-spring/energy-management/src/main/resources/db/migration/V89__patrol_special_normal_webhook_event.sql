-- 物业巡场通知修复补数据：
-- 「特殊区域正常」留档照片此前只能靠"每日入口推送(PATROL_DAILY_ENTRY)"事件命中，
-- 而前端事件列表里没有 PATROL_SPECIAL_NORMAL，导致企业微信群无法订阅该推送。
-- 代码侧已改为订阅 PATROL_SPECIAL_NORMAL 或 PATROL_DAILY_ENTRY 任一即生效（并集），
-- 这里给现有巡场群补上显式订阅，让企微群立即收到留档照片。
UPDATE webhook_config
SET event_types = CONCAT(event_types, ',PATROL_SPECIAL_NORMAL')
WHERE enabled = 1
  AND event_types IS NOT NULL
  AND event_types NOT LIKE '%PATROL_SPECIAL_NORMAL%'
  AND (event_types LIKE '%PATROL_DAILY_ENTRY%' OR event_types LIKE '%PATROL_ISSUE_SUBMIT%');
