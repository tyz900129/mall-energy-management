package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inspections")
@RequiredArgsConstructor
public class InspectionController {
    private final InspectionService service;
    private final InspectionAttachmentService attachmentService;

    @GetMapping("/types") public ApiResponse<List<Map<String,Object>>> types(){return ApiResponse.ok(service.types());}
    @PostMapping("/types") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> createType(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveType(null,b));}
    @PutMapping("/types/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> updateType(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveType(id,b));}
    @GetMapping("/templates") public ApiResponse<List<Map<String,Object>>> templates(){return ApiResponse.ok(service.templates());}
    @PostMapping("/templates") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> createTemplate(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveTemplate(null,b));}
    @PutMapping("/templates/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> updateTemplate(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveTemplate(id,b));}
    @GetMapping("/targets/devices") public ApiResponse<List<Map<String,Object>>> targets(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.targetOptions(keyword));}
    @GetMapping("/plans") public ApiResponse<List<Map<String,Object>>> plans(){return ApiResponse.ok(service.plans());}
    @PostMapping("/plans") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> createPlan(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePlan(null,b));}
    @PutMapping("/plans/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> updatePlan(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePlan(id,b));}
    @PostMapping("/tasks/generate") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> generate(){return ApiResponse.ok(Map.of("generated",service.generateAll()));}
    @GetMapping("/tasks") public ApiResponse<List<Map<String,Object>>> tasks(@RequestParam(required=false)String status,@RequestParam(required=false)String keyword,Authentication a){return ApiResponse.ok(service.tasks(status,keyword,a.getName(),ownOnly(a)));}
    @GetMapping("/tasks/{id}") public ApiResponse<Map<String,Object>> task(@PathVariable Long id){return ApiResponse.ok(service.task(id));}
    @GetMapping("/tasks/token/{token}") public ApiResponse<Map<String,Object>> taskByToken(@PathVariable String token){return ApiResponse.ok(service.taskByToken(token));}
    @GetMapping(value="/tasks/{id}/qr",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable Long id){return service.qr(id);}
    @GetMapping(value="/public-qr/{token}",produces=MediaType.IMAGE_PNG_VALUE) public byte[] publicQr(@PathVariable String token){return service.qrByToken(token);}
    @PostMapping("/tasks/{id}/attachments") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')") public ApiResponse<?> upload(@PathVariable Long id,@RequestParam(defaultValue="INSPECTION")String photoType,@RequestPart("file")MultipartFile file,Authentication a){return ApiResponse.ok(attachmentService.upload(id,photoType,file,a.getName()));}
    @GetMapping("/tasks/{id}/attachments") public ApiResponse<?> attachments(@PathVariable Long id){return ApiResponse.ok(attachmentService.list(id));}
    @GetMapping("/tasks/{id}/attachments/{attachmentId}") public ResponseEntity<?> attachment(@PathVariable Long id,@PathVariable Long attachmentId){var c=attachmentService.read(id,attachmentId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mimeType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+c.fileName().replace("\"","")+"\"").body(c.resource());}
    @PostMapping("/tasks/{id}/submit") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')") public ApiResponse<Map<String,Object>> submit(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.submit(id,b,a.getName()));}
    @GetMapping("/issues") public ApiResponse<List<Map<String,Object>>> issues(@RequestParam(required=false)Long taskId,@RequestParam(required=false)String status){return ApiResponse.ok(service.issues(taskId,status));}
    @PostMapping("/issues/{id}/rectify") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')") public ApiResponse<Map<String,Object>> rectify(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.rectify(id,String.valueOf(b.getOrDefault("note","")),a.getName()));}
    @PostMapping("/issues/{id}/review") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<Map<String,Object>> review(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.review(id,Boolean.TRUE.equals(b.get("passed")),a.getName()));}
    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(){return ApiResponse.ok(service.dashboard());}

    private boolean ownOnly(Authentication a){return a.getAuthorities().stream().anyMatch(x->"PERMISSION_BRIDGE:inspection.task.execute".equals(x.getAuthority()))&&a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("ROLE_SYSTEM_ADMIN")||x.getAuthority().equals("ROLE_SUPER_ADMIN"));}
}
