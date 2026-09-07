package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformDailyUsage;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import com.wanyuecity.energy.usage.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EnergyUsageService {
    private final JdbcTemplate jdbc;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final PlatformConfigService configService;
    private final PlatformAdapter platformAdapter;

    public List<Map<String, Object>> groups() {
        return jdbc.queryForList("""
                SELECT g.*, COUNT(a.platform_device_id) device_count
                FROM device_group g LEFT JOIN device_group_assignment a ON a.group_id=g.id
                GROUP BY g.id ORDER BY g.sort_order,g.id""");
    }

    @Transactional
    public Map<String, Object> createGroup(DeviceGroupRequest request) {
        jdbc.update("INSERT INTO device_group(group_name,color,sort_order,remark) VALUES(?,?,?,?)",
                request.groupName().trim(), defaultColor(request.color()), valueOrZero(request.sortOrder()), request.remark());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return jdbc.queryForMap("SELECT * FROM device_group WHERE id=?", id);
    }

    @Transactional
    public void updateGroup(Long id, DeviceGroupRequest request) {
        int count = jdbc.update("UPDATE device_group SET group_name=?,color=?,sort_order=?,remark=? WHERE id=?",
                request.groupName().trim(), defaultColor(request.color()), valueOrZero(request.sortOrder()), request.remark(), id);
        if (count == 0) throw BizException.badRequest("设备分组不存在");
    }

    @Transactional
    public void deleteGroup(Long id) {
        jdbc.update("DELETE FROM device_group WHERE id=?", id);
    }

    public List<Map<String, Object>> devices(String keyword, Long groupId, Boolean ungrouped, String energyType) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.platform_device_id,d.device_name,d.comm_address,d.serial_number,d.product_id,d.product_name,
                       d.energy_type,d.energy_type_name,d.comm_status,d.last_acq_time,d.last_synced_at,
                       a.group_id,g.group_name,
                       (SELECT GROUP_CONCAT(r.account_number ORDER BY r.account_number SEPARATOR ', ')
                          FROM platform_account_device_current r WHERE r.platform_device_id=d.platform_device_id) account_numbers,
                       (SELECT MAX(u.usage_date) FROM energy_usage_daily u WHERE u.platform_device_id=d.platform_device_id) last_usage_date
                FROM platform_device_current d
                LEFT JOIN device_group_assignment a ON a.platform_device_id=d.platform_device_id
                LEFT JOIN device_group g ON g.id=a.group_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (d.device_name LIKE ? OR d.comm_address LIKE ? OR d.serial_number LIKE ? OR d.platform_device_id LIKE ?)");
            String k = "%" + keyword.trim() + "%"; Collections.addAll(args, k, k, k, k);
        }
        if (groupId != null) { sql.append(" AND a.group_id=?"); args.add(groupId); }
        if (Boolean.TRUE.equals(ungrouped)) sql.append(" AND a.group_id IS NULL");
        if (energyType != null && !energyType.isBlank()) { sql.append(" AND d.energy_type=?"); args.add(energyType); }
        sql.append(" ORDER BY COALESCE(g.sort_order,9999),d.device_name,d.platform_device_id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public int assign(DeviceGroupAssignRequest request) {
        int count = 0;
        for (String deviceId : new LinkedHashSet<>(request.deviceIds())) {
            count += jdbc.update("""
                    INSERT INTO device_group_assignment(platform_device_id,group_id) VALUES(?,?)
                    ON DUPLICATE KEY UPDATE group_id=VALUES(group_id),updated_at=CURRENT_TIMESTAMP(3)""",
                    deviceId, request.groupId());
        }
        return count;
    }

    @Transactional
    public void saveMetric(EnergyMetricRequest request) {
        String mode = "READING".equalsIgnoreCase(request.valueMode()) ? "READING" : "USAGE";
        jdbc.update("""
                INSERT INTO device_energy_metric(product_id,energy_type,identifier,identifier_name,value_mode)
                VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE identifier=VALUES(identifier),identifier_name=VALUES(identifier_name),
                value_mode=VALUES(value_mode),enabled=1""", request.productId(), request.energyType().toUpperCase(),
                request.identifier(), request.identifierName(), mode);
    }

    public List<Map<String, Object>> metrics() {
        return jdbc.queryForList("SELECT * FROM device_energy_metric WHERE enabled=1 ORDER BY energy_type,product_id");
    }

    public Map<String, Object> sync(UsageSyncRequest request) {
        validateRange(request.startDate(), request.endDate());
        List<PlatformDeviceCurrent> current = selectDevices(request.groupId(), request.deviceIds());
        if (current.isEmpty()) throw BizException.badRequest("没有符合条件的设备");
        String batchNo = "EU" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        jdbc.update("INSERT INTO energy_usage_sync_batch(batch_no,start_date,end_date,group_id,requested,status,started_at) VALUES(?,?,?,?,?,'RUNNING',?)",
                batchNo, request.startDate(), request.endDate(), request.groupId(), current.size(), LocalDateTime.now());
        Long batchId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        int succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try {
            var connection = configService.connect();
            Map<MetricKey, List<PlatformDevice>> groups = new LinkedHashMap<>();
            for (PlatformDeviceCurrent d : current) {
                try {
                    Metric metric = resolveMetric(d);
                    groups.computeIfAbsent(new MetricKey(metric, d.getEnergyType()), k -> new ArrayList<>()).add(toPlatformDevice(d));
                } catch (Exception ex) {
                    failed++; errors.add(label(d) + ": " + ex.getMessage());
                }
            }
            for (var entry : groups.entrySet()) {
                List<PlatformDevice> devices = entry.getValue();
                Metric metric = entry.getKey().metric();
                for (int offset = 0; offset < devices.size(); offset += 50) {
                    List<PlatformDevice> batch = devices.subList(offset, Math.min(offset + 50, devices.size()));
                    try {
                        syncDateChunks(connection, batch, entry.getKey().energyType(), metric,
                                request.startDate(), request.endDate(), batchId);
                        succeeded += batch.size();
                    } catch (Exception ex) {
                        failed += batch.size(); errors.add("设备批次 " + (offset / 50 + 1) + ": " + ex.getMessage());
                    }
                }
            }
            String status = failed == 0 ? "SUCCESS" : (succeeded > 0 ? "PARTIAL" : "FAILED");
            jdbc.update("UPDATE energy_usage_sync_batch SET succeeded=?,failed=?,status=?,error_message=?,completed_at=? WHERE id=?",
                    succeeded, failed, status, truncate(String.join("\n", errors), 4000), LocalDateTime.now(), batchId);
            return Map.of("batchNo", batchNo, "status", status, "requested", current.size(), "succeeded", succeeded,
                    "failed", failed, "points", countPoints(batchId));
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE energy_usage_sync_batch SET failed=?,status='FAILED',error_message=?,completed_at=? WHERE id=?",
                    current.size(), truncate(ex.getMessage(), 4000), LocalDateTime.now(), batchId);
            throw ex;
        }
    }

    public List<Map<String, Object>> syncBatches() {
        return jdbc.queryForList("SELECT * FROM energy_usage_sync_batch ORDER BY started_at DESC LIMIT 50");
    }

    public Map<String, Object> analysis(LocalDate start, LocalDate end, String period, Long groupId, String deviceId, Long dimensionId, Long dimensionValueId) {
        validateAnalysisRange(start, end);
        String normalizedPeriod = period == null ? "MONTH" : period.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("WEEK", "MONTH", "QUARTER", "YEAR").contains(normalizedPeriod)) {
            throw BizException.badRequest("统计周期仅支持周、月、季、年");
        }
        AnalysisFilter current = analysisFilter(start, end, groupId, deviceId, dimensionId, dimensionValueId);
        String bucket = bucketExpression(normalizedPeriod);
        List<Map<String, Object>> trend = jdbc.queryForList("""
                SELECT %s period_start,
                       SUM(CASE WHEN UPPER(u.energy_type)='ELECTRIC' THEN COALESCE(u.usage_value,0) ELSE 0 END) electric_usage,
                       SUM(CASE WHEN UPPER(u.energy_type)='WATER' THEN COALESCE(u.usage_value,0) ELSE 0 END) water_usage,
                       COUNT(DISTINCT CASE WHEN UPPER(u.energy_type)='ELECTRIC' THEN u.platform_device_id END) electric_devices,
                       COUNT(DISTINCT CASE WHEN UPPER(u.energy_type)='WATER' THEN u.platform_device_id END) water_devices
                FROM energy_usage_daily u LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                WHERE u.usage_date BETWEEN ? AND ? %s
                GROUP BY period_start ORDER BY period_start
                """.formatted(bucket, current.joins(), current.sql()), current.args().toArray());
        List<Map<String, Object>> ranking = jdbc.queryForList("""
                SELECT u.platform_device_id,d.device_name,g.group_name,UPPER(u.energy_type) energy_type,
                       SUM(COALESCE(u.usage_value,0)) total_usage,AVG(COALESCE(u.usage_value,0)) avg_daily_usage,
                       MAX(COALESCE(u.usage_value,0)) peak_daily_usage,COUNT(DISTINCT u.usage_date) covered_days
                FROM energy_usage_daily u JOIN platform_device_current d ON d.platform_device_id=u.platform_device_id
                LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                LEFT JOIN device_group g ON g.id=a.group_id WHERE u.usage_date BETWEEN ? AND ? %s
                GROUP BY u.platform_device_id,d.device_name,g.group_name,u.energy_type ORDER BY energy_type,total_usage DESC
                """.formatted(current.joins(), current.sql()), current.args().toArray());
        List<Map<String,Object>> breakdown = dimensionId == null ? List.of() : jdbc.queryForList("""
                SELECT %s period_start,v.id dimension_value_id,v.value_name dimension_value_name,v.color,
                       SUM(CASE WHEN UPPER(u.energy_type)='ELECTRIC' THEN COALESCE(u.usage_value,0) ELSE 0 END) electric_usage,
                       SUM(CASE WHEN UPPER(u.energy_type)='WATER' THEN COALESCE(u.usage_value,0) ELSE 0 END) water_usage
                FROM energy_usage_daily u LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                JOIN analysis_dimension_value v ON v.id=da.dimension_value_id
                WHERE u.usage_date BETWEEN ? AND ? %s
                GROUP BY period_start,v.id,v.value_name,v.color ORDER BY period_start,v.sort_order,v.id
                """.formatted(bucket,current.joins(),current.sql()),current.args().toArray());
        long rangeDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(rangeDays - 1);
        AnalysisFilter previous = analysisFilter(previousStart, previousEnd, groupId, deviceId, dimensionId, dimensionValueId);
        Map<String, BigDecimal> currentTotals = totals(current);
        Map<String, BigDecimal> previousTotals = totals(previous);
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (String energyType : List.of("ELECTRIC", "WATER")) {
            BigDecimal total = currentTotals.getOrDefault(energyType, BigDecimal.ZERO);
            BigDecimal prior = previousTotals.getOrDefault(energyType, BigDecimal.ZERO);
            long devices = ranking.stream().filter(v -> energyType.equals(String.valueOf(v.get("energy_type")))).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("energyType", energyType);row.put("totalUsage", total);row.put("previousUsage", prior);
            row.put("changePercent", percentageChange(total, prior));row.put("averageDailyUsage", total.divide(BigDecimal.valueOf(rangeDays), 4, java.math.RoundingMode.HALF_UP));
            row.put("deviceCount", devices);summaries.add(row);
        }
        List<Map<String,Object>> dimensionTotals = new ArrayList<>();
        if(dimensionId!=null){Map<Long,Map<String,Object>> totalsByValue=new LinkedHashMap<>();for(Map<String,Object> row:breakdown){Long valueId=((Number)row.get("dimension_value_id")).longValue();Map<String,Object> total=totalsByValue.computeIfAbsent(valueId,k->{Map<String,Object> m=new LinkedHashMap<>();m.put("dimensionValueId",k);m.put("dimensionValueName",row.get("dimension_value_name"));m.put("color",row.get("color"));m.put("electricUsage",BigDecimal.ZERO);m.put("waterUsage",BigDecimal.ZERO);return m;});total.put("electricUsage",decimal(total.get("electricUsage")).add(decimal(row.get("electric_usage"))));total.put("waterUsage",decimal(total.get("waterUsage")).add(decimal(row.get("water_usage"))));}dimensionTotals.addAll(totalsByValue.values());}
        Map<String,Object> result=new LinkedHashMap<>();result.put("period",normalizedPeriod);result.put("startDate",start);result.put("endDate",end);result.put("rangeDays",rangeDays);result.put("trend",trend);result.put("breakdown",breakdown);result.put("dimensionTotals",dimensionTotals);result.put("ranking",ranking);result.put("summaries",summaries);return result;
    }

    private AnalysisFilter analysisFilter(LocalDate start, LocalDate end, Long groupId, String deviceId, Long dimensionId, Long dimensionValueId) {
        List<Object> args = new ArrayList<>(List.of(start, end));String sql = "",joins="";
        if (groupId != null) { sql += " AND a.group_id=?";args.add(groupId); }
        if (deviceId != null && !deviceId.isBlank()) { sql += " AND u.platform_device_id=?";args.add(deviceId.trim()); }
        if(dimensionId!=null){joins=" JOIN device_dimension_assignment da ON da.platform_device_id=u.platform_device_id AND u.usage_date>=da.effective_from AND (da.effective_to IS NULL OR u.usage_date<=da.effective_to) ";sql+=" AND da.dimension_id=?";args.add(dimensionId);if(dimensionValueId!=null){sql+=" AND da.dimension_value_id=?";args.add(dimensionValueId);}}
        return new AnalysisFilter(joins, sql, args);
    }

    private Map<String, BigDecimal> totals(AnalysisFilter filter) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT UPPER(u.energy_type) energy_type,SUM(COALESCE(u.usage_value,0)) total_usage
                FROM energy_usage_daily u LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                WHERE u.usage_date BETWEEN ? AND ? %s GROUP BY UPPER(u.energy_type)
                """.formatted(filter.joins(),filter.sql()), filter.args().toArray());
        Map<String, BigDecimal> result = new HashMap<>();
        rows.forEach(v -> result.put(String.valueOf(v.get("energy_type")), decimal(v.get("total_usage"))));return result;
    }

    private String bucketExpression(String period) {
        return switch (period) {
            case "WEEK" -> "DATE_SUB(u.usage_date, INTERVAL WEEKDAY(u.usage_date) DAY)";
            case "QUARTER" -> "STR_TO_DATE(CONCAT(YEAR(u.usage_date),'-',LPAD((QUARTER(u.usage_date)-1)*3+1,2,'0'),'-01'),'%Y-%m-%d')";
            case "YEAR" -> "MAKEDATE(YEAR(u.usage_date),1)";
            default -> "DATE_SUB(u.usage_date, INTERVAL DAYOFMONTH(u.usage_date)-1 DAY)";
        };
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO : null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, java.math.RoundingMode.HALF_UP);
    }

    private void syncDateChunks(PlatformConfigService.PlatformConnection c, List<PlatformDevice> devices,
                                String energyType, Metric metric, LocalDate start, LocalDate end, Long batchId) {
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate chunkEnd = YearMonth.from(cursor).atEndOfMonth();
            if (chunkEnd.isAfter(end)) chunkEnd = end;
            List<PlatformDailyUsage> points = platformAdapter.fetchDailyUsage(c.baseUrl(), c.token(), devices,
                    energyType, metric.identifier(), metric.name(), cursor, chunkEnd);
            for (PlatformDailyUsage p : points) upsertPoint(p, energyType, metric, batchId);
            cursor = chunkEnd.plusDays(1);
        }
    }

    private void upsertPoint(PlatformDailyUsage p, String energyType, Metric metric, Long batchId) {
        BigDecimal usage = "READING".equals(metric.mode()) ? null : p.value();
        BigDecimal reading = "READING".equals(metric.mode()) ? p.value() : null;
        jdbc.update("""
                INSERT INTO energy_usage_daily(platform_device_id,usage_date,energy_type,identifier,identifier_name,
                  usage_value,meter_reading,value_mode,sync_batch_id,synced_at,raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE energy_type=VALUES(energy_type),identifier_name=VALUES(identifier_name),
                  usage_value=VALUES(usage_value),meter_reading=VALUES(meter_reading),value_mode=VALUES(value_mode),
                  sync_batch_id=VALUES(sync_batch_id),synced_at=VALUES(synced_at),raw_json=VALUES(raw_json)""",
                p.platformDeviceId(), p.usageDate(), energyType, p.identifier(), p.identifierName(), usage, reading,
                metric.mode(), batchId, LocalDateTime.now(), p.rawJson());
    }

    private Metric resolveMetric(PlatformDeviceCurrent d) {
        if (blank(d.getProductId()) || blank(d.getCommAddress())) throw new IllegalArgumentException("缺少 productId 或通讯地址");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT identifier,identifier_name,value_mode FROM device_energy_metric WHERE product_id=? AND energy_type=? AND enabled=1",
                d.getProductId(), d.getEnergyType());
        if (!rows.isEmpty()) return new Metric(String.valueOf(rows.get(0).get("identifier")),
                String.valueOf(rows.get(0).get("identifier_name")), String.valueOf(rows.get(0).get("value_mode")));
        if ("ELECTRIC".equalsIgnoreCase(d.getEnergyType())) return new Metric("11X", "正向有功电能", "USAGE");
        if ("WATER".equalsIgnoreCase(d.getEnergyType())) return new Metric("totalFlow", "累计流量", "USAGE");
        throw new IllegalArgumentException("尚未配置产品测量点");
    }

    private List<PlatformDeviceCurrent> selectDevices(Long groupId, List<String> ids) {
        var q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlatformDeviceCurrent>();
        if (ids != null && !ids.isEmpty()) q.in("platform_device_id", ids);
        if (groupId != null) q.exists("SELECT 1 FROM device_group_assignment a WHERE a.platform_device_id=platform_device_current.platform_device_id AND a.group_id=" + groupId);
        return deviceMapper.selectList(q);
    }

    private PlatformDevice toPlatformDevice(PlatformDeviceCurrent d) {
        return PlatformDevice.builder().platformDeviceId(d.getPlatformDeviceId()).deviceName(d.getDeviceName())
                .commAddress(d.getCommAddress()).productId(d.getProductId()).productName(d.getProductName())
                .energyType(d.getEnergyType()).energyTypeName(d.getEnergyTypeName()).build();
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw BizException.badRequest("日期范围无效");
        if (start.plusYears(2).isBefore(end)) throw BizException.badRequest("单次查询范围不能超过两年");
    }
    private void validateAnalysisRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw BizException.badRequest("日期范围无效");
        if (start.plusYears(10).isBefore(end)) throw BizException.badRequest("分析范围不能超过十年");
    }
    private int countPoints(Long batchId) { return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM energy_usage_daily WHERE sync_batch_id=?", Integer.class, batchId)).orElse(0); }
    private int valueOrZero(Integer v) { return v == null ? 0 : v; }
    private String defaultColor(String v) { return blank(v) ? "#409EFF" : v; }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String label(PlatformDeviceCurrent d) { return blank(d.getDeviceName()) ? d.getPlatformDeviceId() : d.getDeviceName(); }
    private String truncate(String v, int n) { return v == null || v.length() <= n ? v : v.substring(0, n); }
    private BigDecimal decimal(Object v) { return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()); }
    private record Metric(String identifier, String name, String mode) {}
    private record MetricKey(Metric metric, String energyType) {}
    private record AnalysisFilter(String joins, String sql, List<Object> args) {}
}
