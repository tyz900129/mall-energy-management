package com.wanyuecity.energy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {
    private final RequestPermissionResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        RequestPermissionResolver.Rule rule = resolver.resolve(request);
        if (rule == null) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !has(authentication, rule.permissionCode())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ApiResponse.CODE_FORBIDDEN,
                    "无权执行此操作（缺少权限：" + rule.permissionCode() + "）"));
            return false;
        }
        boolean accepted = rule.acceptedRoles().stream().anyMatch(role -> has(authentication, "ROLE_" + role));
        if (!accepted) {
            List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rule.bridgeRole()));
            authorities.add(new SimpleGrantedAuthority("PERMISSION_BRIDGE:" + rule.permissionCode()));
            UsernamePasswordAuthenticationToken bridged = new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(), authentication.getCredentials(), authorities);
            bridged.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(bridged);
        }
        return true;
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }
}
