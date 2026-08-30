package com.wanyuecity.energy.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.platform.entity.PlatformSyncBatch;
import com.wanyuecity.energy.platform.entity.SyncLock;
import com.wanyuecity.energy.platform.mapper.PlatformSyncBatchMapper;
import com.wanyuecity.energy.platform.mapper.SyncLockMapper;
import com.wanyuecity.energy.platform.service.dto.SyncBatchView;
import com.wanyuecity.energy.platform.service.dto.SyncBatchDetailView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.wanyuecity.energy.core.BizException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional helpers split out of {@link PlatformSyncService} so the
 * {@code @Transactional} advice is honored (self-calls inside the same bean
 * would bypass the proxy). Each public method here runs in its own
 * transaction, which is exactly what the sync orchestration needs.
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class SyncPersistence {

    private final SyncLockMapper syncLockMapper;
    private final PlatformSyncBatchMapper batchMapper;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquireLock(String holder) {
        SyncLock l = syncLockMapper.lockRow();
        if (l == null) {
            SyncLock seed = new SyncLock();
            seed.setId(1L);
            syncLockMapper.insert(seed);
            l = syncLockMapper.lockRow();
        }
        if (l.getHolder() != null && !l.getHolder().isBlank()) {
            LocalDateTime acquired = l.getAcquiredAt();
            if (acquired != null && acquired.isAfter(LocalDateTime.now().minusHours(4))) {
                return false;
            }
        }
        syncLockMapper.mark(holder, "acquired " + LocalDateTime.now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLock() {
        try {
            syncLockMapper.release();
        } catch (Exception ex) {
            log.warn("failed to release sync lock: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createBatch(PlatformSyncBatch b) {
        batchMapper.insert(b);
        return b.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatch(PlatformSyncBatch b) {
        batchMapper.updateById(b);
    }

    public Page<SyncBatchView> listBatches(int pageNum, int pageSize) {
        Page<PlatformSyncBatch> p = batchMapper.selectPage(
                Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<PlatformSyncBatch>()
                        .orderByDesc(PlatformSyncBatch::getStartedAt));
        Page<SyncBatchView> out = Page.of(pageNum, pageSize, p.getTotal());
        List<SyncBatchView> rows = new ArrayList<>();
        for (PlatformSyncBatch b : p.getRecords()) {
            rows.add(toView(b));
        }
        out.setRecords(rows);
        return out;
    }

    public SyncBatchView latestBatch() {
        PlatformSyncBatch b = batchMapper.selectOne(
                new LambdaQueryWrapper<PlatformSyncBatch>()
                        .orderByDesc(PlatformSyncBatch::getStartedAt)
                        .last("LIMIT 1"));
        return b == null ? null : toView(b);
    }

    public SyncBatchDetailView batchDetail(Long id) {
        PlatformSyncBatch batch = batchMapper.selectById(id);
        if (batch == null) throw BizException.badRequest("同步批次不存在");
        SyncBatchDetailView detail = new SyncBatchDetailView();
        detail.setBatch(toView(batch));
        Integer currentRows = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM platform_account_current WHERE sync_batch_id=?) + " +
                "(SELECT COUNT(*) FROM platform_device_current WHERE sync_batch_id=?) + " +
                "(SELECT COUNT(*) FROM platform_account_device_current WHERE sync_batch_id=?)",
                Integer.class, id, id, id);
        detail.setCurrentDataAvailable(currentRows != null && currentRows > 0);
        Integer workflows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_match_run WHERE sync_batch_id=? AND match_status='MATCHED'",
                Integer.class, id);
        detail.setCompletedWorkflowCount(workflows == null ? 0 : workflows);
        detail.setExceptionSummary(jdbc.queryForList(
                "SELECT severity,COUNT(*) AS count FROM data_exception WHERE sync_batch_id=? GROUP BY severity ORDER BY FIELD(severity,'CRITICAL','ERROR','WARN','INFO')",
                id));
        detail.setExceptions(jdbc.queryForList(
                "SELECT id,exception_type AS exceptionType,severity,status,title,description,platform_device_id AS platformDeviceId,account_number AS accountNumber,detected_at AS detectedAt FROM data_exception WHERE sync_batch_id=? ORDER BY FIELD(severity,'CRITICAL','ERROR','WARN','INFO'),id DESC LIMIT 500",
                id));
        return detail;
    }

    private SyncBatchView toView(PlatformSyncBatch b) {
        SyncBatchView v = new SyncBatchView();
        v.setId(b.getId());
        v.setBatchNo(b.getBatchNo());
        v.setStatus(b.getStatus());
        v.setTriggerType(b.getTriggerType());
        v.setTriggerUserId(b.getTriggerUserId());
        v.setAccountCount(b.getAccountCount());
        v.setDeviceCount(b.getDeviceCount());
        v.setRelationCount(b.getRelationCount());
        v.setExceptionCount(b.getExceptionCount());
        v.setErrorMessage(b.getErrorMessage());
        v.setStartedAt(b.getStartedAt());
        v.setCompletedAt(b.getCompletedAt());
        if (b.getStartedAt() != null && b.getCompletedAt() != null) {
            v.setDurationMs(java.time.Duration.between(b.getStartedAt(), b.getCompletedAt()).toMillis());
        }
        return v;
    }
}
