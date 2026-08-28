package com.wanyuecity.energy.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts the JWT from the {@code Authorization} header, validates it, and populates
 * the {@link SecurityContextHolder}. The token itself is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            Claims claims = tokenProvider.parse(token);
            if (claims != null) {
                try {
                    String username = claims.getSubject();
                    Object rolesObj = claims.get("roles");
                    Collection<SimpleGrantedAuthority> authorities = List.of();
                    if (rolesObj instanceof List<?> raw) {
                        authorities = raw.stream()
                                .map(String::valueOf)
                                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                                .collect(Collectors.toList());
                    }
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ex) {
                    // Don't leak details; just clear the context.
                    log.debug("auth setup failed: {}", ex.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = "Bearer ";
        if (header.length() <= prefix.length()) {
            return null;
        }
        if (!header.startsWith(prefix)) {
            return null;
        }
        return header.substring(prefix.length()).trim();
    }
}

