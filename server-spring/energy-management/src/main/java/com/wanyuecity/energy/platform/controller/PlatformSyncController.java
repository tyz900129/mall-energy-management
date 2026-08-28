package com.wanyuecity.energy.platform.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.service.PlatformSyncService;
import com.wanyuecity.energy.platform.service.dto.SyncBatchView;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/sync")
@RequiredArgsConstructor
public class PlatformSyncController {

    private final PlatformSyncService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<SyncBatchView> run(Authentication auth) {
        Long userId = null;
        if (auth != null && auth.getName() != null) {
            // We do not load the user again; the audit log keeps the username.
            // The full user id is only needed for FK; keep it best-effort.
            try {
                userId = Long.valueOf(auth.getName());
            } catch (NumberFormatException ignore) {
                userId = null;
            }
        }
        return ApiResponse.ok(service.runManual(userId));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Page<SyncBatchView>> listBatches(
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

