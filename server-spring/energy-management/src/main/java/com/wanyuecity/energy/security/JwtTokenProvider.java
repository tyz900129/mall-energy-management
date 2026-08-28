package com.wanyuecity.energy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Issues and validates HS256 JWTs. The secret comes from
 * {@code energy.jwt.secret} (env {@code JWT_SECRET}).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${energy.jwt.secret}")
    private String secret;

    @Value("${energy.jwt.expire-minutes:720}")
    private long expireMinutes;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // JJWT requires >= 32 bytes for HS256.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("energy.jwt.secret must be at least 32 bytes (HS256)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(Long userId, String username, List<String> roleCodes) {
        Instant now = Instant.now();
        Instant exp = now.plus(expireMinutes, ChronoUnit.MINUTES);
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("roles", roleCodes);
        return Jwts.builder()
                .subject(username)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // Log without echoing the token itself.
            log.debug("jwt parse failed: {}", ex.getMessage());
            return null;
        }
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}
