package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/property-inspections")
public class OperationPropertyInspectionController {
    private final OperationPropertyInspectionService service;

    public OperationPropertyInspectionController(OperationPropertyInspectionService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER')")
    public ApiResponse<List<Map<String, Object>>> records(@RequestParam(defaultValue = "") String periodDate) {
        return ApiResponse.ok(service.records(periodDate));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<List<Map<String, Object>>> templates() { return ApiResponse.ok(service.templates()); }

    @GetMapping("/templates/stall/{token}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable String token) { return ApiResponse.ok(service.resolve(token)); }

    @GetMapping("/form/stall/{token}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<Map<String,Object>> form(@PathVariable String token, Authentication authentication) {
        return ApiResponse.ok(service.form(token, authentication.getName()));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.createTemplate(body, authentication.getName()));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateTemplate(id, body));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteTemplate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/stall/{token}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String token, @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.submit(token, body, authentication.getName()));
    }
}
