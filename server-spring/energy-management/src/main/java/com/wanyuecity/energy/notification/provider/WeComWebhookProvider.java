package com.wanyuecity.energy.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class WeComWebhookProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(WeComWebhookProvider.class);
    /** 企业微信图片消息：base64 编码前不超过 2MB，且只支持 JPG/PNG。 */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).writeTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json;

    public WeComWebhookProvider(ObjectMapper json) { this.json = json; }

    public String provider() { return "WECOM"; }

    public SendResult send(String url, NotificationMessage msg) {
        SendResult result = post(url, Map.of("msgtype", "markdown", "markdown", Map.of("content", msg.content() == null ? "" : msg.content())));
        // 企业微信 markdown 不支持内嵌图片，现场照片改为独立的图片消息补发
        List<String> images = msg.imageUrls();
        if (images != null) {
            for (String imageUrl : images) {
                try {
                    sendImage(url, imageUrl);
                } catch (Exception ex) {
                    // 单张图片失败不影响主消息
                    log.warn("wecom image skipped: {} ({})", imageUrl, ex.getMessage());
                }
            }
        }
        return result;
    }

    private void sendImage(String url, String imageUrl) throws Exception {
        byte[] bytes = download(imageUrl);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            log.warn("wecom image skipped: {} (empty or larger than 2MB)", imageUrl);
            return;
        }
        SendResult result = post(url, Map.of("msgtype", "image", "image", Map.of(
                "base64", Base64.getEncoder().encodeToString(bytes),
                "md5", md5Hex(bytes))));
        if (result.success()) log.info("wecom image pushed: {} ({} bytes)", imageUrl, bytes.length);
        else log.warn("wecom image failed: {} ({})", imageUrl, result.response());
    }

    private byte[] download(String imageUrl) throws Exception {
        Request req = new Request.Builder().url(imageUrl).get().build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) return null;
            return r.body().bytes();
        }
    }

    private SendResult post(String url, Map<String, Object> payload) {
        try {
            String body = json.writeValueAsString(payload);
            Request req = new Request.Builder().url(url).post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8"))).build();
            try (Response r = http.newCall(req).execute()) {
                String text = r.body() == null ? "" : r.body().string();
                boolean ok = r.isSuccessful() && text.contains("\"errcode\":0");
                return new SendResult(ok, r.code(), limit(text, 2000), ok ? null : "provider rejected request");
            }
        } catch (Exception e) {
            return new SendResult(false, null, null, e.getClass().getSimpleName() + ": " + limit(e.getMessage(), 300));
        }
    }

    private String md5Hex(byte[] bytes) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        StringBuilder sb = new StringBuilder();
        for (byte b : md5.digest(bytes)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String limit(String s, int n) { if (s == null) return null; return s.length() <= n ? s : s.substring(0, n); }
}
