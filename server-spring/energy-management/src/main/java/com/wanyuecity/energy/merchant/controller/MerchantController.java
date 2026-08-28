package com.wanyuecity.energy.merchant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.merchant.service.MerchantService;
import com.wanyuecity.energy.merchant.service.dto.MerchantUpsertRequest;
import com.wanyuecity.energy.merchant.service.dto.MerchantView;
import com.wanyuecity.energy.platform.service.MerchantCodeExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Page<MerchantView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(keyword, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<MerchantView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<MerchantView> create(@Valid @RequestBody MerchantUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<MerchantView> update(@PathVariable Long id, @Valid @RequestBody MerchantUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @GetMapping("/code-preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<MerchantCodeExtractor.Match> preview(@RequestParam("name") String name) {
        return ApiResponse.ok(service.previewCode(name));
    }

    /**
     * spec §4：{@code GET /merchants/{code}} — 按商户代码（6 位）查详情，
     * 包含已绑定的本地铺位、关联的源平台账户列表。
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')")
    public ApiResponse<MerchantView> getByCode(@PathVariable("code") String code) {
        return ApiResponse.ok(service.getByCode(code));
    }
}

