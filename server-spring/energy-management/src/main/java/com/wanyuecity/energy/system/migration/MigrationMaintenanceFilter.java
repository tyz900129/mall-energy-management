package com.wanyuecity.energy.system.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class MigrationMaintenanceFilter extends OncePerRequestFilter {
    private final MigrationMaintenance maintenance;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !maintenance.isActive()
                || path.matches(".*/api/v1/system/migration/jobs/[^/]+$")
                || path.endsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "5");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(ApiResponse.CODE_FAILURE, "系统正在导入数据，请稍后重试"));
    }
}
