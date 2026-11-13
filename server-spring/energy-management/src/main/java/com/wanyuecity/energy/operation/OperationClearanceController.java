package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/operations/clearances")
public class OperationClearanceController {
    private final OperationClearanceService service;

    public OperationClearanceController(OperationClearanceService service) {
        this.service = service;
    }

    @GetMapping("/period")
    public ApiResponse<Map<String, Object>> period() {
        return ApiResponse.ok(service.currentPeriod());
    }

    @GetMapping("/schedule")
    public ApiResponse<Map<String, Object>> schedule() {
        return ApiResponse.ok(service.settings());
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(service.settings());
    }

    @PutMapping("/settings")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.updateSettings(body, authentication.getName()));
    }

    @PostMapping("/stall/{token}")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String token, @RequestBody Map<String, Object> body,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.submit(token, body, authentication.getName()));
    }

    @GetMapping("/stall/{token}/today")
    public ApiResponse<Map<String, Object>> today(@PathVariable String token, Authentication authentication) {
        boolean unrestricted = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_SUPER_ADMIN") || authority.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
        return ApiResponse.ok(service.todayByToken(token, authentication.getName(), unrestricted));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> page(@RequestParam(defaultValue = "") String keyword,
                                                  @RequestParam(defaultValue = "") String periodDate,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  Authentication authentication) {
        boolean all = allScope(authentication);
        return ApiResponse.ok(service.page(authentication.getName(), all, keyword, periodDate, pageNum, pageSize));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(defaultValue = "") String periodDate,
                                         Authentication authentication) {
        byte[] content = service.exportCsv(authentication.getName(), allScope(authentication), keyword, periodDate);
        String filename = URLEncoder.encode("商户日清记录_" + LocalDate.now() + ".csv", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    private boolean allScope(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "operation.clearance.view.all".equals(authority.getAuthority()));
    }
}
