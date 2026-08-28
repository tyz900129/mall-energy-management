package com.wanyuecity.energy.verification.controller;
import com.wanyuecity.energy.core.ApiResponse; import com.wanyuecity.energy.verification.service.VerificationService; import com.wanyuecity.energy.verification.service.dto.*; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.Map; import java.util.List; import org.springframework.http.*; import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/verification") @RequiredArgsConstructor public class VerificationController {
 private final VerificationService service;
 private final com.wanyuecity.energy.verification.service.AttachmentService attachmentService;
 @GetMapping("/tasks/{token}") public ApiResponse<VerificationTaskView> open(@PathVariable String token){return ApiResponse.ok(service.open(token));}
 @PostMapping("/tasks/{token}/start") public ApiResponse<VerificationTaskView> start(@PathVariable String token){return ApiResponse.ok(service.start(token));}
 @PutMapping("/tasks/{token}/devices/{id}") public ApiResponse<VerificationTaskView> device(@PathVariable String token,@PathVariable Long id,@Valid @RequestBody VerificationDeviceUpdateRequest req){return ApiResponse.ok(service.updateDevice(token,id,req));}
 @PostMapping("/tasks/{token}/complete") public ApiResponse<VerificationTaskView> complete(@PathVariable String token,@RequestBody(required=false) Map<String,String> body){return ApiResponse.ok(service.complete(token,body==null?null:body.get("remark")));}
 @PostMapping("/tasks/{token}/devices/{id}/attachments") public ApiResponse<com.wanyuecity.energy.verification.service.dto.AttachmentView> upload(@PathVariable String token,@PathVariable Long id,@RequestParam String photoType,@RequestPart("file") MultipartFile file){return ApiResponse.ok(attachmentService.upload(token,id,photoType,file));}
 @GetMapping("/tasks/{token}/devices/{id}/attachments") public ApiResponse<List<com.wanyuecity.energy.verification.service.dto.AttachmentView>> attachments(@PathVariable String token,@PathVariable Long id){return ApiResponse.ok(attachmentService.list(token,id));}
 @GetMapping("/tasks/{token}/attachments/{id}") public ResponseEntity<org.springframework.core.io.Resource> attachment(@PathVariable String token,@PathVariable Long id){var content=attachmentService.read(token,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+content.fileName().replace("\"","")+"\"").body(content.resource());}
 @GetMapping("/workflow/{workflowId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','AUDITOR','VIEWER')") public ApiResponse<VerificationTaskView> byWorkflow(@PathVariable Long workflowId){return ApiResponse.ok(service.getByWorkflow(workflowId));}
}
