package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/operations/form-images")
public class OperationFormImageController {
    private final OperationFormImageService service;
    public OperationFormImageController(OperationFormImageService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Map<String,Object>> upload(@RequestParam String qrToken, @RequestParam String formType,
                                                   @RequestParam String fieldCode, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(qrToken, formType, fieldCode, file));
    }
    @GetMapping("/{id}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> image(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate()).body(service.load(id));
    }
}
