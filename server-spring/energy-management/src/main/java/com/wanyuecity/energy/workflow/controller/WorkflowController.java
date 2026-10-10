package com.wanyuecity.energy.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.workflow.entity.WorkflowDevice;
import com.wanyuecity.energy.workflow.entity.WorkflowExpectedChange;
import com.wanyuecity.energy.workflow.service.WorkflowService;
import com.wanyuecity.energy.workflow.service.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService service;

    @PostMapping("/precheck/billing-change")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<BillingChangePrecheckView> precheckBillingChange(@Valid @RequestBody BillingChangeRequest req) {
        return ApiResponse.ok(service.precheckBillingChange(req));
    }

    @PostMapping("/billing-change")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<WorkflowView> createBillingChange(@Valid @RequestBody BillingChangeRequest req, Authentication auth) {
        return ApiResponse.ok(service.createBillingChange(req, auth == null ? null : auth.getName()));
    }

    @PostMapping("/precheck/new-merchant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<NewMerchantPrecheckView> precheck(@Valid @RequestBody NewMerchantPrecheckRequest req) {
        return ApiResponse.ok(service.precheckNewMerchant(req));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<WorkflowView> create(@Valid @RequestBody NewMerchantWorkflowRequest req, Authentication auth) {
        return ApiResponse.ok(service.createNewMerchant(req, auth == null ? null : auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','PLATFORM_OPERATOR','AUDITOR','VIEWER')")
    public ApiResponse<Page<WorkflowView>> list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String workflowType, @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize,Authentication auth) {
        return ApiResponse.ok(service.list(status, workflowType, keyword, ownOnly(auth)?auth.getName():null, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','PLATFORM_OPERATOR','AUDITOR','VIEWER')")
    public ApiResponse<WorkflowView> get(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.getAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<WorkflowView> submit(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.submitAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<WorkflowView> cancel(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.cancelAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<WorkflowView> reset(@PathVariable Long id) { return ApiResponse.ok(service.reset(id)); }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) { service.permanentDelete(id); return ApiResponse.ok(); }

    @GetMapping("/{id}/devices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','PLATFORM_OPERATOR','AUDITOR','VIEWER')")
    public ApiResponse<List<WorkflowDevice>> devices(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.devicesAccessible(id,auth.getName(),ownOnly(auth))); }

    @GetMapping("/{id}/expected-changes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','PLATFORM_OPERATOR','AUDITOR','VIEWER')")
    public ApiResponse<List<WorkflowExpectedChange>> changes(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.changesAccessible(id,auth.getName(),ownOnly(auth))); }
    @GetMapping("/{id}/match-evidence") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','AUDITOR','VIEWER')") public ApiResponse<Map<String,Object>> evidence(@PathVariable Long id){return ApiResponse.ok(service.matchEvidence(id));}
    @PostMapping("/{id}/match-resolution") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<WorkflowView> resolve(@PathVariable Long id,@Valid @RequestBody MatchResolutionRequest req,Authentication auth){return ApiResponse.ok(service.resolveMatch(id,req,auth==null?null:auth.getName()));}
    @PutMapping("/{id}/oa-process-no") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<WorkflowView> updateOaProcessNo(@PathVariable Long id,@RequestBody WorkflowPlatformInfoRequest req){return ApiResponse.ok(service.updateOaProcessNo(id,req.getValue()));}
    @PutMapping("/{id}/completion-remark") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<WorkflowView> updateCompletionRemark(@PathVariable Long id,@RequestBody WorkflowPlatformInfoRequest req){return ApiResponse.ok(service.updateCompletionRemark(id,req.getValue()));}
    static boolean ownOnly(Authentication auth){
        Set<String> authorities = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean elevated = authorities.stream().anyMatch(Set.of(
                "ROLE_SUPER_ADMIN", "ROLE_SYSTEM_ADMIN", "ROLE_DEVICE_VERIFIER",
                "ROLE_PLATFORM_OPERATOR", "ROLE_AUDITOR")::contains);
        if (elevated) return false;
        // VIEWER is commonly retained when a self-registered user is later assigned
        // APPLICANT. It must never turn applicant-scoped workflow access into global access.
        return authorities.contains("ROLE_APPLICANT")
                || authorities.contains("workflow.create")
                || authorities.contains("PERMISSION_BRIDGE:workflow.create");
    }
}
