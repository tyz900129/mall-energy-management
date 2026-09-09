package com.wanyuecity.energy.platform.service.detector;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceSnapshot;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档第 35 节：无流程设备账户变更检测。
 * <p>
 * 对比昨日 {@code platform_account_device_snapshot} 与今日 {@code platform_account_device_current}。
 * 若某 device 的计费账户发生变化，且未找到方向、原账户和目标账户均匹配的有效 Workflow，
 * 则写入 {@code UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE} 异常。
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class UnauthorizedChangeDetector {

    private final PlatformAccountDeviceSnapshotMapper snapshotMapper;
    private final PlatformAccountDeviceCurrentMapper currentMapper;
    private final DataExceptionMapper exceptionMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    @Transactional
    public int detect(Long syncBatchId) {
        // 1) 取昨天的快照
        LocalDate yesterday = LocalDate.now().minusDays(1);
        PlatformAccountDeviceSnapshot ySnapshot = snapshotMapper.selectOne(new QueryWrapper<PlatformAccountDeviceSnapshot>()
                .eq("snapshot_date", yesterday)
                .last("LIMIT 1"));
        if (ySnapshot == null) {
            log.info("Unauthorized detector: no snapshot for {}, skip (first sync?)", yesterday);
            return 0;
        }
        Map<String, String> yDeviceToAccount;
        try {
            yDeviceToAccount = parseSnapshot(ySnapshot.getPayload());
        } catch (Exception ex) {
            log.warn("Unauthorized detector: snapshot parse failed, skip. {}", ex.getMessage());
            return 0;
        }

        // 2) 取今天的 current
        List<PlatformAccountDeviceCurrent> today = currentMapper.selectList(null);
        Map<String, String> tDeviceToAccount = new HashMap<>();
        Map<String, Set<String>> todayAccountSets = new HashMap<>();
        for (PlatformAccountDeviceCurrent r : today) {
            // 同一 device 在 current 里若有多个 account，记一个特殊值；这类由 MultiAccountDetector 覆盖
            tDeviceToAccount.putIfAbsent(r.getPlatformDeviceId(), r.getAccountNumber());
            todayAccountSets.computeIfAbsent(r.getPlatformDeviceId(), ignored -> new HashSet<>()).add(r.getAccountNumber());
        }

        // 3) 差集：device 存在昨天 + 今天 + account 改变
        List<DataException> toInsert = new ArrayList<>();
        for (Map.Entry<String, String> e : tDeviceToAccount.entrySet()) {
            String device = e.getKey();
            // 多账户设备由 MultiAccountDetector 按集合规则处理，不能随机取一个账户比较。
            if (todayAccountSets.getOrDefault(device, Set.of()).size() > 1) continue;
            String tAccount = e.getValue();
            String yAccount = yDeviceToAccount.get(device);
            if (yAccount != null && !yAccount.equals(tAccount)) {
                Long workflowId = authorizedBindWorkflow(device, yAccount, tAccount, LocalDateTime.now());
                if (workflowId != null) {
                    log.info("Unauthorized detector: device {} account change {} -> {} covered by workflow {}",
                            device, yAccount, tAccount, workflowId);
                    continue;
                }
                DataException ex = new DataException();
                ex.setExceptionType("UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE");
                ex.setSeverity("ERROR");
                ex.setStatus("OPEN");
                ex.setPlatformDeviceId(device);
                ex.setAccountNumber(tAccount);
                ex.setTitle("设备账户变更但无流程记录");
                ex.setDescription("设备 " + device + " 的计费账户从 " + yAccount + " 变更为 " + tAccount
                        + "；未找到与本次设备、原账户和目标账户相匹配的有效流程，请人工核查。");
                ex.setPayloadJson("{\"deviceId\":\"" + device + "\",\"fromAccount\":\"" + yAccount
                        + "\",\"toAccount\":\"" + tAccount + "\"}");
                ex.setDetectedAt(LocalDateTime.now());
                ex.setSyncBatchId(syncBatchId);
                toInsert.add(ex);
            }
        }

        // 4) 设备消失：昨天有今天没有
        Set<String> todaySet = tDeviceToAccount.keySet();
        for (String device : yDeviceToAccount.keySet()) {
            if (!todaySet.contains(device)) {
                String fromAccount = yDeviceToAccount.get(device);
                Long workflowId = authorizedUnbindWorkflow(device, fromAccount, LocalDateTime.now());
                if (workflowId != null) {
                    log.info("Unauthorized detector: device {} disappeared from account {} covered by workflow {}",
                            device, fromAccount, workflowId);
                    continue;
                }
                DataException ex = new DataException();
                ex.setExceptionType("DEVICE_DISAPPEARED");
                ex.setSeverity("WARN");
                ex.setStatus("OPEN");
                ex.setPlatformDeviceId(device);
                ex.setAccountNumber(yDeviceToAccount.get(device));
                ex.setTitle("设备今日未出现在同步结果中");
                ex.setDescription("设备 " + device + " 昨日还在平台账户关系中，今日已消失。请核查。");
                ex.setPayloadJson("{\"deviceId\":\"" + device + "\"}");
                ex.setDetectedAt(LocalDateTime.now());
                ex.setSyncBatchId(syncBatchId);
                toInsert.add(ex);
            }
        }

        for (DataException ex : toInsert) {
            Long existed = exceptionMapper.selectCount(new QueryWrapper<DataException>()
                    .eq("exception_type", ex.getExceptionType())
                    .eq("platform_device_id", ex.getPlatformDeviceId())
                    .eq("sync_batch_id", syncBatchId));
            if (existed == null || existed == 0) {
                exceptionMapper.insert(ex);
            }
        }
        log.info("Unauthorized detector: {} new exception(s) for batchId={}", toInsert.size(), syncBatchId);
        return toInsert.size();
    }

    /**
     * 账户变更必须由“绑定设备”预期变化覆盖，并且当前账户确实属于流程商户。
     * 不能只按 deviceId 判断，否则同一设备的历史流程会掩盖后来真正的异常变更。
     */
    private Long authorizedBindWorkflow(String deviceId, String fromAccount, String toAccount, LocalDateTime detectedAt) {
        List<Long> ids = jdbc.queryForList("""
                SELECT DISTINCT w.id
                FROM workflow w
                JOIN workflow_expected_change c ON c.workflow_id=w.id AND c.platform_device_id=?
                LEFT JOIN platform_account_current p ON p.account_number=?
                LEFT JOIN platform_account_local_profile l ON l.account_number=p.account_number
                WHERE w.submitted_at IS NOT NULL AND w.submitted_at<=?
                  AND w.status NOT IN ('DRAFT','CANCELLED','RESOLVED_EXCEPTION')
                  AND (w.completed_at IS NULL OR w.completed_at>=DATE_SUB(?,INTERVAL 3 DAY))
                  AND c.change_type='DEVICE_BIND'
                  AND (c.from_account_number IS NULL OR c.from_account_number=?)
                  AND (c.to_account_number=? OR w.target_account_number=?
                       OR COALESCE(NULLIF(l.merchant_code,''),p.merchant_code)=w.merchant_code)
                ORDER BY w.id DESC LIMIT 1
                """, Long.class, deviceId, toAccount, detectedAt, detectedAt, fromAccount, toAccount, toAccount);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 设备从账户关系中消失，仅由对应原账户的撤柜解绑流程覆盖。 */
    private Long authorizedUnbindWorkflow(String deviceId, String fromAccount, LocalDateTime detectedAt) {
        List<Long> ids = jdbc.queryForList("""
                SELECT DISTINCT w.id
                FROM workflow w
                JOIN workflow_expected_change c ON c.workflow_id=w.id AND c.platform_device_id=?
                WHERE w.submitted_at IS NOT NULL AND w.submitted_at<=?
                  AND w.status NOT IN ('DRAFT','CANCELLED','RESOLVED_EXCEPTION')
                  AND (w.completed_at IS NULL OR w.completed_at>=DATE_SUB(?,INTERVAL 3 DAY))
                  AND c.change_type='DEVICE_UNBIND'
                  AND (c.from_account_number IS NULL OR c.from_account_number=?)
                ORDER BY w.id DESC LIMIT 1
                """, Long.class, deviceId, detectedAt, detectedAt, fromAccount);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Snapshot payload 形如 {@code [{"accountNumber":"...", "platformDeviceId":"..."}, ...]} */
    private Map<String, String> parseSnapshot(String json) throws Exception {
        if (json == null || json.isBlank()) return new HashMap<>();
        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        Map<String, String> out = new HashMap<>();
        for (Map<String, Object> r : list) {
            Object device = r.get("platformDeviceId");
            Object account = r.get("accountNumber");
            if (device == null) continue;
            out.put(String.valueOf(device), account == null ? null : String.valueOf(account));
        }
        return out;
    }
}
