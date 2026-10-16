package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/clearances")
public class OperationClearanceController {
    private final OperationClearanceService service;

    public OperationClearanceController(OperationClearanceService service) {
        this.service = service;
    }

    @GetMapping("/period")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Map<String, Object>> period() {
        return ApiResponse.ok(service.currentPeriod());
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER')")
    public ApiResponse<Map<String, Object>> schedule() {
        return ApiResponse.ok(service.settings());
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(service.settings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.updateSettings(body, authentication.getName()));
    }

    @PostMapping("/stall/{token}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','VIEWER')")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String token, @RequestBody Map<String, Object> body,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.submit(token, body, authentication.getName()));
    }

    @GetMapping("/stall/{token}/today")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<Map<String, Object>> today(@PathVariable String token, Authentication authentication) {
        boolean unrestricted = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_SUPER_ADMIN") || authority.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
        return ApiResponse.ok(service.todayByToken(token, authentication.getName(), unrestricted));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER')")
    public ApiResponse<Map<String, Object>> page(@RequestParam(defaultValue = "") String keyword,
                                                  @RequestParam(defaultValue = "") String periodDate,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  Authentication authentication) {
        boolean all = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                        authority.getAuthority().equals("ROLE_SYSTEM_ADMIN") ||
                        authority.getAuthority().equals("ROLE_DEVICE_VERIFIER"));
        return ApiResponse.ok(service.page(authentication.getName(), all, keyword, periodDate, pageNum, pageSize));
    }
}
