package com.wanyuecity.energy.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.service.PlatformSyncService;
import com.wanyuecity.energy.platform.service.dto.SyncBatchView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同步日志只读视图（文档第 16 节 / 第 22 节）。
 * 路径对齐 spec：{@code /api/v1/platform/sync-logs}。
 * 字段：批次号 / 状态 / 触发方式 / 触发人 / 数量 / 异常数 / 耗时 / 起止时间 / 错误信息。
 */
@RestController
@RequestMapping("/api/v1/platform/sync-logs")
@RequiredArgsConstructor
public class PlatformSyncLogController {

    private final PlatformSyncService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Page<SyncBatchView>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.listBatches(pageNum, pageSize));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<SyncBatchView> latest() {
        return ApiResponse.ok(service.latestBatch());
    }
}

