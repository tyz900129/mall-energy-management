package com.wanyuecity.energy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路径级权限校验：未登录 401，已登录但缺少权限码 403；未配置映射的 /api/v1 路径默认拒绝。
 * V108 起不再注入「角色桥」授权，是否通过完全取决于用户的有效权限码集合。
 */
@Component
@RequiredArgsConstructor
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {
    private final RequestPermissionResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        RequestPermissionResolver.Rule rule = resolver.resolve(request);
        if (rule == null || rule.publicAccess()) return true;
        Set<String> required = rule.permissions();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return deny(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.CODE_UNAUTHORIZED,
                    "未登录或登录已过期，请重新登录");
        }
        if (required.isEmpty()) {
            return deny(response, HttpServletResponse.SC_FORBIDDEN, ApiResponse.CODE_FORBIDDEN,
                    "该接口未配置权限映射，已按默认拒绝拦截（请联系管理员补充权限码）");
        }
        Set<String> owned = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (required.stream().noneMatch(owned::contains)) {
            return deny(response, HttpServletResponse.SC_FORBIDDEN, ApiResponse.CODE_FORBIDDEN,
                    "无权执行此操作（缺少权限：" + String.join(" 或 ", required) + "）");
        }
        return true;
    }

    private boolean deny(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
        return false;
    }
}
