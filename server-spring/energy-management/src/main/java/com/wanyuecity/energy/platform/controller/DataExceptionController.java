package com.wanyuecity.energy.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.service.DataExceptionService;
import com.wanyuecity.energy.platform.service.dto.DataExceptionHandleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-exceptions")
@RequiredArgsConstructor
public class DataExceptionController {
    private final DataExceptionService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Page<DataException>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long syncBatchId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(status, severity, exceptionType, keyword, syncBatchId, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<DataException> handle(@PathVariable Long id,
                                              @Valid @RequestBody DataExceptionHandleRequest req,
                                              Authentication auth) {
        return ApiResponse.ok(service.handle(id, req, auth == null ? null : auth.getName()));
    }
}
