package com.wanyuecity.energy.platform.adapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeoEnergyPlatformAdapterTest {

    private MockWebServer server;
    private ExecutorService executor;
    private LeoEnergyPlatformAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        executor = Executors.newFixedThreadPool(2);
        adapter = new LeoEnergyPlatformAdapter(3000, executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        server.shutdown();
    }

    @Test
    void usesCurrentLoginEndpointAndReturnsToken() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"message\":\"ok\",\"data\":{\"token\":\"TOKEN-123\"}}"));

        Session session = adapter.login(server.url("/").toString(), "user", "secret");

        assertEquals("TOKEN-123", session.token());
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/auth/app/login", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"username\":\"user\""));
        assertTrue(body.contains("\"password\":\"secret\""));
    }

    @Test
    void sendsTokenHeaderOnAuthenticatedRequests() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"message\":\"ok\",\"data\":{\"token\":\"TOKEN-456\"}}"));
        server.enqueue(json("{\"code\":\"200000\",\"message\":\"ok\",\"data\":{\"total\":0,\"list\":[]}}"));

        Session session = adapter.login(server.url("/").toString(), "user", "secret");
        adapter.fetchAccounts(server.url("/").toString(), session.token(), "4801");

        assertNotNull(server.takeRequest());
        RecordedRequest request = server.takeRequest();
        assertEquals("TOKEN-456", request.getHeader("token"));
        assertTrue(request.getPath().startsWith("/api/account/page?"));
    }

    @Test
    void relationUsesDeviceIdInsteadOfRelationRowId() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"total\":1,\"list\":[{\"id\":\"ACCOUNT-ID\",\"accountNumber\":\"ACCOUNT-1\"}]}}"));
        server.enqueue(json("{\"code\":\"200000\",\"data\":[{\"id\":\"RELATION-ID\",\"deviceId\":\"DEVICE-1\"}]}"));

        var relations = adapter.fetchAccountDeviceRelations(
                server.url("/").toString(), "TOKEN", "4801");

        assertEquals(1, relations.size());
        assertEquals("DEVICE-1", relations.get(0).getPlatformDeviceId());
    }

    @Test
    void enrichesDeviceSummaryFromDetailEndpoint() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"total\":1,\"list\":[{\"deviceId\":\"DEVICE-1\",\"deviceName\":\"水表\",\"gatewayId\":\"GW-1\",\"deviceSystemName\":\"给水系统\"}]}}"));
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"id\":\"DEVICE-1\",\"name\":\"水表\",\"number\":\"SN-001\",\"commAddress\":\"ADDR-001\",\"gatewayId\":\"GW-1\",\"gatewayName\":\"一层网关\",\"channelId\":\"CH-1\",\"channelName\":\"通道1\",\"productName\":\"智能水表\",\"deviceSystemName\":\"给水系统\"}}"));

        var devices = adapter.fetchDevices(server.url("/").toString(), "TOKEN", "4801");
        assertEquals(1, devices.size());
        assertEquals("ADDR-001", devices.get(0).getCommAddress());
        assertEquals("SN-001", devices.get(0).getSerialNumber());
        assertEquals("一层网关", devices.get(0).getGatewayName());
        assertEquals("通道1", devices.get(0).getChannelName());
        RecordedRequest detail = server.takeRequest();
        detail = server.takeRequest();
        assertTrue(detail.getPath().startsWith("/api/devices/show/DEVICE-1"));
    }

    private MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

