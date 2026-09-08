UPDATE workflow_expected_change c
JOIN platform_account_current p ON p.account_number = c.from_account_number
SET c.verify_status = 'MATCHED',
    c.verified_at = COALESCE(c.verified_at, CURRENT_TIMESTAMP(3)),
    c.exception_reason = '账户已停用'
WHERE c.change_type = 'ACCOUNT_DISABLE'
  AND c.verify_status <> 'MATCHED'
  AND UPPER(COALESCE(p.platform_status, '')) IN ('0', 'DISABLED', 'INACTIVE');
