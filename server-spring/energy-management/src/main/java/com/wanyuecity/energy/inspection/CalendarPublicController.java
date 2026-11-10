package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 日历任务免登录回执入口。
 * token 只定位单条任务，不提供列表/枚举；所有响应经 CalendarPublicService 脱敏。
 */
@RestController
@RequestMapping("/api/v1/calendar-public")
@RequiredArgsConstructor
public class CalendarPublicController {
    private final CalendarPublicService service;

    @GetMapping("/tasks/{token}")
    public ApiResponse<Map<String,Object>> task(@PathVariable String token, HttpServletRequest request) {
        return ApiResponse.ok(service.detail(token, clientIp(request)));
    }

    @PostMapping("/tasks/{token}/photos")
    public ApiResponse<Map<String,Object>> upload(@PathVariable String token,
                                                  @RequestPart("file") MultipartFile file,
                                                  @RequestParam(required = false) String name,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.upload(token, file, name, clientIp(request)));
    }

    @GetMapping("/tasks/{token}/photos/{photoId}")
    public ResponseEntity<?> photo(@PathVariable String token, @PathVariable Long photoId, HttpServletRequest request) {
        var content = service.photo(token, photoId, clientIp(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.fileName().replace("\"", "") + "\"")
                .body(content.resource());
    }

    @PostMapping("/tasks/{token}/submit")
    public ApiResponse<Map<String,Object>> submit(@PathVariable String token,
                                                  @RequestBody Map<String,Object> body,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(service.submit(token, body, clientIp(request), request.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value == null || value.isBlank()) value = request.getRemoteAddr();
        String ip = value.split(",")[0].trim();
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
