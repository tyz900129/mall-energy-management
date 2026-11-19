package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j(topic = "device-alert")
@Service
@RequiredArgsConstructor
public class DeviceAlertService {
    public static final String EVENT = "ENERGY_DEVICE_OFFLINE";
    /** 恢复事件刻意不用 OFFLINE 前缀，避免 Webhook 按子串匹配时被离线订阅误收。 */
    public static final String EVENT_RECOVERED = "ENERGY_DEVICE_ONLINE";
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final PlatformConfigService configService;
    private final PlatformAdapter adapter;
    private final NotificationService notificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public Map<String, Object> run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw BizException.badRequest("设备离线巡检正在执行，请稍后再试");
        }
        Long runId = null;
        try {
            Map<String, Object> setting = getSetting();
            if (!bool(setting.get("enabled"))) {
                return Map.of("skipped", true, "reason", "disabled");
            }
            if ("SCHEDULED".equals(triggerType) && !due(setting)) {
                return Map.of("skipped", true, "reason", "not_due");
            }

            jdbc.update("INSERT INTO energy_device_alert_run(trigger_type,status) VALUES(?,'RUNNING')", triggerType);
            runId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            int thresholdMin = num(setting.get("offline_threshold_minutes"), 30);
            int consecutive = num(setting.get("consecutive_count"), 2);
            LocalDateTime now = LocalDateTime.now();
            boolean quiet = inQuietHours(setting, now.toLocalTime());

            // 从平台监控接口拉取全部设备的实时通讯状态（commStatus/acqTime）
            Set<String> excluded = activeExclusions();
            List<Map<String, Object>> devices = loadMonitoredDevices().stream()
                    .filter(d -> !excluded.contains(str(d.get("platformDeviceId"))))
                    .collect(Collectors.toList());

            // 判定离线
            List<Map<String, Object>> offline = devices.stream()
                    .filter(d -> isOffline(d, thresholdMin, now))
                    .collect(Collectors.toList());

            Set<String> offlineDeviceIds = offline.stream()
                    .map(d -> str(d.get("platformDeviceId")))
                    .collect(Collectors.toSet());

            // 按网关分组
            Map<String, List<Map<String, Object>>> byGateway = devices.stream()
                    .collect(Collectors.groupingBy(d -> str(d.get("gatewayId"))));

            List<AlertScope> alerts = new ArrayList<>();
            Set<String> suppressed = new HashSet<>();

            for (Map.Entry<String, List<Map<String, Object>>> e : byGateway.entrySet()) {
                String gwId = e.getKey();
                List<Map<String, Object>> gwDevices = e.getValue();
                boolean allOffline = gwDevices.stream().allMatch(d -> offlineDeviceIds.contains(str(d.get("platformDeviceId"))));
                if (allOffline && !gwDevices.isEmpty()) {
                    alerts.add(new AlertScope("GATEWAY", gwId, null, null,
                            str(gwDevices.get(0).get("gatewayName")), null, null, gwDevices.size()));
                    gwDevices.forEach(d -> suppressed.add(str(d.get("platformDeviceId"))));
                    continue;
                }
                // 按端口分组
                Map<String, List<Map<String, Object>>> byChannel = gwDevices.stream()
                        .filter(d -> str(d.get("channelId")) != null && !str(d.get("channelId")).isBlank())
                        .collect(Collectors.groupingBy(d -> str(d.get("channelId"))));
                for (Map.Entry<String, List<Map<String, Object>>> ce : byChannel.entrySet()) {
                    String chId = ce.getKey();
                    List<Map<String, Object>> chDevices = ce.getValue();
                    boolean chAllOffline = chDevices.stream().allMatch(d -> offlineDeviceIds.contains(str(d.get("platformDeviceId"))));
                    if (chAllOffline && !chDevices.isEmpty()) {
                        alerts.add(new AlertScope("PORT", gwId, chId, null,
                                str(chDevices.get(0).get("gatewayName")), str(chDevices.get(0).get("channelName")),
                                null, chDevices.size()));
                        chDevices.forEach(d -> suppressed.add(str(d.get("platformDeviceId"))));
                    }
                }
            }
            // 剩余单设备
            for (Map<String, Object> d : offline) {
                String devId = str(d.get("platformDeviceId"));
                if (suppressed.contains(devId)) continue;
                alerts.add(new AlertScope("DEVICE", str(d.get("gatewayId")), str(d.get("channelId")), devId,
                        str(d.get("gatewayName")), str(d.get("channelName")), str(d.get("deviceName")), 1));
            }

            // 处理告警：连续计数、通知、恢复
            int alerted = processAlerts(alerts, consecutive, quiet, now);
            int recovered = processRecoveries(alerts, quiet, now);

            jdbc.update("UPDATE energy_device_alert_run SET status='SUCCESS',scanned_count=?,alerted_count=?,recovered_count=?,completed_at=NOW(3) WHERE id=?",
                    devices.size(), alerted, recovered, runId);

            if (!quiet && alerted > 0) {
                notificationService.notifyConfiguredEvent(EVENT, buildMessage(alerts, now));
            }
            return runRow(runId);
        } catch (Exception ex) {
            log.error("device alert run failed, trigger={}, error={}", triggerType, ex.getMessage(), ex);
            if (runId != null) {
                jdbc.update("UPDATE energy_device_alert_run SET status='FAILED',error_message=?,completed_at=NOW(3) WHERE id=?",
                        shortText(ex.getMessage(), 500), runId);
            }
            throw ex instanceof BizException b ? b : BizException.badRequest("设备离线巡检失败：" + shortText(ex.getMessage(), 180));
        } finally {
            running.set(false);
        }
    }

    /** 拉取实时监控设备，并用本地台账补全网关/端口归属。 */
    private List<Map<String, Object>> loadMonitoredDevices() {
        PlatformConfigService.PlatformConnection connection = configService.connect();
        List<PlatformDevice> monitored = adapter.fetchDeviceMonitoring(
                connection.baseUrl(), connection.token(), connection.spaceId());

        Map<String, Map<String, Object>> ledger = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT platform_device_id pid, gateway_id gatewayId, gateway_name gatewayName, " +
                        "channel_id channelId, channel_name channelName FROM platform_device_current")) {
            ledger.put(str(r.get("pid")), r);
        }

        List<Map<String, Object>> devices = new ArrayList<>();
        for (PlatformDevice pd : monitored) {
            String deviceId = pd.getPlatformDeviceId();
            if (deviceId == null || deviceId.isBlank()) continue;
            Map<String, Object> l = ledger.getOrDefault(deviceId, Map.of());
            String gatewayId = prefer(pd.getGatewayId(), str(l.get("gatewayId")));
            if (gatewayId.isBlank()) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("platformDeviceId", deviceId);
            row.put("deviceName", prefer(pd.getDeviceName(), str(l.get("deviceName"))));
            row.put("gatewayId", gatewayId);
            row.put("gatewayName", prefer(pd.getGatewayName(), str(l.get("gatewayName"))));
            row.put("channelId", prefer(pd.getChannelId(), str(l.get("channelId"))));
            row.put("channelName", prefer(pd.getChannelName(), str(l.get("channelName"))));
            row.put("commStatus", pd.getCommStatus());
            row.put("lastAcqTime", parseAcqTime(pd.getLastAcqTime()));
            devices.add(row);
        }
        return devices;
    }

    /** 有效期内（enabled 且未过期）的不监控设备编号。 */
    private Set<String> activeExclusions() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT platform_device_id FROM energy_device_offline_exclude " +
                        "WHERE enabled=1 AND (effective_from IS NULL OR effective_from<=NOW(3)) " +
                        "  AND (effective_to IS NULL OR effective_to>=NOW(3))", String.class));
    }

    /** 当前实时离线设备列表（含是否已加入不监控清单），供页面展示与批量加入清单。 */
    public Map<String, Object> currentOfflineDevices() {
        Map<String, Object> setting = getSetting();
        int thresholdMin = num(setting.get("offline_threshold_minutes"), 30);
        LocalDateTime now = LocalDateTime.now();
        Set<String> excluded = activeExclusions();
        List<Map<String, Object>> rows = new ArrayList<>();
        int excludedCount = 0;
        for (Map<String, Object> d : loadMonitoredDevices()) {
            if (!isOffline(d, thresholdMin, now)) continue;
            boolean isExcluded = excluded.contains(str(d.get("platformDeviceId")));
            Map<String, Object> r = new LinkedHashMap<>(d);
            r.put("excluded", isExcluded);
            Object lat = d.get("lastAcqTime");
            r.put("lastAcqTime", lat == null ? null : lat.toString().replace('T', ' '));
            rows.add(r);
            if (isExcluded) excludedCount++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("excludedCount", excludedCount);
        out.put("devices", rows);
        return out;
    }

    /** 从离线设备列表批量加入不监控清单。 */
    public int addExclusions(Map<String, Object> body) {
        Object raw = body.get("devices");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw BizException.badRequest("请选择要加入不监控清单的设备");
        }
        String reason = prefer(str(body.get("reason")), "MAINTENANCE");
        String remark = str(body.get("remark"));
        int count = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            String deviceId = str(m.get("platformDeviceId"));
            if (deviceId.isBlank()) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("platformDeviceId", deviceId);
            row.put("deviceName", str(m.get("deviceName")));
            row.put("reason", reason);
            row.put("remark", remark);
            row.put("enabled", true);
            addExclusion(row);
            count++;
        }
        if (count == 0) throw BizException.badRequest("请选择要加入不监控清单的设备");
        return count;
    }

    private int processAlerts(List<AlertScope> alerts, int consecutive, boolean quiet, LocalDateTime now) {
        int count = 0;
        for (AlertScope a : alerts) {
            String scopeKey = a.scopeKey();
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id, consecutive_count cc, notified_at notifiedAt FROM energy_device_alert_event " +
                            "WHERE level=? AND scope_key=? AND status='OPEN' ORDER BY id DESC LIMIT 1",
                    a.level, scopeKey);
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO energy_device_alert_event(level,gateway_id,gateway_name,channel_id,channel_name,platform_device_id,device_name,scope_key,open_scope_key,status,first_offline_at,affected_count) VALUES(?,?,?,?,?,?,?,?,?, 'OPEN', ?, ?)",
                        a.level, a.gatewayId, a.gatewayName, a.channelId, a.channelName, a.deviceId, a.deviceName,
                        scopeKey, scopeKey, now, a.affectedCount);
                count++;
            } else {
                Map<String, Object> row = existing.get(0);
                int cc = num(row.get("cc"), 0) + 1;
                Long id = ((Number) row.get("id")).longValue();
                if (cc >= consecutive && row.get("notifiedAt") == null && !quiet) {
                    jdbc.update("UPDATE energy_device_alert_event SET consecutive_count=?, affected_count=?, notified_at=NOW(3) WHERE id=?",
                            cc, a.affectedCount, id);
                } else {
                    jdbc.update("UPDATE energy_device_alert_event SET consecutive_count=?, affected_count=? WHERE id=?",
                            cc, a.affectedCount, id);
                }
                if (cc >= consecutive && row.get("notifiedAt") == null) count++;
            }
        }
        return count;
    }

    private int processRecoveries(List<AlertScope> alerts, boolean quiet, LocalDateTime now) {
        Set<String> openKeys = alerts.stream().map(AlertScope::scopeKey).collect(Collectors.toSet());
        Set<String> openLevels = alerts.stream().map(a -> a.level).collect(Collectors.toSet());
        List<Map<String, Object>> openEvents = jdbc.queryForList(
                "SELECT id, level, scope_key scopeKey FROM energy_device_alert_event WHERE status='OPEN'");
        int recovered = 0;
        for (Map<String, Object> ev : openEvents) {
            String level = str(ev.get("level"));
            String scopeKey = str(ev.get("scopeKey"));
            if (openLevels.contains(level) && openKeys.contains(scopeKey)) continue;
            Long id = ((Number) ev.get("id")).longValue();
            jdbc.update("UPDATE energy_device_alert_event SET status='RECOVERED', recovered_at=NOW(3), open_scope_key=NULL WHERE id=?", id);
            recovered++;
        }
        if (!quiet && recovered > 0) {
            notificationService.notifyConfiguredEvent(EVENT_RECOVERED,
                    "设备离线告警恢复：本次巡检有 " + recovered + " 条告警已恢复在线。");
        }
        return recovered;
    }

    private boolean isOffline(Map<String, Object> d, int thresholdMin, LocalDateTime now) {
        Object cs = d.get("commStatus");
        if (cs instanceof Number n && n.intValue() == 0) return true;
        Object lat = d.get("lastAcqTime");
        if (lat instanceof LocalDateTime t) {
            return t.plusMinutes(thresholdMin).isBefore(now);
        }
        return false;
    }

    /** 平台 acqTime 形如 "2026-11-14 17:03:21" 或 ISO8601，解析失败按未知处理。 */
    private LocalDateTime parseAcqTime(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().replace('T', ' ');
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean due(Map<String, Object> setting) {
        int interval = num(setting.get("check_interval_minutes"), 120);
        List<Map<String, Object>> last = jdbc.queryForList(
                "SELECT started_at startedAt FROM energy_device_alert_run WHERE status='SUCCESS' ORDER BY id DESC LIMIT 1");
        if (last.isEmpty()) return true;
        Object started = last.get(0).get("startedAt");
        if (!(started instanceof LocalDateTime t)) return true;
        return t.plusMinutes(interval).isBefore(LocalDateTime.now());
    }

    private boolean inQuietHours(Map<String, Object> setting, LocalTime now) {
        LocalTime start = toLocalTime(setting.get("quietStart"));
        LocalTime end = toLocalTime(setting.get("quietEnd"));
        if (start == null || end == null) return false;
        if (start.equals(end)) return false;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end); // 跨天
    }

    private LocalTime toLocalTime(Object v) {
        if (v instanceof LocalTime t) return t;
        if (v instanceof java.sql.Time t) return t.toLocalTime();
        if (v instanceof String s && !s.isBlank()) {
            try { return LocalTime.parse(s.length() == 5 ? s + ":00" : s); } catch (Exception ignored) {}
        }
        return null;
    }

    private String buildMessage(List<AlertScope> alerts, LocalDateTime now) {
        StringBuilder b = new StringBuilder("### 📡 设备离线告警\n\n");
        b.append("**巡检时间**：").append(now == null ? "-" : now.format(MESSAGE_TIME)).append("\n");
        b.append("**告警数量**：").append(alerts.size()).append(" 条\n\n---\n");
        Map<String, Long> byLevel = alerts.stream().collect(Collectors.groupingBy(a -> a.level, Collectors.counting()));
        byLevel.forEach((lvl, cnt) -> b.append("- ").append(levelLabel(lvl)).append("：").append(cnt).append(" 条\n"));
        b.append("\n");
        int max = Math.min(alerts.size(), 15);
        for (int i = 0; i < max; i++) {
            AlertScope a = alerts.get(i);
            b.append("**").append(i + 1).append(". ").append(levelLabel(a.level)).append("**\n");
            if (a.gatewayName != null) b.append("- 网关：").append(a.gatewayName).append("\n");
            if ("PORT".equals(a.level) && a.channelName != null) b.append("- 端口：").append(a.channelName).append("\n");
            if ("DEVICE".equals(a.level) && a.deviceName != null) b.append("- 设备：").append(a.deviceName).append("\n");
            b.append("- 受影响设备数：").append(a.affectedCount).append("\n");
        }
        if (alerts.size() > max) b.append("\n> 其余 ").append(alerts.size() - max).append(" 条告警未展开。\n");
        return b.toString().trim();
    }

    private String levelLabel(String level) {
        return switch (level) {
            case "GATEWAY" -> "网关离线";
            case "PORT" -> "端口离线";
            case "DEVICE" -> "设备离线";
            default -> level;
        };
    }

    // ===== 设置 / 清单 / 事件 查询与维护 =====

    public Map<String, Object> getSetting() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled, check_interval_minutes checkIntervalMinutes, consecutive_count consecutiveCount, " +
                        "offline_threshold_minutes offlineThresholdMinutes, quiet_start quietStart, quiet_end quietEnd " +
                        "FROM energy_device_monitor_setting WHERE id=1");
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO energy_device_monitor_setting(id) VALUES(1) ON DUPLICATE KEY UPDATE id=id");
            return getSetting();
        }
        return rows.get(0);
    }

    public void updateSetting(Map<String, Object> body) {
        jdbc.update("UPDATE energy_device_monitor_setting SET enabled=?, check_interval_minutes=?, consecutive_count=?, " +
                        "offline_threshold_minutes=?, quiet_start=?, quiet_end=? WHERE id=1",
                bool(body.get("enabled")) ? 1 : 0,
                num(body.get("checkIntervalMinutes"), 120),
                num(body.get("consecutiveCount"), 2),
                num(body.get("offlineThresholdMinutes"), 30),
                body.get("quietStart"), body.get("quietEnd"));
    }

    public Map<String, Object> exclusions(String keyword, Boolean enabled, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100), offset = (Math.max(pageNum, 1) - 1) * size;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (platform_device_id LIKE ? OR device_name LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k);
        }
        if (enabled != null) { where.append(" AND enabled=?"); args.add(enabled ? 1 : 0); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM energy_device_offline_exclude" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, platform_device_id platformDeviceId, device_name deviceName, reason, remark, " +
                        "effective_from effectiveFrom, effective_to effectiveTo, enabled, created_at createdAt " +
                        "FROM energy_device_offline_exclude" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", rows);
        out.put("total", total == null ? 0 : total);
        out.put("pageNum", Math.max(pageNum, 1));
        out.put("pageSize", size);
        out.put("running", running.get());
        return out;
    }

    public void addExclusion(Map<String, Object> body) {
        jdbc.update("INSERT INTO energy_device_offline_exclude(platform_device_id, device_name, reason, remark, effective_from, effective_to, enabled) " +
                        "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE device_name=VALUES(device_name), reason=VALUES(reason), remark=VALUES(remark), " +
                        "effective_from=VALUES(effective_from), effective_to=VALUES(effective_to), enabled=VALUES(enabled)",
                str(body.get("platformDeviceId")), str(body.get("deviceName")),
                str(body.getOrDefault("reason", "OTHER")), str(body.get("remark")),
                body.get("effectiveFrom"), body.get("effectiveTo"),
                bool(body.get("enabled")) ? 1 : 0);
    }

    public void deleteExclusion(Long id) {
        jdbc.update("DELETE FROM energy_device_offline_exclude WHERE id=?", id);
    }

    public Map<String, Object> events(String level, String status, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100), offset = (Math.max(pageNum, 1) - 1) * size;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (level != null && !level.isBlank()) { where.append(" AND level=?"); args.add(level); }
        if (status != null && !status.isBlank()) { where.append(" AND status=?"); args.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM energy_device_alert_event" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, level, gateway_id gatewayId, gateway_name gatewayName, channel_id channelId, channel_name channelName, " +
                        "platform_device_id platformDeviceId, device_name deviceName, status, first_offline_at firstOfflineAt, " +
                        "affected_count affectedCount, notified_at notifiedAt, recovered_at recoveredAt " +
                        "FROM energy_device_alert_event" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", rows);
        out.put("total", total == null ? 0 : total);
        out.put("pageNum", Math.max(pageNum, 1));
        out.put("pageSize", size);
        return out;
    }

    public List<Map<String, Object>> runs() {
        return jdbc.queryForList("SELECT id, trigger_type triggerType, status, scanned_count scannedCount, " +
                "alerted_count alertedCount, recovered_count recoveredCount, error_message errorMessage, " +
                "started_at startedAt, completed_at completedAt FROM energy_device_alert_run ORDER BY id DESC LIMIT 20");
    }

    private Map<String, Object> runRow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, trigger_type triggerType, status, scanned_count scannedCount, alerted_count alertedCount, " +
                        "recovered_count recoveredCount, error_message errorMessage, started_at startedAt, completed_at completedAt " +
                        "FROM energy_device_alert_run WHERE id=?", id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private record AlertScope(String level, String gatewayId, String channelId, String deviceId,
                              String gatewayName, String channelName, String deviceName, int affectedCount) {
        String scopeKey() {
            if ("GATEWAY".equals(level)) return gatewayId;
            if ("PORT".equals(level)) return gatewayId + ":" + channelId;
            return deviceId;
        }
    }

    private boolean bool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }
    private int num(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }
    private String str(Object v) { return v == null ? "" : v.toString().trim(); }
    private String prefer(String primary, String fallback) {
        return primary == null || primary.isBlank() ? (fallback == null ? "" : fallback) : primary;
    }
    private String shortText(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
