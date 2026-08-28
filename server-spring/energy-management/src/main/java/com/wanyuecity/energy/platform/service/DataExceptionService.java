package com.wanyuecity.energy.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.service.dto.DataExceptionHandleRequest;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataExceptionService {
    private static final Set<String> STATUSES = Set.of("OPEN", "ACK", "HANDLING", "RESOLVED", "IGNORED");

    private final DataExceptionMapper exceptionMapper;
    private final SysUserMapper userMapper;

    public Page<DataException> list(String status, String severity, String exceptionType,
                                    String keyword, Long syncBatchId, int pageNum, int pageSize) {
        QueryWrapper<DataException> q = new QueryWrapper<>();
        if (hasText(status)) q.eq("status", status.trim().toUpperCase());
        if (hasText(severity)) q.eq("severity", severity.trim().toUpperCase());
        if (hasText(exceptionType)) q.eq("exception_type", exceptionType.trim());
        if (syncBatchId != null) q.eq("sync_batch_id", syncBatchId);
        if (hasText(keyword)) {
            String k = keyword.trim();
            q.and(w -> w.like("title", k).or().like("description", k)
                    .or().like("platform_device_id", k).or().like("account_number", k)
                    .or().like("merchant_code", k));
        }
        q.orderByAsc("FIELD(status,'OPEN','ACK','HANDLING','RESOLVED','IGNORED')")
                .orderByDesc("detected_at");
        return exceptionMapper.selectPage(Page.of(pageNum, Math.min(Math.max(pageSize, 1), 100)), q);
    }

    @Transactional
    public DataException handle(Long id, DataExceptionHandleRequest req, String username) {
        DataException row = exceptionMapper.selectById(id);
        if (row == null) throw BizException.badRequest("exception not found: id=" + id);
        String status = req.getStatus().trim().toUpperCase();
        if (!STATUSES.contains(status)) throw BizException.badRequest("invalid exception status: " + status);
        if (("RESOLVED".equals(status) || "IGNORED".equals(status)) && !hasText(req.getHandleResult())) {
            throw BizException.badRequest("handleResult is required for terminal status");
        }
        row.setStatus(status);
        row.setHandleResult(hasText(req.getHandleResult()) ? req.getHandleResult().trim() : null);
        row.setHandledAt(LocalDateTime.now());
        if (hasText(username)) {
            SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
            row.setHandlerId(user == null ? null : user.getId());
        }
        exceptionMapper.updateById(row);
        return row;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

