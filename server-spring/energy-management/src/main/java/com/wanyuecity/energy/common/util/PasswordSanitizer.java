package com.wanyuecity.energy.common.util;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback {@link MessageConverter} that masks the values of common sensitive
 * keys (password, token, secret, ciphertext, cookie, etc.) when the log
 * message is JSON-shaped. Non-JSON messages pass through untouched so we
 * don't risk mangling debug text.
 *
 * <p>Add to {@code logback-spring.xml} via:
 * <pre>
 *   &lt;conversionRule conversionWord="msg"
 *       converterClass="com.wanyuecity.energy.common.util.PasswordSanitizer"/&gt;
 *   &lt;encoder&gt;&lt;pattern&gt;%msg&lt;/pattern&gt;&lt;/encoder&gt;
 * </pre>
 */
@Slf4j
public class PasswordSanitizer extends MessageConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Key whitelist (case-insensitive) whose values must be replaced. */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "password_hash", "passwordhash",
            "passwd", "pwd",
            "token", "access_token", "refresh_token", "id_token", "jwt",
            "authorization",
            "secret", "client_secret",
            "ciphertext", "password_ciphertext", "password_iv",
            "cookie", "set-cookie", "session", "sessionid",
            "api_key", "apikey", "api-key",
            "credential", "credentials"
    );

    private static final String MASK = "***REDACTED***";

    /** Fallback regex for non-JSON messages: catches "key=value" pairs. */
    private static final Pattern KV_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|access_token|refresh_token|jwt|secret|ciphertext|cookie|session|api[_-]?key|credential)(['\"]?\\s*[:=]\\s*['\"]?)([^\\s,'\"}{&]+)");

    public PasswordSanitizer() {
        super();
    }

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null || msg.isEmpty()) {
            return msg;
        }
        String trimmed = msg.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                Object parsed = MAPPER.readValue(trimmed, new TypeReference<Object>() {});
                sanitize(parsed);
                return MAPPER.writeValueAsString(parsed);
            } catch (Exception ignore) {
                // fall through to regex sanitizer
            }
        }
        return sanitizePlain(trimmed);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sanitize(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Iterator<Map.Entry<?, ?>> it = ((Map) map).entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<?, ?> e = it.next();
                String key = e.getKey() == null ? "" : String.valueOf(e.getKey());
                if (SENSITIVE_KEYS.contains(key.toLowerCase())) {
                    ((Map) map).put(key, MASK);
                } else {
                    sanitize(e.getValue());
                }
            }
        } else if (node instanceof Iterable<?> iter) {
            for (Object child : iter) {
                sanitize(child);
            }
        }
    }

    private String sanitizePlain(String s) {
        Matcher m = KV_PATTERN.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(1) + m.group(2) + Matcher.quoteReplacement(MASK);
            m.appendReplacement(out, replacement);
        }
        m.appendTail(out);
        return out.toString();
    }
}

