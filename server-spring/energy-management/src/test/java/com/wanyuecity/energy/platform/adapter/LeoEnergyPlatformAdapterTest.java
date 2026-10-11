package com.wanyuecity.energy.platform.adapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDate;
import java.util.List;
import com.wanyuecity.energy.platform.dto.PlatformDevice;

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
    void enrichesMissingAccountPhoneFromDetailEndpoint() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"total\":1,\"list\":[{\"id\":\"ACCOUNT-ID\",\"accountNumber\":\"3066-000001\",\"userName\":\"商户\",\"mobilePhone\":\"\"}]}}"));
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"id\":\"ACCOUNT-ID\",\"accountNumber\":\"3066-000001\",\"userName\":\"商户\",\"mobilePhone\":\"13800138000\"}}"));

        var accounts = adapter.fetchAccounts(server.url("/").toString(), "TOKEN", "4801");

        assertEquals(1, accounts.size());
        assertEquals("13800138000", accounts.get(0).getMobilePhone());
        server.takeRequest();
        RecordedRequest detail = server.takeRequest();
        assertTrue(detail.getPath().startsWith("/api/account/?id=ACCOUNT-ID"));
    }

    @Test
    void fetchesArrearsAccountsWithSourceFilter() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"total\":1,\"list\":[{\"id\":\"A-1\",\"accountNumber\":\"3066-000001\",\"name\":\"测试商户\",\"arrears\":\"125.50\",\"arrearsTime\":\"2026-10-11 00:00:46\"}]}}"));

        var accounts = adapter.fetchArrearsAccounts(server.url("/").toString(), "TOKEN", "4801");

        assertEquals(1, accounts.size());
        assertEquals("125.50", accounts.get(0).getArrears().toPlainString());
        assertEquals("2026-10-11 00:00:46", accounts.get(0).getArrearsStartedAt());
        RecordedRequest request = server.takeRequest();
        assertEquals("arrears", request.getRequestUrl().queryParameter("type"));
        assertEquals("4801", request.getRequestUrl().queryParameter("spaceId"));
        assertEquals("1", request.getRequestUrl().queryParameter("pageNumber"));
        assertEquals("50", request.getRequestUrl().queryParameter("pageSize"));
    }

    @Test
    void fetchesDailyAccountHistoryBills() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":[{\"id\":15608270,\"accountNumber\":211948813500420100,\"startTime\":\"2026-10-10 00:00:00\",\"endTime\":\"2026-10-11 00:00:00\",\"amount\":127.52,\"updateTime\":\"2026-10-11 00:02:46\"}]}"));

        var bills = adapter.fetchAccountHistoryBills(server.url("/").toString(), "TOKEN", "7861");

        assertEquals(1, bills.size());
        assertEquals("2026-10-10", bills.get(0).getBillDate().toString());
        assertEquals("127.52", bills.get(0).getAmount().toPlainString());
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/account/7861/history-bills?id=7861", request.getPath());
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

    @Test
    void mapsEnergyReportColumnsToDailyUsage() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"columnList\":[{\"prop\":\"_1\"},{\"prop\":\"_2\"}],\"valueList\":[{\"deviceId\":\"DEVICE-1\",\"_1\":\"12.5\",\"_2\":\"8.25\"}]}}"));
        PlatformDevice device = PlatformDevice.builder().platformDeviceId("DEVICE-1").deviceName("电表")
                .commAddress("1").productId("100").build();

        var points = adapter.fetchDailyUsage(server.url("/").toString(), "TOKEN", List.of(device),
                "ELECTRIC", "11X", "正向有功电能",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));

        assertEquals(2, points.size());
        assertEquals("12.5", points.get(0).value().toPlainString());
        assertEquals(LocalDate.of(2026, 8, 2), points.get(1).usageDate());
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/devices/show/energy-report-data", request.getPath());
        assertEquals("TOKEN", request.getHeader("token"));
    }

    @Test
    void loadsCumulativeMetricFromProductConfiguration() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":[{\"identifier\":\"12L\",\"identifierName\":\"累积流量\",\"productIds\":[437]}]}"));

        var metrics = adapter.fetchCumulativeMetrics(server.url("/").toString(), "TOKEN", "437");

        assertEquals(1, metrics.size());
        assertEquals("12L", metrics.get(0).identifier());
        assertEquals("累积流量", metrics.get(0).identifierName());
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/product/functions", request.getRequestUrl().encodedPath());
        assertEquals("437", request.getRequestUrl().queryParameter("ids"));
        assertEquals("CUMULATIVE", request.getRequestUrl().queryParameter("valueTypeEnum"));
        assertEquals("TOKEN", request.getHeader("token"));
    }

    @Test
    void mapsYearReportColumnsToMonthlyUsage() throws Exception {
        server.enqueue(json("{\"code\":\"200000\",\"data\":{\"columnList\":[{\"prop\":\"_7\"},{\"prop\":\"_8\"}],\"valueList\":[{\"deviceId\":\"DEVICE-1\",\"_7\":\"318.75\",\"_8\":\"402.5\"}]}}"));
        PlatformDevice device = PlatformDevice.builder().platformDeviceId("DEVICE-1").deviceName("电表")
                .commAddress("1").productId("100").build();

        var points = adapter.fetchMonthlyUsage(server.url("/").toString(), "TOKEN", List.of(device),
                "ELECTRIC", "11X", "正向有功电能",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, points.size());
        assertEquals(LocalDate.of(2026, 7, 1), points.get(0).usageMonth());
        assertEquals("318.75", points.get(0).value().toPlainString());
        assertEquals(LocalDate.of(2026, 8, 1), points.get(1).usageMonth());
        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertEquals("/api/devices/show/energy-report-data", request.getPath());
        assertTrue(body.contains("\"timeType\":\"year\""));
    }

    private MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
