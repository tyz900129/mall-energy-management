-- 客诉渠道补充历史数据来源
INSERT IGNORE INTO cs_complaint_dict(dict_type,item_value,item_label,sort_order)
VALUES ('CHANNEL','WECHAT','微信',6),
       ('CHANNEL','ONLINE_PLATFORM','网络平台',7);
