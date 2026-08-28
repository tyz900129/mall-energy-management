package com.wanyuecity.energy.platform.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountDeviceRelation;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link PlatformAdapter} for the Leo energy platform
 * ({@code https://app.leotechnology.cn}). It speaks the JSON API documented in
 * 《水电费系统结构.md》. Authentication uses cookie-based session; the returned
 * {@code cookieHeader} (e.g. {@code JSESSIONID=...}) must be replayed on every
 * subsequent call.
 */
@Slf4j(topic = "platform-sync")
@Component
public class LeoEnergyPlatformAdapter implements PlatformAdapter {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int defaultTimeoutMs;
    private final java.util.concurrent.ExecutorService platformHttpExecutor;

    public LeoEnergyPlatformAdapter(
            @Value("${energy.platform.request-timeout-ms:15000}") int defaultTimeoutMs,
            @org.springframework.beans.factory.annotation.Qualifier("platformHttpExecutor")
            @org.springframework.context.annotation.Lazy
            java.util.concurrent.ExecutorService platformHttpExecutor) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.platformHttpExecutor = platformHttpExecutor;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(defaultTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    // ---- login / testConnection ----------------------------------------

    @Override
    public Session login(String baseUrl, String username, String password) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PlatformAdapterException("BAD_CONFIG", "baseUrl is blank");
        }
        if (username == null || password == null) {
            throw new PlatformAdapterException("BAD_CONFIG", "username/password is null");
        }
        // Current platform: JSON login returns data.token. Keep the legacy
        // form endpoint as a fallback for older private deployments.
        LoginResult r = doJsonLogin(baseUrl, username, password);
        if (!r.success) {
            r = doFormLogin(baseUrl, username, password);
        }
        if (!r.success) {
            throw new PlatformAdapterException("AUTH_FAILED", "Platform login failed: " + r.message);
        }
        return new Session(r.cookieHeader, r.cookieHeader, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2));
    }

    @Override
    public TestConnectionResult testConnection(String baseUrl, String username, String password, String spaceId) {
        Session s = login(baseUrl, username, password);
        try {
            JsonNode resp = getJson(baseUrl, "/api/account/page?spaceId=" + enc(spaceId)
                    + "&pageNumber=1&pageSize=10&type=all&name=&mobilePhone=", s.token());
            if (!isSuccess(resp)) {
                return new TestConnectionResult(false, "Account list returned: " + codeAndMessage(resp), null);
            }
            int count = resp.path("data").path("total").asInt(0);
            return new TestConnectionResult(true, "Logged in; account total = " + count, count);
        } catch (PlatformAdapterException ex) {
            return new TestConnectionResult(false, ex.getMessage(), null);
        }
    }

    // ---- accounts / devices / relations --------------------------------

    @Override
    public List<PlatformAccount> fetchAccounts(String baseUrl, String sessionToken, String spaceId) {
        return paginate(baseUrl, sessionToken,
                "/api/account/page?spaceId=" + enc(spaceId) + "&type=all&name=&mobilePhone=",
                "data", "list", this::mapAccount);
    }

    @Override
    public List<PlatformDevice> fetchDevices(String baseUrl, String sessionToken, String spaceId) {
        List<PlatformDevice> summaries = paginate(baseUrl, sessionToken,
                "/api/devices/show/device-monitoring?spaceId=" + enc(spaceId)
                        + "&commStatus=&deviceName=&productId=&deviceSystem=",
                "data", "list", this::mapDevice);
        if (summaries.isEmpty()) return summaries;

        // 列表接口只返回监控摘要；通讯地址、表号、网关名称和通道信息在详情接口。
        List<java.util.concurrent.Callable<PlatformDevice>> tasks = new ArrayList<>(summaries.size());
        for (PlatformDevice summary : summaries) {
            tasks.add(() -> fetchDeviceDetail(baseUrl, sessionToken, summary));
        }
        List<PlatformDevice> enriched = new ArrayList<>(summaries.size());
        try {
            long timeout = Math.max(60L, (long) defaultTimeoutMs / 1000L * 2L) * Math.max(1, summaries.size());
            List<java.util.concurrent.Future<PlatformDevice>> futures = platformHttpExecutor.invokeAll(tasks, timeout, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                var future = futures.get(i);
                if (future.isCancelled()) enriched.add(summaries.get(i));
                else try { enriched.add(future.get()); } catch (Exception ex) { enriched.add(summaries.get(i)); }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return summaries;
        }
        return enriched;
    }

    private PlatformDevice fetchDeviceDetail(String baseUrl, String token, PlatformDevice summary) {
        try {
            String id = summary.getPlatformDeviceId();
            JsonNode resp = getJson(baseUrl, "/api/devices/show/" + enc(id) + "?id=" + enc(id), token);
            if (!isSuccess(resp) || !resp.path("data").isObject()) return summary;
            return mergeDevice(summary, resp.path("data"));
        } catch (Exception ex) {
            log.warn("device detail fetch failed, deviceId={}: {}", summary.getPlatformDeviceId(), ex.getMessage());
            return summary;
        }
    }

    @Override
    public List<PlatformAccountDeviceRelation> fetchAccountDeviceRelations(String baseUrl, String sessionToken, String spaceId) {
        // The source-platform does not provide a single batch endpoint for
        // account-device relations; each account requires its own
        // /api/account/{id}/devices call. Fan out across a bounded thread
        // pool so the wall time is roughly accounts/parallelism instead of
        // accounts * latency. A task that fails is logged and skipped —
        // a missing account in the relation table is preferable to a
        // full-batch failure.
        List<PlatformAccount> accounts = fetchAccounts(baseUrl, sessionToken, spaceId);
        if (accounts.isEmpty()) {
            return List.of();
        }

        List<java.util.concurrent.Callable<List<PlatformAccountDeviceRelation>>> tasks = new ArrayList<>(accounts.size());
        for (PlatformAccount a : accounts) {
            final String accountNo = a.getAccountNumber();
            final String accountId = a.getPlatformAccountId();
            tasks.add(() -> fetchRelationsForAccount(baseUrl, sessionToken, accountId, accountNo));
        }

        List<PlatformAccountDeviceRelation> out = new java.util.ArrayList<>();
        int failed = 0;
        try {
            long perBatchTimeout = Math.max(30L, (long) defaultTimeoutMs / 1000L * 4L) * accounts.size();
            List<java.util.concurrent.Future<List<PlatformAccountDeviceRelation>>> futures =
                    platformHttpExecutor.invokeAll(tasks, perBatchTimeout, TimeUnit.SECONDS);
            for (java.util.concurrent.Future<List<PlatformAccountDeviceRelation>> f : futures) {
                if (f.isCancelled()) {
                    failed++;
                    continue;
                }
                try {
                    List<PlatformAccountDeviceRelation> per = f.get();
                    if (per != null && !per.isEmpty()) {
                        out.addAll(per);
                    }
                } catch (java.util.concurrent.ExecutionException ex) {
                    failed++;
                    log.warn("relations fetch failed for one account: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PlatformAdapterException("INTERRUPTED", "Relations fetch was interrupted", ex);
        }
        if (failed > 0) {
            log.warn("relations fetch: {}/{} account calls failed (skipped)", failed, accounts.size());
        }
        return out;
    }

    /** Fetch relations for a single account. Empty list on any failure. */
    private List<PlatformAccountDeviceRelation> fetchRelationsForAccount(String baseUrl, String sessionToken, String accountId, String accountNumber) {
        try {
            JsonNode resp = getJson(baseUrl,
                    "/api/account/" + enc(accountId) + "/devices?id=" + enc(accountId),
                    sessionToken);
            if (!isSuccess(resp)) {
                return List.of();
            }
            JsonNode data = resp.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<PlatformAccountDeviceRelation> out = new ArrayList<>();
            for (JsonNode dev : data) {
                // `id` is the account-device relation row id on the current
                // platform. The stable physical device identity is `deviceId`.
                String devId = dev.path("deviceId").asText(dev.path("id").asText(""));
                if (devId.isEmpty()) continue;
                out.add(PlatformAccountDeviceRelation.builder()
                        .accountNumber(accountNumber)
                        .platformDeviceId(devId)
                        .build());
            }
            return out;
        } catch (Exception ex) {
            log.debug("relations fetch for account {} failed: {}", accountNumber, ex.getMessage());
            return List.of();
        }
    }

    // ---- HTTP plumbing -------------------------------------------------

    private JsonNode getJson(String baseUrl, String path, String cookieHeader) {
        Request req = new Request.Builder()
                .url(joinUrl(baseUrl, path))
                .header("Cookie", cookieHeader == null ? "" : cookieHeader)
                .header("token", cookieHeader == null ? "" : cookieHeader)
                .header("Accept", "application/json")
                .get()
                .build();
        return execute(req);
    }

    private JsonNode execute(Request req) {
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new PlatformAdapterException("HTTP_" + resp.code(),
                        "HTTP " + resp.code() + " on " + req.url().encodedPath());
            }
            return mapper.readTree(body);
        } catch (IOException ex) {
            throw new PlatformAdapterException("IO_ERROR", "I/O error calling platform", ex);
        }
    }

    private <T> List<T> paginate(String baseUrl, String cookie, String pathPrefix,
                                 String dataKey, String listKey,
                                 java.util.function.Function<JsonNode, T> mapper) {
        List<T> out = new ArrayList<>();
        int page = 1;
        while (true) {
            String sep = pathPrefix.contains("?") ? "&" : "?";
            String path = pathPrefix + sep + "pageNumber=" + page + "&pageSize=" + DEFAULT_PAGE_SIZE;
            JsonNode resp = getJson(baseUrl, path, cookie);
            if (!isSuccess(resp)) {
                throw new PlatformAdapterException("PLATFORM_ERROR",
                        "Paginated fetch failed at page " + page + ": " + codeAndMessage(resp));
            }
            JsonNode data = resp.path(dataKey);
            JsonNode list = data.path(listKey);
            if (!list.isArray() || list.isEmpty()) {
                break;
            }
            for (JsonNode item : list) {
                out.add(mapper.apply(item));
            }
            int total = data.path("total").asInt(0);
            if (out.size() >= total || list.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
            if (page > 500) {  // safety cap
                log.warn("paginate reached safety cap (500 pages)");
                break;
            }
        }
        return out;
    }

    private LoginResult doJsonLogin(String baseUrl, String username, String password) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", password));
            Request req = new Request.Builder()
                    .url(joinUrl(baseUrl, "/api/auth/app/login"))
                    .header("Accept", "application/json")
                    .post(RequestBody.create(json, JSON))
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    JsonNode root = mapper.readTree(body);
                    if (isSuccess(root)) {
                        String token = root.path("data").path("token").asText("");
                        if (token.isBlank()) {
                            return LoginResult.fail("login response did not contain data.token");
                        }
                        return LoginResult.ok(token);
                    }
                    return LoginResult.fail(codeAndMessage(root));
                }
                return LoginResult.fail("HTTP " + resp.code());
            }
        } catch (Exception ex) {
            log.debug("json login probe failed: {}", ex.getMessage());
            return LoginResult.fail(ex.getMessage());
        }
    }

    private LoginResult doFormLogin(String baseUrl, String username, String password) {
        try {
            FormBody form = new FormBody.Builder()
                    .add("account", username)
                    .add("password", password)
                    .build();
            Request req = new Request.Builder()
                    .url(joinUrl(baseUrl, "/api/login"))
                    .header("Accept", "application/json")
                    .post(form)
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    JsonNode root = mapper.readTree(body);
                    if (isSuccess(root)) {
                        return LoginResult.ok(cookieOf(resp));
                    }
                    return LoginResult.fail(codeAndMessage(root));
                }
                return LoginResult.fail("HTTP " + resp.code());
            }
        } catch (Exception ex) {
            return LoginResult.fail(ex.getMessage());
        }
    }

    private String cookieOf(Response resp) {
        // Concatenate Set-Cookie values; this is sufficient for cookie-based sessions.
        List<String> values = resp.headers("Set-Cookie");
        if (values.isEmpty()) {
            return "";
        }
        return String.join("; ", values.stream()
                .map(v -> v.split(";", 2)[0])
                .toList());
    }

    // ---- mapping -------------------------------------------------------

    private PlatformAccount mapAccount(JsonNode n) {
        return PlatformAccount.builder()
                .platformAccountId(n.path("id").asText(""))
                .accountNumber(n.path("accountNumber").asText(""))
                .accountName(n.path("userName").asText(""))
                .userName(n.path("name").asText(""))
                .mobilePhone(n.path("mobilePhone").asText(""))
                .balance(decimalOrNull(n.path("balance")))
                .arrears(decimalOrNull(n.path("arrears")))
                .platformStatus(n.path("billConfiguration").path("status").asText(""))
                .sourceSpaceId(n.path("spaceId").asText(""))
                .build();
    }

    private PlatformDevice mapDevice(JsonNode n) {
        return PlatformDevice.builder()
                .platformDeviceId(firstText(n,"deviceId","id"))
                .deviceName(firstText(n,"deviceName","name"))
                .commAddress(firstText(n,"commAddress"))
                .serialNumber(firstText(n,"serialNumber","number"))
                .gatewayId(firstText(n,"gatewayId"))
                .gatewayName(firstText(n,"gatewayName"))
                .channelId(firstText(n,"channelId"))
                .channelName(firstText(n,"channelName"))
                .productId(firstText(n,"productId"))
                .productName(firstText(n,"productName"))
                .commStatus(n.path("commStatus").isMissingNode() ? null : n.path("commStatus").asInt())
                .lastAcqTime(n.path("acqTime").asText(""))
                .deviceSystem(n.path("deviceSystem").asText(""))
                .deviceSystemName(n.path("deviceSystemName").asText(""))
                .energyType(mapEnergyType(n.path("deviceSystemName").asText("")))
                .energyTypeName(n.path("deviceSystemName").asText(""))
                .build();
    }

    private PlatformDevice mergeDevice(PlatformDevice summary, JsonNode detail) {
        PlatformDevice d = mapDevice(detail);
        return PlatformDevice.builder()
                .platformDeviceId(prefer(d.getPlatformDeviceId(), summary.getPlatformDeviceId()))
                .deviceName(prefer(d.getDeviceName(), summary.getDeviceName()))
                .commAddress(prefer(d.getCommAddress(), summary.getCommAddress()))
                .serialNumber(prefer(d.getSerialNumber(), summary.getSerialNumber()))
                .gatewayId(prefer(d.getGatewayId(), summary.getGatewayId()))
                .gatewayName(prefer(d.getGatewayName(), summary.getGatewayName()))
                .channelId(prefer(d.getChannelId(), summary.getChannelId()))
                .channelName(prefer(d.getChannelName(), summary.getChannelName()))
                .productId(prefer(d.getProductId(), summary.getProductId()))
                .productName(prefer(d.getProductName(), summary.getProductName()))
                .energyType(prefer(d.getEnergyType(), summary.getEnergyType()))
                .energyTypeName(prefer(d.getEnergyTypeName(), summary.getEnergyTypeName()))
                .deviceSystem(prefer(d.getDeviceSystem(), summary.getDeviceSystem()))
                .deviceSystemName(prefer(d.getDeviceSystemName(), summary.getDeviceSystemName()))
                .commStatus(d.getCommStatus() != null ? d.getCommStatus() : summary.getCommStatus())
                .lastAcqTime(prefer(d.getLastAcqTime(), summary.getLastAcqTime())).build();
    }

    private String firstText(JsonNode n, String... names) {
        for (String name : names) { String v = n.path(name).asText(""); if (!v.isBlank()) return v; }
        return "";
    }
    private String prefer(String primary, String fallback) { return primary == null || primary.isBlank() ? fallback : primary; }

    private String mapEnergyType(String systemName) {
        if (systemName == null) return "";
        if (systemName.contains("配电") || systemName.toLowerCase().contains("electric")) return "ELECTRIC";
        if (systemName.contains("给水") || systemName.contains("水") || systemName.toLowerCase().contains("water")) return "WATER";
        if (systemName.contains("气") || systemName.toLowerCase().contains("gas")) return "GAS";
        return "OTHER";
    }

    // ---- helpers -------------------------------------------------------

    private boolean isSuccess(JsonNode resp) {
        String code = resp.path("code").asText("");
        return "200000".equals(code) || "200".equals(code) || "0".equals(code);
    }

    private String codeAndMessage(JsonNode resp) {
        return "code=" + resp.path("code").asText("?") + " message=" + resp.path("message").asText("?");
    }

    private BigDecimal decimalOrNull(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return null;
        return new BigDecimal(n.asText("0"));
    }

    private String joinUrl(String baseUrl, String path) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        if (base == null) {
            throw new PlatformAdapterException("BAD_CONFIG", "Invalid baseUrl: " + baseUrl);
        }
        HttpUrl full = base.newBuilder().encodedPath(path).build();
        if (path.contains("?")) {
            // Re-attach query — simpler approach is to use the query-aware builder.
            int q = path.indexOf('?');
            String p = path.substring(0, q);
            String qs = path.substring(q + 1);
            Map<String, String> params = splitQuery(qs);
            HttpUrl.Builder b = base.newBuilder().encodedPath(p);
            for (Map.Entry<String, String> e : params.entrySet()) {
                b.setQueryParameter(e.getKey(), e.getValue());
            }
            full = b.build();
        }
        return full.toString();
    }

    private Map<String, String> splitQuery(String qs) {
        Map<String, String> out = new HashMap<>();
        for (String pair : qs.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(pair, "");
            } else {
                out.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return out;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private record LoginResult(boolean success, String message, String cookieHeader) {
        static LoginResult ok(String cookie) { return new LoginResult(true, "ok", cookie); }
        static LoginResult fail(String msg) { return new LoginResult(false, msg, null); }
    }
}

