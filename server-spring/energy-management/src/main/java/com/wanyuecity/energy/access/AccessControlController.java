package com.wanyuecity.energy.access;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/access-control")
@RequiredArgsConstructor
public class AccessControlController {
    private final AccessControlService service;
    private final VisitorAttachmentService visitorAttachments;
    private final AccessEventSyncService eventSyncService;
    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>>dashboard(){return ApiResponse.ok(service.dashboard());}
    @GetMapping("/points") public ApiResponse<List<Map<String,Object>>>points(){return ApiResponse.ok(service.points());}
    @PostMapping("/points") public ApiResponse<Map<String,Object>>createPoint(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePoint(null,b));}
    @PutMapping("/points/{id}") public ApiResponse<Map<String,Object>>updatePoint(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePoint(id,b));}
    @GetMapping("/devices") public ApiResponse<List<Map<String,Object>>>devices(){return ApiResponse.ok(service.devices());}
    @PostMapping("/devices/probe") public ApiResponse<Map<String,Object>>probeDevice(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.probeDevice(b));}
    @PostMapping("/devices/{id}/discover") public ApiResponse<Map<String,Object>>discoverDevice(@PathVariable Long id){return ApiResponse.ok(service.discoverDevice(id));}
    @PostMapping("/devices/{id}/write-mode") public ApiResponse<Map<String,Object>>writeMode(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.setDeviceWriteMode(id,Boolean.TRUE.equals(b.get("enabled"))));}
    @PostMapping("/person-scans") public ApiResponse<Map<String,Object>>startPersonScan(Authentication a){return ApiResponse.ok(service.startPersonScan(a.getName()));}
    @GetMapping("/person-scans/latest") public ApiResponse<Map<String,Object>>latestPersonScan(){return ApiResponse.ok(service.latestPersonScan());}
    @PostMapping("/card-scans") public ApiResponse<Map<String,Object>>startCardScan(Authentication a){return ApiResponse.ok(service.startCardScan(a.getName()));}
    @GetMapping("/card-scans/latest") public ApiResponse<Map<String,Object>>latestCardScan(){return ApiResponse.ok(service.latestCardScan());}
    @PostMapping("/devices") public ApiResponse<Map<String,Object>>createDevice(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveDevice(null,b));}
    @PutMapping("/devices/{id}") public ApiResponse<Map<String,Object>>updateDevice(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveDevice(id,b));}
    @GetMapping("/persons") public ApiResponse<List<Map<String,Object>>>persons(@RequestParam(required=false)String keyword,@RequestParam(required=false)String status){return ApiResponse.ok(service.persons(keyword,status));}
    @GetMapping("/persons/{id}") public ApiResponse<Map<String,Object>>person(@PathVariable Long id,Authentication a){boolean sensitive=a.getAuthorities().stream().anyMatch(x->"ROLE_SUPER_ADMIN".equals(x.getAuthority()));return ApiResponse.ok(service.person(id,sensitive));}
    @GetMapping("/persons/{id}/face") public ApiResponse<Map<String,Object>>personFace(@PathVariable Long id,Authentication a){boolean sensitive=a.getAuthorities().stream().anyMatch(x->"ROLE_SUPER_ADMIN".equals(x.getAuthority()));if(!sensitive)throw com.wanyuecity.energy.core.BizException.forbidden("仅超级管理员可以查看人脸照片");return ApiResponse.ok(service.personFace(id));}
    @PostMapping("/persons") public ApiResponse<Map<String,Object>>createPerson(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePerson(null,b));}
    @PutMapping("/persons/{id}") public ApiResponse<Map<String,Object>>updatePerson(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.savePerson(id,b));}
    @DeleteMapping("/persons/{id}/permanent") public ApiResponse<Void>deletePerson(@PathVariable Long id,Authentication a){requireSuperAdmin(a);service.permanentlyDeletePerson(id);return ApiResponse.ok();}
    @GetMapping("/self") public ApiResponse<Map<String,Object>>self(Authentication a){return ApiResponse.ok(service.self(a.getName()));}
    @PutMapping("/self") public ApiResponse<Map<String,Object>>updateSelf(@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.updateSelf(a.getName(),b));}
    @PostMapping("/self/claim") public ApiResponse<Map<String,Object>>claimSelf(@RequestBody Map<String,Object>b,Authentication a,jakarta.servlet.http.HttpServletRequest r){return ApiResponse.ok(service.claimSelf(a.getName(),b,r.getRemoteAddr()));}
    @GetMapping("/visitors") public ApiResponse<List<Map<String,Object>>>visitors(){return ApiResponse.ok(service.visitors());}
    @GetMapping("/visitors/{id}") public ApiResponse<Map<String,Object>>visitor(@PathVariable Long id,Authentication a){service.requireVisitorAccess(id,a.getName(),has(a,"visitor.view.all"));return ApiResponse.ok(service.visitor(id));}
    @GetMapping("/visitors/mine") public ApiResponse<List<Map<String,Object>>>mine(Authentication a){return ApiResponse.ok(service.mineVisitors(a.getName()));}
    @PostMapping("/visitors") public ApiResponse<Map<String,Object>>createVisitor(@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.createVisitor(b,a.getName()));}
    @PostMapping(value="/visitors/{id}/attachments",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<Map<String,Object>>uploadVisitorPhoto(@PathVariable Long id,@RequestParam String photoType,@RequestPart("file")org.springframework.web.multipart.MultipartFile file,Authentication a){service.requireVisitorAccess(id,a.getName(),has(a,"visitor.view.all"));return ApiResponse.ok(visitorAttachments.upload(id,photoType,file,a.getName()));}
    @GetMapping("/visitors/{id}/attachments") public ApiResponse<List<Map<String,Object>>>visitorPhotos(@PathVariable Long id,Authentication a){service.requireVisitorAccess(id,a.getName(),has(a,"visitor.view.all"));return ApiResponse.ok(visitorAttachments.list(id));}
    @GetMapping("/visitors/{id}/attachments/{attachmentId}") public org.springframework.http.ResponseEntity<?>visitorPhoto(@PathVariable Long id,@PathVariable Long attachmentId,Authentication a){service.requireVisitorAccess(id,a.getName(),has(a,"visitor.view.all"));var c=visitorAttachments.read(id,attachmentId);return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(c.mimeType())).cacheControl(org.springframework.http.CacheControl.noStore()).body(c.resource());}
    @DeleteMapping("/visitors/{id}/permanent") public ApiResponse<Void>deleteVisitor(@PathVariable Long id,Authentication a){requireSuperAdmin(a);service.permanentlyDeleteVisitor(id);return ApiResponse.ok();}
    @PostMapping("/visitors/{id}/decision") public ApiResponse<Map<String,Object>>decision(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.decideVisitor(id,b,a.getName()));}
    @PostMapping("/visitors/{id}/revoke") public ApiResponse<Map<String,Object>>revoke(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.revokeVisitor(id,b,a.getName()));}
    @GetMapping("/renewals") public ApiResponse<List<Map<String,Object>>>renewals(){return ApiResponse.ok(service.renewalApplications());}
    @GetMapping("/renewals/mine") public ApiResponse<List<Map<String,Object>>>mineRenewals(Authentication a){return ApiResponse.ok(service.mineRenewals(a.getName()));}
    @PostMapping("/renewals/mine") public ApiResponse<Map<String,Object>>requestRenewal(Authentication a){return ApiResponse.ok(service.requestRenewal(a.getName()));}
    @PostMapping("/renewals/{id}/decision") public ApiResponse<Map<String,Object>>decideRenewal(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){return ApiResponse.ok(service.decideRenewal(id,b,a.getName()));}
    @GetMapping("/events") public ApiResponse<Map<String,Object>>events(Authentication a,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.events(a.getName(),false,pageNum,pageSize));}
    @GetMapping("/events/mine") public ApiResponse<Map<String,Object>>myEvents(Authentication a,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.events(a.getName(),true,pageNum,pageSize));}
    @PostMapping("/events/sync") public ApiResponse<Map<String,Object>>syncEvents(Authentication a){return ApiResponse.ok(eventSyncService.syncAll(a.getName()));}
    @GetMapping("/events/sync-status") public ApiResponse<Map<String,Object>>eventSyncStatus(){return ApiResponse.ok(eventSyncService.status());}
    @GetMapping("/events/{id}/snapshot") public org.springframework.http.ResponseEntity<?>eventSnapshot(@PathVariable Long id){var s=eventSyncService.snapshot(id);return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(s.mimeType())).cacheControl(org.springframework.http.CacheControl.noStore()).body(new org.springframework.core.io.FileSystemResource(s.path()));}
    @GetMapping("/events/{id}/thumbnail") public org.springframework.http.ResponseEntity<?>eventThumbnail(@PathVariable Long id){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG).cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePrivate()).body(eventSyncService.thumbnail(id));}
    @GetMapping("/reviews") public ApiResponse<List<Map<String,Object>>>reviews(){return ApiResponse.ok(service.reviews());}
    @PostMapping("/reviews/generate") public ApiResponse<Integer>generate(@RequestParam(defaultValue="60")int inactivityDays){return ApiResponse.ok(service.generateInactivityReviews(Math.max(1,inactivityDays)));}
    @PostMapping("/reviews/{id}/decision") public ApiResponse<Void>review(@PathVariable Long id,@RequestBody Map<String,Object>b,Authentication a){service.decideReview(id,b,a.getName());return ApiResponse.ok();}
    private boolean has(Authentication a,String permission){return a.getAuthorities().stream().anyMatch(x->permission.equals(x.getAuthority())||("PERM_"+permission).equals(x.getAuthority())||"ROLE_SUPER_ADMIN".equals(x.getAuthority())||"ROLE_SYSTEM_ADMIN".equals(x.getAuthority()));}
    private void requireSuperAdmin(Authentication a){if(a.getAuthorities().stream().noneMatch(x->"ROLE_SUPER_ADMIN".equals(x.getAuthority())))throw com.wanyuecity.energy.core.BizException.forbidden("仅超级管理员可以彻底删除测试数据");}
}
