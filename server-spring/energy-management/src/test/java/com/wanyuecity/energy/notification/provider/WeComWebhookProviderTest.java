package com.wanyuecity.energy.notification.provider;
import com.fasterxml.jackson.databind.ObjectMapper;import okhttp3.mockwebserver.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class WeComWebhookProviderTest {private MockWebServer server;private WeComWebhookProvider provider;@BeforeEach void setUp()throws Exception{server=new MockWebServer();server.start();provider=new WeComWebhookProvider(new ObjectMapper());}@AfterEach void close()throws Exception{server.shutdown();}
 @Test void acceptsSuccessfulWeComResponse(){server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"errcode\":0,\"errmsg\":\"ok\"}"));SendResult r=provider.send(server.url("/cgi-bin/webhook/send?key=test").toString(),new NotificationMessage("TEST",1L,"hello","{}"));assertTrue(r.success());assertEquals(200,r.httpStatus());}
 @Test void recordsProviderRejection(){server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"errcode\":93000,\"errmsg\":\"invalid webhook\"}"));SendResult r=provider.send(server.url("/cgi-bin/webhook/send?key=test").toString(),new NotificationMessage("TEST",1L,"hello","{}"));assertFalse(r.success());assertNotNull(r.error());}
}
