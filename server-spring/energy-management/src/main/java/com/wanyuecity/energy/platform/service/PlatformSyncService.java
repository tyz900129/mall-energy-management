package com.wanyuecity.energy.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.adapter.Session;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountDeviceRelation;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import com.wanyuecity.energy.platform.entity.PlatformConfig;
import com.wanyuecity.energy.platform.entity.PlatformSyncBatch;
import com.wanyuecity.energy.platform.mapper.PlatformConfigMapper;
import com.wanyuecity.energy.platform.service.dto.SyncBatchView;
import com.wanyuecity.energy.platform.service.dto.SyncBatchDetailView;
import com.wanyuecity.energy.platform.service.detector.MultiAccountDetector;
import com.wanyuecity.energy.platform.service.detector.OrphanRelationDetector;
import com.wanyuecity.energy.platform.service.detector.UnauthorizedChangeDetector;
import com.wanyuecity.energy.platform.service.snapshot.SnapshotWriter;
import com.wanyuecity.energy.workflow.service.WorkflowPlatformMatcher;
import com.wanyuecity.energy.stall.service.StallNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 3 — orchestrates the full source-platform sync.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Acquire MySQL row lock (sync_lock) — single-instance guarantee.</li>
 *   <li>Create Batch (status=RUNNING).</li>
 *   <li>Login to source platform.</li>
 *   <li>Fetch accounts / devices / relations (paginated).</li>
 *   <li>Validate (non-empty + referential integrity).</li>
 *   <li>Replace current tables in 3 transactions (separate {@link CurrentPersistence} beans).</li>
 *   <li>Write daily snapshot rows.</li>
 *   <li>Update Batch → SUCCESS; record last_sync_at on platform_config.</li>
 * </ol>
 *
 * <p>Failure paths keep the previous batch's current data intact and write
 * the error to the Batch row. The lock is always released (try/finally).</p>
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class PlatformSyncService {

    private static final DateTimeFormatter BATCH_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PlatformAdapter adapter;
    private final PlatformConfigMapper configMapper;
    private final CredentialCryptoService crypto;
    private final SyncPersistence sync;
    private final CurrentPersistence current;
    private final SnapshotWriter snapshotWriter;
    private final MultiAccountDetector multiAccountDetector;
    private final OrphanRelationDetector orphanRelationDetector;
    private final UnauthorizedChangeDetector unauthorizedChangeDetector;
    private final WorkflowPlatformMatcher workflowPlatformMatcher;
    private final StallNameResolver stallNameResolver;

    // ---- public entry points -------------------------------------------

    public SyncBatchView runManual(Long triggerUserId) {
        return runSync("MANUAL", triggerUserId);
    }

    public SyncBatchView runScheduled() {
        return runSync("AUTO", null);
    }

    public Page<SyncBatchView> listBatches(int pageNum, int pageSize) {
        return sync.listBatches(pageNum, pageSize);
    }

    public SyncBatchView latestBatch() {
        return sync.latestBatch();
    }

    public SyncBatchDetailView batchDetail(Long id) {
        return sync.batchDetail(id);
    }

    // ---- core ---------------------------------------------------------

    public SyncBatchView runSync(String triggerType, Long triggerUserId) {
        PlatformConfig cfg = configMapper.findSingleton();
        if (cfg == null) {
            throw BizException.badRequest("Platform config not initialized");
        }
        if (cfg.getPasswordCiphertext() == null
                || "__PENDING__".equals(cfg.getPasswordCiphertext())) {
            throw BizException.badRequest("Platform password not configured");
        }
        if (Integer.valueOf(0).equals(cfg.getEnabled()) && "MANUAL".equals(triggerType)) {
            log.warn("manual sync requested while platform.enabled=0; proceeding anyway");
        }

        String holder = "sync-" + triggerType + "-" + UUID.randomUUID();
        if (!sync.acquireLock(holder)) {
            throw BizException.badRequest("Another sync is already in progress");
        }

        PlatformSyncBatch batch = newBatch(triggerType, triggerUserId);
        Long batchId = sync.createBatch(batch);
        batch.setId(batchId);
        log.info("sync started: batchNo={}, trigger={}, userId={}", batch.getBatchNo(), triggerType, triggerUserId);

        try {
            String password = crypto.decrypt(cfg.getPasswordCiphertext(), cfg.getPasswordIv());
            Session session = adapter.login(cfg.getBaseUrl(), cfg.getAccountUsername(), password);
            String spaceId = cfg.getSpaceId();

            List<PlatformAccount> accounts = adapter.fetchAccounts(cfg.getBaseUrl(), session.token(), spaceId);
            List<PlatformDevice> devices = adapter.fetchDevices(cfg.getBaseUrl(), session.token(), spaceId);
            List<PlatformAccountDeviceRelation> relations =
                    adapter.fetchAccountDeviceRelations(cfg.getBaseUrl(), session.token(), spaceId);

            if (accounts.isEmpty()) {
                throw new IllegalStateException("Source returned 0 accounts; aborting to protect previous data");
            }
            validateRelations(accounts, devices, relations);

            // 一次事务：清 + 写 accounts/devices/relations（spec 文档第 16 节）
            // 失败时回滚到上一批次，外部 current 保持不变。
            LocalDateTime now = LocalDateTime.now();
            int[] counts = current.replaceAll(accounts, devices, relations, batchId, now);
            int aCount = counts[0], dCount = counts[1], rCount = counts[2];
            // 按铺位编码同步当前租户账户；设备关系不参与租户匹配。
            stallNameResolver.refreshAll();

            // snapshot 是只读派生，单独写失败不影响 current（spec 文档第 16 节"独立写快照"）
            snapshotWriter.writeDaily(accounts, devices, relations, batchId);

            // 治理：异常检测（spec 文档第 17 节 / 第 34-35 节）
            // 当前 transaction 已 commit，下面是 best-effort，单条异常不影响主流程。
            int multiEx = 0, orphanEx = 0, unauthEx = 0;
            try { multiEx = multiAccountDetector.detect(batchId); } catch (Exception ex) {
                log.error("multi-account detector failed (batchId={}): {}", batchId, ex.getMessage(), ex);
            }
            try { orphanEx = orphanRelationDetector.detect(batchId); } catch (Exception ex) {
                log.error("orphan-relation detector failed (batchId={}): {}", batchId, ex.getMessage(), ex);
            }
            try { unauthEx = unauthorizedChangeDetector.detect(batchId); } catch (Exception ex) {
                log.error("unauthorized-change detector failed (batchId={}): {}", batchId, ex.getMessage(), ex);
            }

            batch.setAccountCount(aCount);
            batch.setDeviceCount(dCount);
            batch.setRelationCount(rCount);
            batch.setExceptionCount(multiEx + orphanEx + unauthEx);
            batch.setStatus("SUCCESS");
            batch.setCompletedAt(LocalDateTime.now());
            sync.updateBatch(batch);

            cfg.setLastSyncAt(LocalDateTime.now());
            cfg.setLastSyncStatus("SUCCESS");
            cfg.setLastSyncBatchId(batchId);
            configMapper.updateById(cfg);

            int matchedWorkflows = 0;
            try { matchedWorkflows = workflowPlatformMatcher.matchAfterSync(batchId); } catch (Exception ex) {
                log.error("workflow matching failed (batchId={}): {}", batchId, ex.getMessage(), ex);
            }

            log.info("sync ok: batchId={}, accounts={}, devices={}, relations={}, completedWorkflows={}",
                    batchId, aCount, dCount, rCount, matchedWorkflows);
            return sync.latestBatch();

        } catch (Exception ex) {
            log.error("sync failed at batchId={}: {}", batchId, ex.getMessage(), ex);
            batch.setStatus("FAILED");
            batch.setCompletedAt(LocalDateTime.now());
            batch.setErrorMessage(truncate(ex.getMessage(), 4000));
            try { sync.updateBatch(batch); } catch (Exception ignore) { /* batch row may not exist */ }

            cfg.setLastSyncAt(LocalDateTime.now());
            cfg.setLastSyncStatus("FAILED");
            cfg.setLastSyncBatchId(batchId);
            try { configMapper.updateById(cfg); } catch (Exception ignore) { }
            throw BizException.badRequest("Sync failed: " + ex.getMessage());
        } finally {
            sync.releaseLock();
        }
    }

    // ---- helpers ------------------------------------------------------

    private PlatformSyncBatch newBatch(String triggerType, Long triggerUserId) {
        PlatformSyncBatch b = new PlatformSyncBatch();
        b.setBatchNo("SYNC" + LocalDateTime.now().format(BATCH_NO_FMT) + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        b.setStartedAt(LocalDateTime.now());
        b.setStatus("RUNNING");
        b.setTriggerType(triggerType);
        b.setTriggerUserId(triggerUserId);
        b.setAccountCount(0);
        b.setDeviceCount(0);
        b.setRelationCount(0);
        return b;
    }

    private void validateRelations(List<PlatformAccount> accounts,
                                   List<PlatformDevice> devices,
                                   List<PlatformAccountDeviceRelation> relations) {
        if (relations.isEmpty()) {
            log.warn("sync produced 0 relations; continuing (this may be normal for the first sync)");
            return;
        }
        Set<String> accountNos = new HashSet<>();
        for (PlatformAccount a : accounts) accountNos.add(a.getAccountNumber());
        Set<String> deviceIds = new HashSet<>();
        for (PlatformDevice d : devices) deviceIds.add(d.getPlatformDeviceId());

        int orphanAccounts = 0, orphanDevices = 0;
        for (PlatformAccountDeviceRelation r : relations) {
            if (!accountNos.contains(r.getAccountNumber())) orphanAccounts++;
            if (!deviceIds.contains(r.getPlatformDeviceId())) orphanDevices++;
        }
        if (orphanAccounts > 0 || orphanDevices > 0) {
            log.warn("relations reference unknown endpoints: orphanAccounts={}, orphanDevices={}", orphanAccounts, orphanDevices);
        }
    }

    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n);
    }
}
