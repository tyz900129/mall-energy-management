package com.wanyuecity.energy.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.workflow.service.ConstructionWorkflowService;
import com.wanyuecity.energy.workflow.service.ConstructionLifecycleService;
import com.wanyuecity.energy.workflow.service.ConstructionAttachmentService;
import com.wanyuecity.energy.verification.service.dto.AttachmentView;
import com.wanyuecity.energy.workflow.entity.ConstructionInspection;
import com.wanyuecity.energy.workflow.entity.ConstructionAcceptance;
import com.wanyuecity.energy.workflow.service.dto.ConstructionInspectionRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionAcceptanceRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionAcceptanceResultRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/construction-workflows")
@RequiredArgsConstructor
public class ConstructionWorkflowController {
    private final ConstructionWorkflowService service;
    private final ConstructionLifecycleService lifecycleService;
    private final ConstructionAttachmentService attachmentService;

    @GetMapping
    public ApiResponse<Page<ConstructionWorkflowView>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String workflowType, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication) {
        return ApiResponse.ok(service.list(status, workflowType, keyword,
                ownOnly(authentication) ? authentication.getName() : null, pageNum, pageSize));
    }

    @GetMapping("/contact-options")
    public ApiResponse<List<Map<String,Object>>> contactOptions(@RequestParam String side) {
        return ApiResponse.ok(service.contactOptions(side));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConstructionWorkflowView> get(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(service.get(id, authentication.getName(), ownOnly(authentication)));
    }

    @PostMapping
    public ApiResponse<ConstructionWorkflowView> create(@Valid @RequestBody ConstructionWorkflowRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.create(request, authentication == null ? null : authentication.getName()));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ConstructionWorkflowView> submit(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(service.submit(id, authentication.getName(), ownOnly(authentication)));
    }

    @PostMapping("/{id}/inspections/{type}")
    public ApiResponse<ConstructionInspection> inspect(@PathVariable Long id, @PathVariable String type,
            @Valid @RequestBody ConstructionInspectionRequest request, Authentication authentication) {
        request.setInspectionType(type);
        return ApiResponse.ok(lifecycleService.inspect(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/inspections/{inspectionId}/close")
    public ApiResponse<ConstructionInspection> closeInspection(@PathVariable Long id, @PathVariable Long inspectionId) {
        return ApiResponse.ok(lifecycleService.closeInspection(id, inspectionId));
    }

    @PostMapping("/{id}/attachments")
    public ApiResponse<AttachmentView> uploadAttachment(@PathVariable Long id, @RequestParam String targetType,
            @RequestParam Long targetId, @RequestParam String photoType, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.ok(attachmentService.upload(id, targetType, targetId, photoType, file, authentication.getName()));
    }

    @GetMapping("/{id}/attachments")
    public ApiResponse<java.util.List<AttachmentView>> attachments(@PathVariable Long id) {
        return ApiResponse.ok(attachmentService.list(id));
    }

    @PostMapping("/{id}/acceptance-request")
    public ApiResponse<ConstructionAcceptance> requestAcceptance(@PathVariable Long id,
            @Valid @RequestBody ConstructionAcceptanceRequest request, Authentication authentication) {
        return ApiResponse.ok(lifecycleService.requestAcceptance(id, request, authentication.getName(), canManage(authentication)));
    }

    @PostMapping("/{id}/acceptance-result")
    public ApiResponse<ConstructionAcceptance> acceptanceResult(@PathVariable Long id,
            @Valid @RequestBody ConstructionAcceptanceResultRequest request, Authentication authentication) {
        return ApiResponse.ok(lifecycleService.decideAcceptance(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ConstructionWorkflowView> cancel(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(service.cancel(id, authentication.getName(), ownOnly(authentication)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ConstructionWorkflowView> complete(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(service.complete(id, authentication == null ? null : authentication.getName()));
    }

    @PostMapping("/{id}/reset")
    public ApiResponse<ConstructionWorkflowView> reset(@PathVariable Long id) {
        return ApiResponse.ok(service.reset(id));
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return ApiResponse.ok();
    }

    private boolean ownOnly(Authentication authentication) {
        if (has(authentication, "ROLE_SUPER_ADMIN") || has(authentication, "ROLE_SYSTEM_ADMIN")) return false;
        if (has(authentication, "ROLE_APPLICANT")) return !has(authentication, "ROLE_VIEWER");
        return has(authentication, "construction.create");
    }

    private boolean canManage(Authentication authentication) {
        return has(authentication, "construction.acceptance.manage");
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
