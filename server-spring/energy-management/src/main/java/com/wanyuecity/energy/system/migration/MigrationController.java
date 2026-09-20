package com.wanyuecity.energy.system.migration;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/system/migration")
@RequiredArgsConstructor
public class MigrationController {
    private final MigrationService service;

    @PostMapping("/exports")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.JobView> export(@Valid @RequestBody PasswordRequest request) {
        return ApiResponse.ok(service.startExport(request.password()));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.JobView> job(@PathVariable String id) {
        return ApiResponse.ok(service.getJob(id));
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id, @RequestParam String token) {
        MigrationService.Download download = service.download(id, token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(download.resource());
    }

    @PostMapping("/imports/uploads")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.UploadView> initializeUpload(@Valid @RequestBody UploadInitRequest request) {
        return ApiResponse.ok(service.initializeUpload(request.fileName(), request.fileSize()));
    }

    @PutMapping(value = "/imports/uploads/{id}/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.UploadView> uploadChunk(@PathVariable String id, @PathVariable int index,
                                                                 @RequestBody byte[] bytes) {
        return ApiResponse.ok(service.appendChunk(id, index, bytes));
    }

    @PostMapping("/imports/uploads/{id}/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.JobView> validate(@PathVariable String id,
                                                           @Valid @RequestBody PasswordRequest request) {
        return ApiResponse.ok(service.startValidation(id, request.password()));
    }

    @PostMapping("/imports/{jobId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MigrationService.JobView> restore(@PathVariable String jobId,
                                                          @Valid @RequestBody RestoreRequest request) {
        return ApiResponse.ok(service.startRestore(jobId, request.confirmation()));
    }

    public record PasswordRequest(@NotBlank @Size(min = 8, max = 128) String password) { }
    public record UploadInitRequest(@NotBlank @Size(max = 255) String fileName, @Positive long fileSize) { }
    public record RestoreRequest(@NotBlank String confirmation) { }
}
