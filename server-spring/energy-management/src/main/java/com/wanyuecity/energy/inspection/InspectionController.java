package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
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
 @PostMapping("/types") public ApiResponse<Map<String,Object>> createType(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveType(null,b));}
 @PutMapping("/types/{id}") public ApiResponse<Map<String,Object>> updateType(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveType(id,b));}
    @GetMapping("/templates") public ApiResponse<List<Map<String,Object>>> templates(){return ApiResponse.ok(service.templates());}
 @PostMapping("/templates") public ApiResponse<Map<String,Object>> createTemplate(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveTemplate(null,b));}
 @PutMapping("/templates/{id}") public ApiResponse<Map<String,Object>> updateTemplate(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveTemplate(id,b));}
    @GetMapping("/targets/devices") public ApiResponse<List<Map<String,Object>>> targets(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.targetOptions(keyword));}
    @GetMapping("/owner-options") public ApiResponse<Map<String,Object>> ownerOptions(){return ApiResponse.ok(service.ownerOptions());}
    @GetMapping("/plans") public ApiResponse<List<Map<String,Object>>> plans(){return ApiResponse.ok(service.plans());}
 @PostMapping("/plans") public ApiResponse<Map<String,Object>> createPlan(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePlan(null,b));}
 @PutMapping("/plans/{id}") public ApiResponse<Map<String,Object>> updatePlan(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePlan(id,b));}
 @PostMapping("/tasks/generate") public ApiResponse<Map<String,Object>> generate(@RequestBody(required=false) Map<String,Object> body){Object pid=body==null?null:body.get("planId");Object d=body==null?null:body.get("date");Long planId=pid==null?null:Long.valueOf(String.valueOf(pid));java.time.LocalDate day=(d==null||String.valueOf(d).isBlank())?null:java.time.LocalDate.parse(String.valueOf(d));return ApiResponse.ok(Map.of("generated",service.generate(planId,day)));}
 @GetMapping("/generation-logs") public ApiResponse<List<Map<String,Object>>> generationLogs(@RequestParam(required=false) Long planId,@RequestParam(required=false) String from,@RequestParam(required=false) String to){java.time.LocalDate f=(from==null||from.isBlank())?null:java.time.LocalDate.parse(from);java.time.LocalDate t=(to==null||to.isBlank())?null:java.time.LocalDate.parse(to);return ApiResponse.ok(service.generationLogs(planId,f,t));}
    @GetMapping("/tasks") public ApiResponse<List<Map<String,Object>>> tasks(@RequestParam(required=false)String status,@RequestParam(required=false)String keyword,Authentication a){return ApiResponse.ok(service.tasks(status,keyword,a.getName(),ownOnly(a)));}
    @GetMapping("/tasks/{id}") public ApiResponse<Map<String,Object>> task(@PathVariable Long id){return ApiResponse.ok(service.task(id));}
    @GetMapping("/tasks/token/{token}") public ApiResponse<Map<String,Object>> taskByToken(@PathVariable String token){return ApiResponse.ok(service.taskByToken(token));}
    @GetMapping(value="/tasks/{id}/qr",produces=MediaType.IMAGE_PNG_VALUE) public byte[] qr(@PathVariable Long id){return service.qr(id);}
    @GetMapping(value="/public-qr/{token}",produces=MediaType.IMAGE_PNG_VALUE) public byte[] publicQr(@PathVariable String token){return service.qrByToken(token);}
 @PostMapping("/tasks/{id}/attachments") public ApiResponse<?> upload(@PathVariable Long id,@RequestParam(defaultValue="INSPECTION")String photoType,@RequestPart("file")MultipartFile file,Authentication a){return ApiResponse.ok(attachmentService.upload(id,photoType,file,a.getName()));}
    @GetMapping("/tasks/{id}/attachments") public ApiResponse<?> attachments(@PathVariable Long id){return ApiResponse.ok(attachmentService.list(id));}
    @GetMapping("/tasks/{id}/attachments/{attachmentId}") public ResponseEntity<?> attachment(@PathVariable Long id,@PathVariable Long attachmentId){var c=attachmentService.read(id,attachmentId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mimeType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+c.fileName().replace("\"","")+"\"").body(c.resource());}
 @PostMapping("/tasks/{id}/submit") public ApiResponse<Map<String,Object>> submit(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.submit(id,b,a.getName()));}
    @PostMapping("/tasks/{id}/claim") public ApiResponse<Map<String,Object>> claim(@PathVariable Long id,Authentication a){return ApiResponse.ok(service.claim(id,a.getName()));}
 @PostMapping("/tasks/{id}/reassign") public ApiResponse<Map<String,Object>> reassign(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.reassign(id,b));}
    @GetMapping("/issues") public ApiResponse<List<Map<String,Object>>> issues(@RequestParam(required=false)Long taskId,@RequestParam(required=false)String status){return ApiResponse.ok(service.issues(taskId,status));}
 @PostMapping("/issues/{id}/rectify") public ApiResponse<Map<String,Object>> rectify(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.rectify(id,String.valueOf(b.getOrDefault("note","")),a.getName()));}
 @PostMapping("/issues/{id}/review") public ApiResponse<Map<String,Object>> review(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.review(id,Boolean.TRUE.equals(b.get("passed")),a.getName()));}
    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(){return ApiResponse.ok(service.dashboard());}

    /** 数据范围：仅持 inspection.task.execute 的普通岗位按本人任务；管理员/核实岗保持全局。 */
    private boolean ownOnly(Authentication a){
        boolean global=java.util.Set.of("ROLE_SUPER_ADMIN","ROLE_SYSTEM_ADMIN","ROLE_DEVICE_VERIFIER","ROLE_VIEWER").stream()
                .anyMatch(role->a.getAuthorities().stream().anyMatch(x->role.equals(x.getAuthority())));
        if(global) return false;
        return a.getAuthorities().stream().anyMatch(x->"inspection.task.execute".equals(x.getAuthority()));
    }
}
