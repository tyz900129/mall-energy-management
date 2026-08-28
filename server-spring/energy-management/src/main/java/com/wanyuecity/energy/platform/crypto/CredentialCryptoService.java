package com.wanyuecity.energy.platform.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for source-platform credentials.
 *
 * <ul>
 *   <li>Key derivation: {@code SHA-256(envValue)} → first 32 bytes.</li>
 *   <li>IV: 12 random bytes per encryption, stored as Base64 alongside the ciphertext.</li>
 *   <li>Authenticated tag: 128 bits, appended to ciphertext by JCE.</li>
 * </ul>
 *
 * The {@code envName} (e.g. {@code PLATFORM_CREDENTIAL_SECRET}) is configurable via
 * {@code energy.platform.credential-secret-env}; the actual secret is read from the
 * process environment. If the env var is missing the service refuses to start.
 */
@Slf4j(topic = "security")
@Service
public class CredentialCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String envName;
    private final SecureRandom random = new SecureRandom();

    private SecretKey key;
    private boolean available = false;

    public CredentialCryptoService(@Value("${energy.platform.credential-secret-env}") String envName) {
        this.envName = envName;
    }

    @PostConstruct
    void init() {
        String secret = System.getenv(envName);
        if (secret == null || secret.isBlank()) {
            log.warn("env {} is not set; credential encryption is unavailable. Set it before saving platform config.", envName);
            return;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
            this.available = true;
            log.info("credential crypto initialized (keySource=env:{})", envName);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to init credential crypto", ex);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public Encrypted encrypt(String plaintext) {
        ensureAvailable();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(Base64.getEncoder().encodeToString(cipherBytes), Base64.getEncoder().encodeToString(iv));
        } catch (Exception ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    public String decrypt(String ciphertextBase64, String ivBase64) {
        ensureAvailable();
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] cipherBytes = Base64.getDecoder().decode(ciphertextBase64);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Do not echo the ciphertext.
            throw new IllegalStateException("Decryption failed (bad key or tampered ciphertext)", ex);
        }
    }

    private void ensureAvailable() {
        if (!available) {
            throw new IllegalStateException("Credential crypto unavailable: env " + envName + " is not set");
        }
    }

    /** Pair of base64-encoded ciphertext + IV. */
    public record Encrypted(String ciphertext, String iv) {
    }
}
