package com.wanyuecity.energy.patrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patrol")
public class PatrolMobileController {
    private final PatrolService service;
    private final ObjectMapper objectMapper;

    public PatrolMobileController(PatrolService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/entry")
    public ApiResponse<Map<String, Object>> entry(@RequestParam String token) {
        return ApiResponse.ok(service.entry(token));
    }

    @GetMapping("/points/{id}/form")
    public ApiResponse<Map<String, Object>> form(@PathVariable Long id, @RequestParam String token, Authentication authentication) {
        return ApiResponse.ok(service.pointForm(id, token, authentication == null ? null : authentication.getName()));
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> submit(@RequestParam String token,
                                                    @RequestParam Long pointId,
                                                    @RequestParam String result,
                                                    @RequestParam(required = false) String submitterName,
                                                    @RequestParam(required = false) String note,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String urgency,
                                                    @RequestParam(required = false) Long departmentId,
                                                    @RequestParam(required = false) String checkResults,
                                                    @RequestParam(value = "photos", required = false) MultipartFile[] photos,
                                                    Authentication authentication,
                                                    HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pointId", pointId);
        body.put("result", result);
        body.put("submitterName", submitterName);
        body.put("note", note);
        body.put("category", category);
        body.put("urgency", urgency);
        body.put("departmentId", departmentId);
        if (checkResults != null && !checkResults.isBlank()) {
            try { body.put("checkResults", objectMapper.readValue(checkResults, Object.class)); }
            catch (Exception ignored) { }
        }
        return ApiResponse.ok(service.submit(token, authentication == null ? null : authentication.getName(), body, photos, clientIp(request)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
