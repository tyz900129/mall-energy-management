package com.wanyuecity.energy.patrol;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/patrol-public")
public class PatrolPublicController {
    private final PatrolService service;
    private final PatrolAttachmentService attachmentService;

    public PatrolPublicController(PatrolService service, PatrolAttachmentService attachmentService) {
        this.service = service;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/issues/{token}")
    public ApiResponse<Map<String, Object>> issue(@PathVariable String token) {
        return ApiResponse.ok(service.publicIssue(token));
    }

    @PostMapping(value = "/submissions/{submissionId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<java.util.List<Map<String, Object>>> appendPhotos(@PathVariable Long submissionId,
                                                                        @RequestParam String token,
                                                                        @RequestParam("photos") MultipartFile[] photos) {
        return ApiResponse.ok(service.publicAppendPhotos(token, submissionId, photos));
    }

    @PostMapping(value = "/issues/{token}/feedback", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> feedback(@PathVariable String token,
                                                     @RequestParam(required = false) String note,
                                                     @RequestParam(value = "photos", required = false) MultipartFile[] photos,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(service.publicFeedback(token, note, photos, clientIp(request)));
    }

    @GetMapping("/departments/{token}")
    public ApiResponse<Map<String, Object>> department(@PathVariable String token,
                                                        @RequestParam(required = false, defaultValue = "open") String range) {
        return ApiResponse.ok(service.publicDepartment(token, range));
    }

    @GetMapping("/attachments/{token}/{attachmentId}")
    public ResponseEntity<Resource> attachment(@PathVariable String token, @PathVariable Long attachmentId) {
        Resource resource = service.publicAttachment(token, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(attachmentService.mimeType(attachmentId)))
                .body(resource);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
