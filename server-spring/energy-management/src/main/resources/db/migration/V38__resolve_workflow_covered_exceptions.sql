-- 修复历史误报：异常检测曾未实际过滤已有流程。
-- 仅处理流程提交之后发生、且设备/变更方向/原目标账户均匹配的告警；
-- 历史流程、草稿、已取消及人工关闭异常的流程不会掩盖真正的异常。
UPDATE data_exception de
JOIN (
  SELECT de2.id exception_id, MAX(w.id) workflow_id
  FROM data_exception de2
  JOIN workflow_expected_change c ON c.platform_device_id=de2.platform_device_id
  JOIN workflow w ON w.id=c.workflow_id
  LEFT JOIN platform_account_current p
    ON p.account_number=JSON_UNQUOTE(JSON_EXTRACT(de2.payload_json,'$.toAccount'))
  LEFT JOIN platform_account_local_profile l ON l.account_number=p.account_number
  WHERE de2.status='OPEN'
    AND de2.exception_type IN ('UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE','DEVICE_DISAPPEARED')
    AND w.submitted_at IS NOT NULL
    AND w.submitted_at<=de2.detected_at
    AND w.status NOT IN ('DRAFT','CANCELLED','RESOLVED_EXCEPTION')
    AND (w.completed_at IS NULL OR w.completed_at>=DATE_SUB(de2.detected_at,INTERVAL 3 DAY))
    AND (
      (
        de2.exception_type='UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE'
        AND c.change_type='DEVICE_BIND'
        AND (
          c.from_account_number IS NULL
          OR c.from_account_number=JSON_UNQUOTE(JSON_EXTRACT(de2.payload_json,'$.fromAccount'))
        )
        AND (
          c.to_account_number=JSON_UNQUOTE(JSON_EXTRACT(de2.payload_json,'$.toAccount'))
          OR w.target_account_number=JSON_UNQUOTE(JSON_EXTRACT(de2.payload_json,'$.toAccount'))
          OR COALESCE(NULLIF(l.merchant_code,''),p.merchant_code)=w.merchant_code
        )
      )
      OR
      (
        de2.exception_type='DEVICE_DISAPPEARED'
        AND c.change_type='DEVICE_UNBIND'
        AND (c.from_account_number IS NULL OR c.from_account_number=de2.account_number)
      )
    )
  GROUP BY de2.id
) covered ON covered.exception_id=de.id
SET de.status='RESOLVED',
    de.workflow_id=covered.workflow_id,
    de.handled_at=COALESCE(de.handled_at,CURRENT_TIMESTAMP(3)),
    de.handle_result=CONCAT('系统自动识别：该变化已由流程ID ',covered.workflow_id,' 覆盖');
