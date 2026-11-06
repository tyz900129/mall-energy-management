package com.wanyuecity.energy.qrinspection.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.qrinspection.entity.*;
import com.wanyuecity.energy.qrinspection.service.QrInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/qr-inspection")
@RequiredArgsConstructor
public class QrInspectionController {

    private final QrInspectionService service;

    private static final String SUBMIT_ROLES =
            "hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','OPS_PATROL_MANAGER','PROPERTY_MANAGER','DEVICE_VERIFIER')";

    // ======= 扫码入口（H5） =======

    @GetMapping("/stalls/{token}/entry")
    public ApiResponse<Map<String, Object>> available(
            @PathVariable String token, Authentication auth) {
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        return ApiResponse.ok(service.availableScenes(token, auth));
    }

    /** 页面上下文：从群消息链接进入时带回场景/计划/任务与表单结构 */
    @GetMapping("/context")
    @PreAuthorize(SUBMIT_ROLES)
    public ApiResponse<Map<String, Object>> context(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long taskId,
            Authentication auth) {
        return ApiResponse.ok(service.context(scene, planId, taskId, auth));
    }

    /** 铺位模糊搜索（商管按处室楼层过滤，物业全量） */
    @GetMapping("/stalls/search")
    @PreAuthorize(SUBMIT_ROLES)
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scene,
            Authentication auth) {
        return ApiResponse.ok(service.searchStalls(keyword, scene, auth));
    }

    /** 按所选铺位提交（搜索/扫码通用） */
    @PostMapping("/submit")
    @PreAuthorize(SUBMIT_ROLES)
    public ApiResponse<QrInspectionRecord> submit(
            @RequestBody QrInspectionRecord record,
            Authentication auth) {
        return ApiResponse.ok(service.submit(record, auth));
    }

    /** 扫码链接提交（兼容原入口） */
    @PostMapping("/stalls/{token}/submit")
    @PreAuthorize(SUBMIT_ROLES)
    public ApiResponse<QrInspectionRecord> submitByToken(
            @PathVariable String token,
            @RequestBody QrInspectionRecord record,
            Authentication auth) {
        return ApiResponse.ok(service.submitByToken(record, token, auth));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','OPS_PATROL_MANAGER','PROPERTY_MANAGER','DEVICE_VERIFIER') "
            + "or hasAuthority('qr.inspection.config') or hasAuthority('qr.inspection.plan.manage') or hasAuthority('qr.inspection.view')")
    public ApiResponse<List<Map<String, Object>>> departments() {
        return ApiResponse.ok(service.listDepartments());
    }

    // ======= 模板配置（PC 后台，直接修改不升版本） =======

    @GetMapping("/templates")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('qr.inspection.config')")
    public ApiResponse<List<QrInspectionTemplate>> templates(@RequestParam(required = false) String scene) {
        return ApiResponse.ok(service.listTemplates(scene));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('qr.inspection.config')")
    public ApiResponse<QrInspectionTemplate> saveTemplate(
            @RequestBody QrInspectionTemplate t, Authentication auth) {
        return ApiResponse.ok(service.saveTemplate(t, auth.getName()));
    }

    // ======= 周期计划（部门月度） =======

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('qr.inspection.view') or hasAuthority('qr.inspection.plan.manage')")
    public ApiResponse<List<QrInspectionPlan>> plans(@RequestParam(required = false) String scene) {
        return ApiResponse.ok(service.listPlans(scene));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('qr.inspection.plan.manage')")
    public ApiResponse<QrInspectionPlan> savePlan(@RequestBody QrInspectionPlan p) {
        return ApiResponse.ok(service.savePlan(p));
    }

    @GetMapping("/plans/{id}/rounds")
    @PreAuthorize("hasAuthority('qr.inspection.view') or hasAuthority('qr.inspection.plan.manage')")
    public ApiResponse<List<QrInspectionPlanRound>> planRounds(@PathVariable Long id) {
        return ApiResponse.ok(service.planRounds(id));
    }

    // ======= 联合巡检 =======

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('qr.inspection.view') or hasAuthority('qr.inspection.task.create')")
    public ApiResponse<List<QrInspectionTask>> tasks() {
        return ApiResponse.ok(service.listTasks());
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('qr.inspection.view') or hasAuthority('qr.inspection.task.create')")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.taskDetail(id));
    }

    @PostMapping("/joint/create")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('qr.inspection.task.create')")
    public ApiResponse<QrInspectionTask> createJoint(
            @RequestBody Map<String, Object> body, Authentication auth) {
        QrInspectionTask t = new QrInspectionTask();
        t.setName((String) body.get("name"));
        t.setStartAt(java.time.LocalDateTime.parse((String) body.get("startAt")));
        t.setEndAt(java.time.LocalDateTime.parse((String) body.get("endAt")));
        t.setRemark((String) body.get("remark"));
        t.setCreatedBy(auth.getName());
        t.setCreatedByName(displayNameOf(auth));
        @SuppressWarnings("unchecked")
        List<String> deptCodes = (List<String>) body.get("participatingDeptCodes");
        return ApiResponse.ok(service.createJoint(t, deptCodes));
    }

    @PostMapping("/tasks/{id}/close")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('qr.inspection.task.create')")
    public ApiResponse<Void> closeTask(@PathVariable Long id, Authentication auth) {
        service.closeJoint(id, auth.getName());
        return ApiResponse.ok(null);
    }

    // ======= 整改 H5（可匿名，只要 token 对） =======

    @GetMapping("/rectify/{token}")
    public ApiResponse<QrInspectionIssue> rectifyByToken(@PathVariable String token) {
        return ApiResponse.ok(service.issueByToken(token));
    }

    @PostMapping("/rectify/{token}")
    public ApiResponse<QrInspectionIssue> rectifySubmit(
            @PathVariable String token,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        String text = (String) body.get("feedbackText");
        @SuppressWarnings("unchecked")
        List<String> photos = (List<String>) body.get("feedbackPhotos");
        String byName = auth != null ? displayNameOf(auth) : (String) body.get("submitterName");
        String byId   = auth != null ? auth.getName() : null;
        return ApiResponse.ok(service.feedback(token, text, photos, byName, byId));
    }

    // ======= 巡检记录查询（仅超级管理员 / 系统管理员） =======

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> records(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listRecords(scene, keyword, start, end, page, size));
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> recordDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.recordDetail(id));
    }

    // ======= 报告 =======

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('qr.inspection.view')")
    public ApiResponse<Map<String, Object>> report(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long departmentId) {
        return ApiResponse.ok(service.report(scene, start, end, departmentId));
    }

    private String displayNameOf(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return auth.getName(); // 简化：直接用 username，Service 内部查 user 表
    }
}
