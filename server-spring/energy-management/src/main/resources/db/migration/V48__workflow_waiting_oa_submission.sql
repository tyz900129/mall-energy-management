-- A verified billing workflow is not ready for platform matching until its OA number exists.
UPDATE workflow
SET status = 'WAITING_OA_SUBMISSION',
    waiting_platform_at = NULL
WHERE status = 'WAITING_PLATFORM_CHANGE'
  AND (oa_process_no IS NULL OR TRIM(oa_process_no) = '');
