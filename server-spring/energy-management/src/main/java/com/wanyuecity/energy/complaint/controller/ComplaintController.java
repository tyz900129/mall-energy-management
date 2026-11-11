package com.wanyuecity.energy.complaint.controller;

import com.wanyuecity.energy.complaint.service.ComplaintExcelService;
import com.wanyuecity.energy.complaint.service.ComplaintOptionService;
import com.wanyuecity.energy.complaint.service.ComplaintService;
import com.wanyuecity.energy.complaint.service.ComplaintStaffService;
import com.wanyuecity.energy.complaint.service.ComplaintStatsService;
import com.wanyuecity.energy.complaint.service.dto.ComplaintQuery;
import com.wanyuecity.energy.complaint.service.dto.ComplaintRequest;
import com.wanyuecity.energy.complaint.service.dto.ComplaintStatsQuery;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService service;
    private final ComplaintOptionService optionService;
    private final ComplaintStatsService statsService;
    private final ComplaintExcelService excelService;
    private final ComplaintStaffService staffService;

    // ================= 客诉单 =================

    @GetMapping
    @PreAuthorize("hasAuthority('complaint.view')")
    public ApiResponse<Map<String, Object>> list(ComplaintQuery query, Authentication auth) {
        return ApiResponse.ok(service.list(query, service.scopeUserId(auth.getAuthorities())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('complaint.view')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(service.detail(id, service.scopeUserId(auth.getAuthorities())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('complaint.create')")
    public ApiResponse<Map<String, Object>> create(@RequestBody ComplaintRequest request, Authentication auth) {
        return ApiResponse.ok(service.create(request, service.currentUser(auth.getName())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('complaint.update')")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody ComplaintRequest request, Authentication auth) {
        return ApiResponse.ok(service.update(id, request, service.currentUser(auth.getName()), service.scopeUserId(auth.getAuthorities())));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('complaint.update')")
    public ApiResponse<Map<String, Object>> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        return ApiResponse.ok(service.changeStatus(id, body.get("status"), body.get("note"), service.currentUser(auth.getName()), service.scopeUserId(auth.getAuthorities())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('complaint.close')")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                                  Authentication auth) {
        Map<String, String> payload = body == null ? Map.of() : body;
        return ApiResponse.ok(service.close(id, payload.get("satisfaction"), payload.get("note"), service.currentUser(auth.getName()), service.scopeUserId(auth.getAuthorities())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('complaint.delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    // ================= 登记选项 =================

    @GetMapping("/options/brands")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<List<Map<String, Object>>> brands(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String businessType) {
        return ApiResponse.ok(optionService.brands(keyword, businessType));
    }

    @GetMapping("/options/brands/by-token/{token}")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<Map<String, Object>> brandByToken(@PathVariable String token) {
        return ApiResponse.ok(optionService.brandByToken(token));
    }

    @PostMapping("/options/brands")
    @PreAuthorize("hasAuthority('complaint.create') or hasAuthority('complaint.brand.manage')")
    public ApiResponse<Map<String, Object>> createBrand(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(optionService.createBrand(body));
    }

    @GetMapping("/options/users")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String roleCodes,
                                                         @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(optionService.users(roleCodes, keyword));
    }

    /** 录入时选择经办人/责任商管名单。 */
    @GetMapping("/options/staff")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<List<Map<String, Object>>> staffOptions(@RequestParam(required = false) String type,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(staffService.options(type, keyword));
    }

    /** 铺位责任商管账号映射为名单人员（选中品牌时自动带出）。 */
    @GetMapping("/options/staff/by-users")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<List<Map<String, Object>>> staffByUsers(@RequestParam List<Long> userIds,
                                                               @RequestParam(defaultValue = "MANAGER") String type) {
        return ApiResponse.ok(staffService.byUsers(userIds, type));
    }

    // ================= 人员名单维护 =================

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.staff.manage')")
    public ApiResponse<List<Map<String, Object>>> staffList(@RequestParam(required = false) String type,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(staffService.list(type, keyword, status));
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('complaint.staff.manage')")
    public ApiResponse<Map<String, Object>> createStaff(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(staffService.create(body));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('complaint.staff.manage')")
    public ApiResponse<Map<String, Object>> updateStaff(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(staffService.update(id, body));
    }

    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('complaint.staff.manage')")
    public ApiResponse<Void> deleteStaff(@PathVariable Long id) {
        staffService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/options/dicts")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<List<Map<String, Object>>> dicts(@RequestParam(required = false) String type) {
        return ApiResponse.ok(optionService.dicts(type));
    }

    @GetMapping("/options/business-types")
    @PreAuthorize("hasAuthority('complaint.view') or hasAuthority('complaint.create')")
    public ApiResponse<Map<String, Object>> businessTypes() {
        return ApiResponse.ok(optionService.dictionaryOptions());
    }

    // ================= 附件 =================

    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('complaint.create') or hasAuthority('complaint.update')")
    public ApiResponse<List<Map<String, Object>>> upload(@PathVariable Long id,
                                                          @RequestParam(value = "photos", required = false) MultipartFile[] photos,
                                                          Authentication auth) {
        return ApiResponse.ok(service.addAttachments(id, photos, service.scopeUserId(auth.getAuthorities())));
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('complaint.view')")
    public ApiResponse<List<Map<String, Object>>> attachments(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(service.attachments(id, service.scopeUserId(auth.getAuthorities())));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/file")
    @PreAuthorize("hasAuthority('complaint.view')")
    public ResponseEntity<Resource> file(@PathVariable Long id, @PathVariable Long attachmentId, Authentication auth) {
        Resource resource = service.loadAttachment(id, attachmentId, service.scopeUserId(auth.getAuthorities()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(service.attachmentMimeType(attachmentId)))
                .body(resource);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('complaint.update') or hasAuthority('complaint.create')")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId, Authentication auth) {
        service.deleteAttachment(id, attachmentId, service.scopeUserId(auth.getAuthorities()));
        return ApiResponse.ok(null);
    }

    // ================= 报表 =================

    @GetMapping("/stats/overview")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<Map<String, Object>> overview(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.overview(query));
    }

    @GetMapping("/stats/distribution")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<List<Map<String, Object>>> distribution(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.distribution(query));
    }

    @GetMapping("/stats/trend")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<List<Map<String, Object>>> trend(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.trend(query));
    }

    /** 周期同环比：数量与金额，granularity=week/month/quarter/half。 */
    @GetMapping("/stats/period-compare")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<Map<String, Object>> periodCompare(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.periodCompare(query));
    }

    @GetMapping("/stats/cross")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<List<Map<String, Object>>> cross(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.cross(query));
    }

    @GetMapping("/stats/ranking")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<List<Map<String, Object>>> ranking(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.ranking(query));
    }

    @GetMapping("/stats/efficiency")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<Map<String, Object>> efficiency(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.efficiency(query));
    }

    @GetMapping("/stats/cost")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<Map<String, Object>> cost(ComplaintStatsQuery query) {
        return ApiResponse.ok(statsService.cost(query));
    }

    @GetMapping("/stats/filters")
    @PreAuthorize("hasAuthority('complaint.stats.view')")
    public ApiResponse<Map<String, Object>> filters() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dictionaries", optionService.dicts(null));
        result.putAll(optionService.dictionaryOptions());
        return ApiResponse.ok(result);
    }

    @GetMapping("/stats/export")
    @PreAuthorize("hasAuthority('complaint.export') or hasAuthority('complaint.stats.view')")
    public void exportReport(ComplaintStatsQuery query, HttpServletResponse response) throws IOException {
        excelService.exportReport(query, response);
    }

    // ================= 导入导出 =================

    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('complaint.import')")
    public void template(HttpServletResponse response) throws IOException {
        excelService.template(response);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('complaint.import')")
    public ApiResponse<?> importFile(@RequestParam("file") MultipartFile file, Authentication auth) {
        return ApiResponse.ok(excelService.importFile(file, service.currentUser(auth.getName())));
    }

    /**
     * 更新导入（费用修正）：按客诉编号更新公司承担费用/商户承担费用/无需承担费用。
     * 默认 dryRun=true，仅预演不写库；正式执行需显式传 dryRun=false。
     * allowClosed=true 允许修正已解决/已关闭单据，但仅费用三列，且需额外具备
     * complaint.import.force 权限（仅有 complaint.import 时返回 403）。
     */
    @PostMapping("/import/update")
    @PreAuthorize("hasAuthority('complaint.import')")
    public ApiResponse<?> updateImport(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
                                       @RequestParam(value = "allowClosed", defaultValue = "false") boolean allowClosed,
                                       Authentication auth) {
        if (allowClosed && !hasAuthority(auth, "complaint.import.force")) {
            throw BizException.forbidden("缺少「客诉导入-已关闭单据修正」权限（complaint.import.force），"
                    + "无法修正已解决/已关闭单据的费用");
        }
        return ApiResponse.ok(excelService.updateImport(file, service.currentUser(auth.getName()), dryRun, allowClosed));
    }

    private boolean hasAuthority(Authentication auth, String code) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> code.equals(a.getAuthority()));
    }

    @GetMapping("/import/errors/{token}")
    @PreAuthorize("hasAuthority('complaint.import')")
    public void errors(@PathVariable String token, HttpServletResponse response) throws IOException {
        excelService.exportErrors(token, response);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('complaint.export')")
    public void export(ComplaintQuery query, Authentication auth, HttpServletResponse response) throws IOException {
        excelService.export(query, service.scopeUserId(auth.getAuthorities()), response);
    }
}
