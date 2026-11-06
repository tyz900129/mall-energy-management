package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops-patrol")
public class OpsPatrolAdminController {
    private final OpsPatrolService service;

    public OpsPatrolAdminController(OpsPatrolService service) { this.service = service; }

    // ---------------------------------------------------------------- 处室与群
    @GetMapping("/depts")
    public ApiResponse<List<Map<String, Object>>> depts() { return ApiResponse.ok(service.depts()); }

    @PutMapping("/depts/{id}")
    public ApiResponse<Map<String, Object>> saveDept(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveDept(id, body));
    }

    @GetMapping("/webhooks")
    public ApiResponse<List<Map<String, Object>>> webhooks() { return ApiResponse.ok(service.webhooks()); }

    @GetMapping("/floor-options")
    public ApiResponse<List<Map<String, Object>>> floorOptions() { return ApiResponse.ok(service.floorOptions()); }

    // ---------------------------------------------------------------- 时段
    @GetMapping("/slots")
    public ApiResponse<List<Map<String, Object>>> slots() { return ApiResponse.ok(service.slots()); }

    @PutMapping("/slots/{id}")
    public ApiResponse<Map<String, Object>> updateSlot(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateSlot(id, body));
    }

    // ---------------------------------------------------------------- 检查项
    @GetMapping("/check-items")
    public ApiResponse<List<Map<String, Object>>> checkItems(@RequestParam(required = false) String formType) {
        return ApiResponse.ok(service.checkItems(formType));
    }

    @PostMapping("/check-items")
    public ApiResponse<Map<String, Object>> createItem(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCheckItem(null, body));
    }

    @PutMapping("/check-items/{id}")
    public ApiResponse<Map<String, Object>> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveCheckItem(id, body));
    }

    @DeleteMapping("/check-items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        service.deleteCheckItem(id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 排班
    @GetMapping("/duties")
    public ApiResponse<Map<String, Object>> duties(@RequestParam(required = false) String month) {
        return ApiResponse.ok(service.duties(month));
    }

    @PostMapping("/duties")
    public ApiResponse<Map<String, Object>> saveDuty(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.saveDuty(body));
    }

    @DeleteMapping("/duties/{id}")
    public ApiResponse<Void> deleteDuty(@PathVariable Long id) {
        service.deleteDuty(id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 店铺联想
    @GetMapping("/stores")
    public ApiResponse<List<Map<String, Object>>> stores(@RequestParam String keyword,
                                                          @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(service.stores(keyword, deptId));
    }

    // ---------------------------------------------------------------- 任务看板
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> taskBoard(@RequestParam(required = false) String date) {
        return ApiResponse.ok(service.taskBoard(date));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.taskDetail(id));
    }

    @PostMapping("/tasks/{id}/push")
    public ApiResponse<Void> pushTask(@PathVariable Long id) {
        service.pushTaskNow(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{id}/exempt")
    public ApiResponse<Void> exemptTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.exemptTask(id, body.get("reason") == null ? null : body.get("reason").toString());
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        service.deleteTask(id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 问题
    @GetMapping("/issues")
    public ApiResponse<Map<String, Object>> issues(@RequestParam(required = false) String deptId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String floor,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.issues(deptId, status, floor, keyword, page, size));
    }

    @GetMapping("/issues/{id}")
    public ApiResponse<Map<String, Object>> issueDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.issueDetail(id));
    }

    @PutMapping("/issues/{id}/assign")
    public ApiResponse<Map<String, Object>> assignIssue(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.assignIssue(id, body, authentication.getName()));
    }

    @PostMapping(value = "/issues/{id}/rectify", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> rectifyIssue(@PathVariable Long id,
                                                          @RequestParam(required = false) String note,
                                                          @RequestParam(value = "photos", required = false) org.springframework.web.multipart.MultipartFile[] photos,
                                                          Authentication authentication) {
        return ApiResponse.ok(service.rectifyIssue(id, note, photos, authentication.getName()));
    }

    @PostMapping(value = "/issues/{id}/rectify", consumes = "application/json")
    public ApiResponse<Map<String, Object>> rectifyIssueJson(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.rectifyIssue(id, body.get("note") == null ? null : body.get("note").toString(), null, authentication.getName()));
    }

    @PostMapping("/issues/{id}/review")
    public ApiResponse<Map<String, Object>> reviewIssue(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        boolean pass = Boolean.TRUE.equals(body.get("pass")) || "true".equals(String.valueOf(body.get("pass")));
        return ApiResponse.ok(service.reviewIssue(id, pass, body.get("note") == null ? null : body.get("note").toString(), authentication.getName()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/issues/{id}")
    public ApiResponse<Void> deleteIssue(@PathVariable Long id) {
        service.deleteIssue(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/issues/{id}/attachments/{attachmentId}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> issueAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        org.springframework.core.io.Resource resource = service.issueAttachment(id, attachmentId);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(service.attachmentMime(attachmentId)))
                .body(resource);
    }

    // ---------------------------------------------------------------- 交接记录
    @GetMapping("/handovers")
    public ApiResponse<List<Map<String, Object>>> handovers(@RequestParam(required = false) String date) {
        return ApiResponse.ok(service.handovers(date));
    }

    // ---------------------------------------------------------------- 报表
    @GetMapping("/reports/daily")
    public ApiResponse<Map<String, Object>> dailyReport(@RequestParam(required = false) String date) {
        return ApiResponse.ok(service.dailyReport(date));
    }
}
