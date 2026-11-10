/*
 * ============================================================================
 * 【反编译还原 · 仅供 diff 参考 · 请勿基于本文件改代码上线】
 *
 * 本文件由线上运行产物反编译还原，不是原始源码：
 *   来源 war   : /home/admin/energy-deploy/energy-management.war
 *   war sha256 : cca2127f7007f817ff5e3e9d6af3056ad7d18f9278a2d3471d53ae1fc1b37722
 *   来源 class : WEB-INF/classes/com/wanyuecity/energy/inspection/InspectionController.class
 *   反编译工具 : CFR 0.152（-extraclasspath 含同类内部类）
 *   还原时间   : 2026-11-10
 *
 * 复核状态（2026-11-10；本次仅做"让基线可编译"的最小修正，不是功能变更）：
 *   [已复核·未改动] 本文件无需修正即可编译；方法列表（含线上独有的 claim / ownerOptions /
 *     reassign）已用 javap -p 与线上 class 逐项核对一致，未做任何结构调整。
 *   [仍未确认] 注解书写形态（如 value={"/x"}）、局部变量名、注释等源码形态；字节码无法区分。
 *
 * 编译状态   : mvn -B -DskipTests compile 通过；mvn -B test 68/68 通过（2026-11-10，JDK 17）。
 *              javap 成员列表与线上 class 一致。
 *
 * 用途：baseline/deployed-20261109 的对照锚点，用于回答"线上 2026-11-09 的构建到底包含
 *      哪些代码"。可用于 diff；这不是原始源码，改动前请先确认真实源码位置。
 * ============================================================================
 *//*

 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.wanyuecity.energy.core.ApiResponse
 *  com.wanyuecity.energy.inspection.InspectionAttachmentService
 *  com.wanyuecity.energy.inspection.InspectionAttachmentService$ResourceContent
 *  lombok.Generated
 *  org.springframework.http.MediaType
 *  org.springframework.http.ResponseEntity
 *  org.springframework.http.ResponseEntity$BodyBuilder
 *  org.springframework.security.access.prepost.PreAuthorize
 *  org.springframework.security.core.Authentication
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RequestPart
 *  org.springframework.web.bind.annotation.RestController
 *  org.springframework.web.multipart.MultipartFile
 */
package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.inspection.InspectionAttachmentService;
import com.wanyuecity.energy.inspection.InspectionService;
import java.util.List;
import java.util.Map;
import lombok.Generated;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value={"/api/v1/inspections"})
public class InspectionController {
    private final InspectionService service;
    private final InspectionAttachmentService attachmentService;

    @GetMapping(value={"/types"})
    public ApiResponse<List<Map<String, Object>>> types() {
        return ApiResponse.ok(this.service.types());
    }

    @PostMapping(value={"/types"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> createType(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.saveType(null, b));
    }

    @PutMapping(value={"/types/{id}"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> updateType(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.saveType(id, b));
    }

    @GetMapping(value={"/templates"})
    public ApiResponse<List<Map<String, Object>>> templates() {
        return ApiResponse.ok(this.service.templates());
    }

    @PostMapping(value={"/templates"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.saveTemplate(null, b));
    }

    @PutMapping(value={"/templates/{id}"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.saveTemplate(id, b));
    }

    @GetMapping(value={"/targets/devices"})
    public ApiResponse<List<Map<String, Object>>> targets(@RequestParam(required=false) String keyword) {
        return ApiResponse.ok(this.service.targetOptions(keyword));
    }

    @GetMapping(value={"/owner-options"})
    public ApiResponse<Map<String, Object>> ownerOptions() {
        return ApiResponse.ok(this.service.ownerOptions());
    }

    @GetMapping(value={"/plans"})
    public ApiResponse<List<Map<String, Object>>> plans() {
        return ApiResponse.ok(this.service.plans());
    }

    @PostMapping(value={"/plans"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> createPlan(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.savePlan(null, b));
    }

    @PutMapping(value={"/plans/{id}"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> updatePlan(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.savePlan(id, b));
    }

    @PostMapping(value={"/tasks/generate"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> generate() {
        return ApiResponse.ok(Map.of("generated", this.service.generateAll()));
    }

    @GetMapping(value={"/tasks"})
    public ApiResponse<List<Map<String, Object>>> tasks(@RequestParam(required=false) String status, @RequestParam(required=false) String keyword, Authentication a) {
        return ApiResponse.ok(this.service.tasks(status, keyword, a.getName(), this.ownOnly(a)));
    }

    @GetMapping(value={"/tasks/{id}"})
    public ApiResponse<Map<String, Object>> task(@PathVariable Long id) {
        return ApiResponse.ok(this.service.task(id));
    }

    @GetMapping(value={"/tasks/token/{token}"})
    public ApiResponse<Map<String, Object>> taskByToken(@PathVariable String token) {
        return ApiResponse.ok(this.service.taskByToken(token));
    }

    @GetMapping(value={"/tasks/{id}/qr"}, produces={"image/png"})
    public byte[] qr(@PathVariable Long id) {
        return this.service.qr(id);
    }

    @GetMapping(value={"/public-qr/{token}"}, produces={"image/png"})
    public byte[] publicQr(@PathVariable String token) {
        return this.service.qrByToken(token);
    }

    @PostMapping(value={"/tasks/{id}/attachments"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<?> upload(@PathVariable Long id, @RequestParam(defaultValue="INSPECTION") String photoType, @RequestPart(value="file") MultipartFile file, Authentication a) {
        return ApiResponse.ok((Object)this.attachmentService.upload(id, photoType, file, a.getName()));
    }

    @GetMapping(value={"/tasks/{id}/attachments"})
    public ApiResponse<?> attachments(@PathVariable Long id) {
        return ApiResponse.ok((Object)this.attachmentService.list(id));
    }

    @GetMapping(value={"/tasks/{id}/attachments/{attachmentId}"})
    public ResponseEntity<?> attachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        InspectionAttachmentService.ResourceContent c = this.attachmentService.read(id, attachmentId);
        return ((ResponseEntity.BodyBuilder)ResponseEntity.ok().contentType(MediaType.parseMediaType((String)c.mimeType())).header("Content-Disposition", new String[]{"inline; filename=\"" + c.fileName().replace("\"", "") + "\""})).body((Object)c.resource());
    }

    @PostMapping(value={"/tasks/{id}/submit"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        return ApiResponse.ok(this.service.submit(id, b, a.getName()));
    }

    @PostMapping(value={"/tasks/{id}/claim"})
    public ApiResponse<Map<String, Object>> claim(@PathVariable Long id, Authentication a) {
        return ApiResponse.ok(this.service.claim(id, a.getName()));
    }

    @PostMapping(value={"/tasks/{id}/reassign"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> reassign(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(this.service.reassign(id, b));
    }

    @GetMapping(value={"/issues"})
    public ApiResponse<List<Map<String, Object>>> issues(@RequestParam(required=false) Long taskId, @RequestParam(required=false) String status) {
        return ApiResponse.ok(this.service.issues(taskId, status));
    }

    @PostMapping(value={"/issues/{id}/rectify"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Map<String, Object>> rectify(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        return ApiResponse.ok(this.service.rectify(id, String.valueOf(b.getOrDefault("note", "")), a.getName()));
    }

    @PostMapping(value={"/issues/{id}/review"})
    @PreAuthorize(value="hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id, @RequestBody Map<String, Object> b, Authentication a) {
        return ApiResponse.ok(this.service.review(id, Boolean.TRUE.equals(b.get("passed")), a.getName()));
    }

    @GetMapping(value={"/dashboard"})
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(this.service.dashboard());
    }

    private boolean ownOnly(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x -> "PERMISSION_BRIDGE:inspection.task.execute".equals(x.getAuthority())) && a.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_SYSTEM_ADMIN") || x.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    @Generated
    public InspectionController(InspectionService service, InspectionAttachmentService attachmentService) {
        this.service = service;
        this.attachmentService = attachmentService;
    }
}
