package com.wanyuecity.energy.workflow.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.workflow.service.ConstructionLifecycleService;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/construction-public")
@RequiredArgsConstructor
public class ConstructionPublicController {
    private final ConstructionLifecycleService service;
    private final com.wanyuecity.energy.workflow.service.ConstructionAttachmentService attachmentService;

    @GetMapping("/{token}")
    public ApiResponse<ConstructionWorkflowView> get(@PathVariable String token) {
        return ApiResponse.ok(service.publicView(token));
    }

    @GetMapping(value = "/{token}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).contentType(MediaType.IMAGE_PNG).body(service.qr(token));
    }

    @GetMapping("/{token}/attachments/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> attachment(@PathVariable String token, @PathVariable Long id) {
        var content = attachmentService.read(token, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.fileName().replace("\"", "") + "\"")
                .body(content.resource());
    }
}
