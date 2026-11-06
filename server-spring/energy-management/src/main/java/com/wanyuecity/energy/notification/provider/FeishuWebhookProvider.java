package com.wanyuecity.energy.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FeishuWebhookProvider implements NotificationProvider {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)]+)\\)");
    private static final Pattern IMAGE_LINK = Pattern.compile("!\\[[^\\]]*]\\(https?://[^)]+\\)");
    private static final Pattern FONT_TAG = Pattern.compile("</?font(?:\\s+[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10)).writeTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    public FeishuWebhookProvider(ObjectMapper json) { this.json = json; }
    public String provider() { return "FEISHU"; }

    public SendResult send(String url, NotificationMessage msg) {
        try {
            String body = json.writeValueAsString(buildPayload(msg));
            Request req = new Request.Builder().url(url).post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8"))).build();
            try (Response r = http.newCall(req).execute()) {
                String text = r.body() == null ? "" : r.body().string();
                JsonNode n = json.readTree(text);
                boolean ok = r.isSuccessful() && (n.path("code").asInt(-1) == 0 || n.path("StatusCode").asInt(-1) == 0);
                return new SendResult(ok, r.code(), limit(text, 2000), ok ? null : "provider rejected request");
            }
        } catch (Exception e) {
            return new SendResult(false, null, null, e.getClass().getSimpleName() + ": " + limit(e.getMessage(), 300));
        }
    }

    Map<String, Object> buildPayload(NotificationMessage msg) {
        String markdown = IMAGE_LINK.matcher(FONT_TAG.matcher(msg.content() == null ? "" : msg.content()).replaceAll("").trim()).replaceAll("").trim();
        Matcher link = MARKDOWN_LINK.matcher(markdown);
        List<Map<String, String>> buttons = new ArrayList<>();
        while (link.find()) {
            String text = link.group(1).replaceFirst("^[^\\p{L}\\p{N}]+\\s*", "").trim();
            buttons.add(Map.of("text", text, "url", link.group(2)));
        }
        if (!buttons.isEmpty()) markdown = MARKDOWN_LINK.matcher(markdown).replaceAll("").trim();
        // 飞书自定义机器人不能内嵌外链图片，现场照片降级为可点开原图的行内链接（不占用底部按钮位）
        if (msg.imageUrls() != null && !msg.imageUrls().isEmpty()) {
            StringBuilder photos = new StringBuilder();
            int index = 1;
            for (String imageUrl : msg.imageUrls()) {
                if (index > 6) break;
                photos.append("[").append(index++).append("](").append(imageUrl).append(") ");
            }
            markdown = markdown + "\n**现场照片**：" + photos.toString().trim();
        }
        String title = extractTitle(markdown);
        markdown = markdown.replaceFirst("(?m)^#{1,6}\\s+.*(?:\\R|$)", "").trim();

        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", markdown)));
        if (!buttons.isEmpty()) {
            List<Map<String, Object>> actions = new ArrayList<>();
            for (Map<String, String> b : buttons) {
                Map<String, Object> button = new LinkedHashMap<>();
                button.put("tag", "button");
                button.put("text", Map.of("tag", "plain_text", "content", b.get("text").isBlank() ? "查看详情" : b.get("text")));
                button.put("type", "primary");
                button.put("url", b.get("url"));
                actions.add(button);
            }
            elements.add(Map.of("tag", "action", "actions", actions));
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true, "enable_forward", true));
        card.put("header", Map.of("template", headerTemplate(msg.eventType()), "title", Map.of("tag", "plain_text", "content", title)));
        card.put("elements", elements);
        return Map.of("msg_type", "interactive", "card", card);
    }

    private String extractTitle(String markdown) {
        Matcher m = Pattern.compile("(?m)^#{1,6}\\s+(.+)$").matcher(markdown);
        return m.find() ? m.group(1).trim() : "万悦城后勤管理系统通知";
    }
    private String headerTemplate(String eventType) {
        if (eventType != null && (eventType.contains("OVERDUE") || eventType.contains("EXCEPTION") || eventType.contains("ARREARS"))) return "red";
        if (eventType != null && eventType.contains("COMPLETED")) return "green";
        return "blue";
    }
    private String limit(String s, int n) { if (s == null) return null; return s.length() <= n ? s : s.substring(0, n); }
}
