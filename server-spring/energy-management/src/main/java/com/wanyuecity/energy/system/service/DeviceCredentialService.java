package com.wanyuecity.energy.system.service;

import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DeviceCredentialService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public String issue(Long userId, String fingerprint, String deviceName) {
        if (fingerprint == null || fingerprint.isBlank()) return null;
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM user_device_credential WHERE user_id=? AND fingerprint_hash=?", userId, hash(fingerprint));
        jdbc.update("INSERT INTO user_device_credential(user_id,token_hash,fingerprint_hash,device_name,expires_at) VALUES(?,?,?,?,?)",
                userId, hash(token), hash(fingerprint), trim(deviceName,120), LocalDateTime.now().plusDays(30));
        return token;
    }

    @Transactional
    public Long authenticate(String token, String fingerprint) {
        var ids = jdbc.query("SELECT user_id FROM user_device_credential WHERE token_hash=? AND fingerprint_hash=? AND expires_at>NOW() LIMIT 1",
                (rs,n)->rs.getLong(1), hash(token), hash(fingerprint));
        if (ids.isEmpty()) throw BizException.unauthorized("设备自动登录已失效，请使用密码登录");
        jdbc.update("UPDATE user_device_credential SET last_used_at=NOW() WHERE token_hash=?", hash(token));
        return ids.get(0);
    }

    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
    private String trim(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
}

