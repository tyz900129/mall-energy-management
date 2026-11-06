package com.wanyuecity.energy.patrol;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patrol")
public class PatrolAdminController {
    private final PatrolService service;

    public PatrolAdminController(PatrolService service) { this.service = service; }

    @GetMapping("/points")
    public ApiResponse<List<Map<String, Object>>> points(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(service.points(includeDisabled));
    }

    @PostMapping("/points")
    public ApiResponse<Map<String, Object>> createPoint(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.savePoint(null, body, authentication.getName()));
    }

    @PutMapping("/points/{id}")
    public ApiResponse<Map<String, Object>> updatePoint(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.savePoint(id, body, authentication.getName()));
    }

    @DeleteMapping("/points/{id}")
    public ApiResponse<Void> deletePoint(@PathVariable Long id) {
        service.deletePoint(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/departments")
    public ApiResponse<List<Map<String, Object>>> departments() {
        return ApiResponse.ok(service.departments());
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<Map<String, Object>> updateDepartment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateDepartment(id, body));
    }

    @PostMapping("/departments/{id}/token")
    public ApiResponse<Map<String, Object>> resetDepartmentToken(@PathVariable Long id) {
        return ApiResponse.ok(service.resetDepartmentToken(id));
    }

    @GetMapping("/webhooks")
    public ApiResponse<List<Map<String, Object>>> webhooks() {
        return ApiResponse.ok(service.webhooks());
    }

    @GetMapping("/duties")
    public ApiResponse<Map<String, Object>> duties(@RequestParam(required = false) String month) {
        return ApiResponse.ok(service.duties(month));
    }

    @PostMapping("/duties")
    public ApiResponse<Void> saveDuty(@RequestBody Map<String, Object> body) {
        service.saveDuty(body);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/duties/{id}")
    public ApiResponse<Void> deleteDuty(@PathVariable Long id) {
        service.deleteDuty(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(service.categoryList());
    }

    @PostMapping("/categories")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCategory(null, body));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCategory(id, body));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/point-types")
    public ApiResponse<List<Map<String, Object>>> pointTypes() {
        return ApiResponse.ok(service.pointTypeList());
    }

    @PostMapping("/point-types")
    public ApiResponse<Map<String, Object>> createPointType(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.savePointType(null, body));
    }

    @PutMapping("/point-types/{id}")
    public ApiResponse<Map<String, Object>> updatePointType(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.savePointType(id, body));
    }

    @DeleteMapping("/point-types/{id}")
    public ApiResponse<Void> deletePointType(@PathVariable Long id) {
        service.deletePointType(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/rounds/today")
    public ApiResponse<Map<String, Object>> todayRound() {
        return ApiResponse.ok(service.todayRound());
    }

    @PostMapping("/rounds/push")
    public ApiResponse<Map<String, Object>> push() {
        return ApiResponse.ok(service.pushToday());
    }

    @GetMapping("/submissions")
    public ApiResponse<Map<String, Object>> submissions(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(service.submissionPage(query));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/submissions/{id}")
    public ApiResponse<Void> deleteSubmission(@PathVariable Long id) {
        service.deleteSubmission(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/issues")
    public ApiResponse<Map<String, Object>> issues(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(service.issuePage(query));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/issues/{id}")
    public ApiResponse<Void> deleteIssue(@PathVariable Long id) {
        service.deleteIssue(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> issue(@PathVariable Long id) {
        return ApiResponse.ok(service.issueDetail(id));
    }

    @PostMapping("/issues/{id}/assign")
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        Long departmentId = body.get("departmentId") instanceof Number number ? number.longValue() : null;
        return ApiResponse.ok(service.assign(id, departmentId, body.get("note") == null ? null : body.get("note").toString(), authentication.getName()));
    }

    @PostMapping("/issues/{id}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        boolean approved = Boolean.TRUE.equals(body.get("approved")) || "true".equalsIgnoreCase(String.valueOf(body.get("approved")));
        return ApiResponse.ok(service.review(id, approved, body.get("note") == null ? null : body.get("note").toString(), authentication.getName()));
    }

    @GetMapping("/stats/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.overview());
    }
}
