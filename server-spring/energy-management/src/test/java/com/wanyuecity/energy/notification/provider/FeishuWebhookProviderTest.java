package com.wanyuecity.energy.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeishuWebhookProviderTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void sendsInteractiveCardAndRemovesWeComMarkup() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":0,\"msg\":\"success\"}"));
            FeishuWebhookProvider provider = new FeishuWebhookProvider(json);
            String content = "### 📋 现场设备核验任务\n> <font color=\"info\">已有商户增加铺位</font>\n\n**商户**：测试商户\n\n[👉 点击进入现场核验](https://auto.apperload.com/verify/abc)";
            SendResult result = provider.send(server.url("/open-apis/bot/v2/hook/test").toString(), new NotificationMessage("FIELD_VERIFICATION_CREATED", 1L, content, "{}"));
            assertTrue(result.success());
            JsonNode payload = json.readTree(server.takeRequest().getBody().readUtf8());
            assertEquals("interactive", payload.path("msg_type").asText());
            assertEquals("📋 现场设备核验任务", payload.at("/card/header/title/content").asText());
            String serialized = payload.toString();
            assertFalse(serialized.contains("<font"));
            assertFalse(serialized.contains("###"));
            assertTrue(serialized.contains("https://auto.apperload.com/verify/abc"));
            assertTrue(serialized.contains("点击进入现场核验"));
        }
    }
}
