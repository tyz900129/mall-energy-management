package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops-patrol-public")
public class OpsPatrolPublicController {
    private final OpsPatrolService service;

    public OpsPatrolPublicController(OpsPatrolService service) { this.service = service; }

    @GetMapping("/entry")
    public ApiResponse<Map<String, Object>> entry(@RequestParam String token) {
        return ApiResponse.ok(service.publicEntry(token));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> task(@RequestParam String token, @PathVariable Long taskId) {
        return ApiResponse.ok(service.publicTask(token, taskId));
    }

    @PostMapping("/tasks/{taskId}/items")
    public ApiResponse<Map<String, Object>> saveItems(@RequestParam String token, @PathVariable Long taskId,
                                                       @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.ok(service.publicSaveItems(token, taskId, body, clientIp(request)));
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestParam String token, @PathVariable Long taskId,
                                                    @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.ok(service.publicSubmit(token, taskId, body, clientIp(request)));
    }

    @PostMapping("/tasks/{taskId}/confirm-handover")
    public ApiResponse<Map<String, Object>> confirmHandover(@RequestParam String token, @PathVariable Long taskId,
                                                             @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.ok(service.publicConfirmHandover(token, taskId, body, clientIp(request)));
    }

    @GetMapping("/stores")
    public ApiResponse<List<Map<String, Object>>> stores(@RequestParam String token, @RequestParam String keyword) {
        return ApiResponse.ok(service.publicStores(token, keyword));
    }

    @PostMapping(value = "/issues/{issueId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<Map<String, Object>>> uploadPhotos(@RequestParam String token, @PathVariable Long issueId,
                                                                @RequestParam("photos") MultipartFile[] photos) {
        return ApiResponse.ok(service.publicUploadPhotos(token, issueId, photos));
    }

    @GetMapping("/attachments/{token}/{attachmentId}")
    public ResponseEntity<Resource> attachment(@PathVariable String token, @PathVariable Long attachmentId) {
        Resource resource = service.publicAttachment(token, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(service.attachmentMime(attachmentId)))
                .body(resource);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
