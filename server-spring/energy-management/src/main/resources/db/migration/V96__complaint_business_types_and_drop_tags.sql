-- 客诉历史数据导入补充业态，并移除不再使用的关联标签字典
INSERT IGNORE INTO business_dictionary(dict_type,item_value,item_label,sort_order,status)
VALUES ('BUSINESS_TYPE','SUPERMARKET','超市',20,1),
       ('BUSINESS_TYPE','OTHER','其他',21,1);

DELETE FROM cs_complaint_dict WHERE dict_type='TAG';

-- 历史数据已不再使用标签，清空残留值
UPDATE cs_complaint SET related_tags=NULL WHERE related_tags IS NOT NULL;
