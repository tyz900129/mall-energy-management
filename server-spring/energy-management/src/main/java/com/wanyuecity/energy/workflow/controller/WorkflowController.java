package com.wanyuecity.energy.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.workflow.entity.WorkflowDevice;
import com.wanyuecity.energy.workflow.entity.WorkflowExpectedChange;
import com.wanyuecity.energy.workflow.service.WorkflowService;
import com.wanyuecity.energy.workflow.service.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<BillingChangePrecheckView> precheckBillingChange(@Valid @RequestBody BillingChangeRequest req) {
        return ApiResponse.ok(service.precheckBillingChange(req));
    }

    @PostMapping("/billing-change")
    public ApiResponse<WorkflowView> createBillingChange(@Valid @RequestBody BillingChangeRequest req, Authentication auth) {
        return ApiResponse.ok(service.createBillingChange(req, auth == null ? null : auth.getName()));
    }

    @PostMapping("/precheck/new-merchant")
    public ApiResponse<NewMerchantPrecheckView> precheck(@Valid @RequestBody NewMerchantPrecheckRequest req) {
        return ApiResponse.ok(service.precheckNewMerchant(req));
    }

    @PostMapping
    public ApiResponse<WorkflowView> create(@Valid @RequestBody NewMerchantWorkflowRequest req, Authentication auth) {
        return ApiResponse.ok(service.createNewMerchant(req, auth == null ? null : auth.getName()));
    }

    @GetMapping
    public ApiResponse<Page<WorkflowView>> list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String workflowType, @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize,Authentication auth) {
        return ApiResponse.ok(service.list(status, workflowType, keyword, ownOnly(auth)?auth.getName():null, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowView> get(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.getAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/submit")
    public ApiResponse<WorkflowView> submit(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.submitAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/cancel")
    public ApiResponse<WorkflowView> cancel(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.cancelAccessible(id,auth.getName(),ownOnly(auth))); }

    @PostMapping("/{id}/reset")
    public ApiResponse<WorkflowView> reset(@PathVariable Long id) { return ApiResponse.ok(service.reset(id)); }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) { service.permanentDelete(id); return ApiResponse.ok(); }

    @GetMapping("/{id}/devices")
    public ApiResponse<List<WorkflowDevice>> devices(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.devicesAccessible(id,auth.getName(),ownOnly(auth))); }

    @GetMapping("/{id}/expected-changes")
    public ApiResponse<List<WorkflowExpectedChange>> changes(@PathVariable Long id,Authentication auth) { return ApiResponse.ok(service.changesAccessible(id,auth.getName(),ownOnly(auth))); }
 @GetMapping("/{id}/match-evidence") public ApiResponse<Map<String,Object>> evidence(@PathVariable Long id){return ApiResponse.ok(service.matchEvidence(id));}
 @PostMapping("/{id}/match-resolution") public ApiResponse<WorkflowView> resolve(@PathVariable Long id,@Valid @RequestBody MatchResolutionRequest req,Authentication auth){return ApiResponse.ok(service.resolveMatch(id,req,auth==null?null:auth.getName()));}
 @PutMapping("/{id}/oa-process-no") public ApiResponse<WorkflowView> updateOaProcessNo(@PathVariable Long id,@RequestBody WorkflowPlatformInfoRequest req){return ApiResponse.ok(service.updateOaProcessNo(id,req.getValue()));}
 @PutMapping("/{id}/completion-remark") public ApiResponse<WorkflowView> updateCompletionRemark(@PathVariable Long id,@RequestBody WorkflowPlatformInfoRequest req){return ApiResponse.ok(service.updateCompletionRemark(id,req.getValue()));}
    /** 数据范围：持有 workflow.view.all 为全局；否则仅本人发起/参与。 */
    static boolean ownOnly(Authentication auth){
        Set<String> authorities = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        if (authorities.contains("workflow.view.all")) return false;
        // APPLICANT（商管课长）按本人范围；无 workflow.create 的只读角色维持既有全局行为
        return authorities.contains("workflow.create") || authorities.contains("ROLE_APPLICANT");
    }
}
