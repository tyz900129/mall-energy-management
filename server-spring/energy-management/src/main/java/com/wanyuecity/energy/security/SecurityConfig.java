package com.wanyuecity.energy.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.wanyuecity.energy.system.audit.AuditMutationFilter;
import com.wanyuecity.energy.system.migration.MigrationMaintenance;
import com.wanyuecity.energy.system.migration.MigrationMaintenanceFilter;
import com.wanyuecity.energy.system.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;

/**
 * Stateless JWT-based security. CSRF is disabled (no browser cookie session). CORS
 * allows the Vue dev server and same-origin.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private MigrationMaintenance migrationMaintenance = new MigrationMaintenance();
    @Value("${energy.cors.allowed-origins:http://localhost:5173}") private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints
                        .requestMatchers(
                                "/",
                                "/energy/**",
                                "/auth/login",
                                "/auth/register",
                                "/auth/device-login",
                                "/auth/ping",
                                "/auth/captcha",
                                "/actuator/health",
                                "/actuator/info",
                                "/files/**"
                                ,"/api/v1/verification/tasks/**"
                                ,"/api/v1/construction-public/**"
                                ,"/api/v1/inspections/public-qr/**"
                                ,"/api/v1/inspection-equipment/public-qr/**"
                                ,"/api/v1/access-public/**"
                                ,"/api/v1/operation-public/**"
                                ,"/api/v1/system/migration/exports/*/download"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MigrationMaintenanceFilter(migrationMaintenance, objectMapper), JwtAuthenticationFilter.class)
                .addFilterAfter(new AuditMutationFilter(auditLogService,objectMapper), MigrationMaintenanceFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s->!s.isBlank()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
