package com.wanyuecity.energy.system.controller;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 根路径响应：让浏览器访问 {@code /} 时直接拿到 200 + API 自描述信息，
 * 而不是被 Spring Security 拒成 401。新前端应基于此返回的 endpoints 列表拼装请求。
 *
 * <p>同时提供 {@code /energy} → {@code /} 的兼容重定向（旧的 context-path 路径），
 * 避免在 CF Tunnel 上把 hostname 直接绑到应用根后老链接 404。
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ApiResponse<Map<String, Object>> root() {
        return ApiResponse.ok(Map.of(
                "name", "mall-energy-management",
                "version", "1.0.0",
                "contextPath", "/",
                "endpoints", Map.of(
                        "login",     "POST /auth/login   | GET /auth/login?username=&password=",
                        "me",        "GET  /auth/me",
                        "ping",      "GET  /auth/ping",
                        "health",    "GET  /actuator/health",
                        "platform",  "GET  /api/v1/platform/config"
                )
        ));
    }

    /**
     * Backward-compat: 旧 context-path 链接 {@code /energy/...} 全部 302 到根。
     * 仅处理 /energy 开头且不是 /energy/api 等内部路径的请求。
     */
    @GetMapping("/energy/**")
    public void redirectLegacy(HttpServletResponse response) throws IOException {
        String path = "/"; // 简化：/energy 和 /energy/xxx 全部回到根，由前端决定
        response.sendRedirect(path);
    }
}

