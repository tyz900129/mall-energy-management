package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.dto.PlatformDailyUsage;
import com.wanyuecity.energy.platform.dto.PlatformMonthlyUsage;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.platform.service.PlatformConfigService;
import com.wanyuecity.energy.usage.service.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class EnergyUsageService {
    private final JdbcTemplate jdbc;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final PlatformConfigService configService;
    private final PlatformAdapter platformAdapter;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @PostConstruct
    void closeInterruptedSyncBatches() {
        jdbc.update("""
                UPDATE energy_usage_sync_batch
                SET monthly_points=(SELECT COUNT(*) FROM energy_usage_monthly m WHERE m.sync_batch_id=energy_usage_sync_batch.id),
                    daily_points=(SELECT COUNT(*) FROM energy_usage_daily d WHERE d.sync_batch_id=energy_usage_sync_batch.id),
                    failed=GREATEST(failed,requested-succeeded),status='FAILED',
                    error_message=CONCAT_WS('\n',NULLIF(error_message,''),'服务重启导致同步中断，请重新执行'),
                    completed_at=COALESCE(completed_at,CURRENT_TIMESTAMP(3))
                WHERE status='RUNNING'""");
    }

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

    public List<Map<String, Object>> syncExclusions() {
        return jdbc.queryForList("""
                SELECT e.platform_device_id,
                       COALESCE(NULLIF(d.device_name,''),e.device_name) device_name,
                       d.comm_address,d.serial_number,d.product_id,d.product_name,
                       d.energy_type,d.energy_type_name,d.comm_status,d.last_acq_time,
                       e.reason,e.enabled,e.created_at,e.updated_at
                FROM energy_usage_sync_exclusion e
                LEFT JOIN platform_device_current d ON d.platform_device_id=e.platform_device_id
                ORDER BY e.enabled DESC,e.updated_at DESC,e.platform_device_id
                """);
    }

    @Transactional
    public int addSyncExclusions(UsageSyncExclusionRequest request) {
        Set<String> deviceIds = new LinkedHashSet<>();
        for (String deviceId : request.deviceIds()) {
            if (deviceId != null && !deviceId.isBlank()) deviceIds.add(deviceId.trim());
        }
        if (deviceIds.isEmpty()) throw BizException.badRequest("请选择需要过滤的设备");
        String reason = blank(request.reason()) ? "离线设备，暂停用量同步" : request.reason().trim();
        List<String> missing = new ArrayList<>();
        for (String deviceId : deviceIds) {
            List<String> names = jdbc.query("SELECT device_name FROM platform_device_current WHERE platform_device_id=?",
                    (rs, rowNum) -> rs.getString(1), deviceId);
            if (names.isEmpty()) {
                missing.add(deviceId);
                continue;
            }
            jdbc.update("""
                    INSERT INTO energy_usage_sync_exclusion(platform_device_id,device_name,reason,enabled)
                    VALUES(?,?,?,1)
                    ON DUPLICATE KEY UPDATE device_name=VALUES(device_name),reason=VALUES(reason),enabled=1,
                      updated_at=CURRENT_TIMESTAMP(3)
                    """, deviceId, names.get(0), reason);
        }
        if (!missing.isEmpty()) throw BizException.badRequest("设备不存在：" + String.join("、", missing));
        return deviceIds.size();
    }

    @Transactional
    public void updateSyncExclusion(String deviceId, UsageSyncExclusionUpdateRequest request) {
        int affected = jdbc.update("""
                UPDATE energy_usage_sync_exclusion
                SET enabled=?,reason=COALESCE(NULLIF(?,''),reason),updated_at=CURRENT_TIMESTAMP(3)
                WHERE platform_device_id=?
                """, Boolean.TRUE.equals(request.enabled()) ? 1 : 0,
                request.reason() == null ? null : request.reason().trim(), deviceId);
        if (affected == 0) {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM energy_usage_sync_exclusion WHERE platform_device_id=?", Integer.class, deviceId);
            if (existing == null || existing == 0) throw BizException.badRequest("过滤名单设备不存在");
        }
    }

    @Transactional
    public void deleteSyncExclusion(String deviceId) {
        if (jdbc.update("DELETE FROM energy_usage_sync_exclusion WHERE platform_device_id=?", deviceId) == 0) {
            throw BizException.badRequest("过滤名单设备不存在");
        }
    }

    public Map<String, Object> sync(UsageSyncRequest request) {
        if (!syncInProgress.compareAndSet(false, true)) {
            throw BizException.badRequest("已有用量同步正在执行，请等待当前批次完成");
        }
        try {
            return executeSync(request);
        } finally {
            syncInProgress.set(false);
        }
    }

    private Map<String, Object> executeSync(UsageSyncRequest request) {
        validateRange(request.startDate(), request.endDate());
        List<PlatformDeviceCurrent> selected = selectDevices(request.groupId(), request.deviceIds());
        if (selected.isEmpty()) throw BizException.badRequest("没有符合条件的设备");
        Set<String> excludedIds = enabledSyncExclusionIds();
        List<PlatformDeviceCurrent> current = selected.stream()
                .filter(device -> !excludedIds.contains(device.getPlatformDeviceId())).toList();
        int excludedDevices = selected.size() - current.size();
        if (current.isEmpty()) throw BizException.badRequest("所选设备均在同步过滤名单中，共 " + excludedDevices + " 台");
        String batchNo = "EU" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        jdbc.update("INSERT INTO energy_usage_sync_batch(batch_no,start_date,end_date,group_id,requested,excluded_devices,status,started_at) VALUES(?,?,?,?,?,?,'RUNNING',?)",
                batchNo, request.startDate(), request.endDate(), request.groupId(), current.size(), excludedDevices, LocalDateTime.now());
        Long batchId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        int succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try {
            var connection = configService.connect();
            Map<MetricKey, List<PlatformDevice>> groups = new LinkedHashMap<>();
            for (PlatformDeviceCurrent d : current) {
                try {
                    String energyType = effectiveEnergyType(d);
                    Metric metric = resolveMetric(d,energyType);
                    groups.computeIfAbsent(new MetricKey(metric,energyType,d.getProductId(),d.getProductName()), k -> new ArrayList<>()).add(toPlatformDevice(d,energyType));
                } catch (Exception ex) {
                    failed++; errors.add(label(d) + ": " + ex.getMessage());
                }
            }
            for (var entry : groups.entrySet()) {
                List<PlatformDevice> devices = entry.getValue();
                Metric metric = entry.getKey().metric();
                for (int offset = 0; offset < devices.size(); offset += 50) {
                    List<PlatformDevice> batch = devices.subList(offset, Math.min(offset + 50, devices.size()));
                    BatchResult batchResult=syncDeviceBatch(connection,batch,entry.getKey(),request.startDate(),request.endDate(),batchId,offset/50+1,new SplitBudget(12));
                    succeeded+=batchResult.succeeded();failed+=batchResult.failed();errors.addAll(batchResult.errors());
                    updateSyncProgress(batchId,succeeded,failed);
                }
            }
            int monthlyPoints=countMonthlyPoints(batchId),dailyPoints=countDailyPoints(batchId),purged=archiveAndPurgeDaily(batchId);
            String status = failed == 0 ? "SUCCESS" : (succeeded > 0 ? "PARTIAL" : "FAILED");
            jdbc.update("UPDATE energy_usage_sync_batch SET succeeded=?,failed=?,monthly_points=?,daily_points=?,purged_daily_points=?,status=?,error_message=?,completed_at=? WHERE id=?",
                    succeeded, failed,monthlyPoints,dailyPoints,purged,status, truncate(String.join("\n", errors), 4000), LocalDateTime.now(), batchId);
            Map<String,Object> result=new LinkedHashMap<>();result.put("batchNo",batchNo);result.put("status",status);result.put("selected",selected.size());result.put("requested",current.size());result.put("excludedDevices",excludedDevices);result.put("succeeded",succeeded);result.put("failed",failed);result.put("monthlyPoints",monthlyPoints);result.put("dailyPoints",dailyPoints);result.put("purgedDailyPoints",purged);result.put("dailyRetentionStart",dailyRetentionStart());return result;
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
        boolean daily="WEEK".equals(normalizedPeriod);String sourceTable=daily?"energy_usage_daily":"energy_usage_monthly";String dateField=daily?"u.usage_date":"u.usage_month";
        if(daily&&start.isBefore(dailyRetentionStart()))start=dailyRetentionStart();
        AnalysisFilter current = analysisFilter(start, end, groupId, deviceId, dimensionId, dimensionValueId,sourceTable,dateField);
        String bucket = bucketExpression(normalizedPeriod,dateField);
        List<Map<String, Object>> trend = jdbc.queryForList("""
                SELECT %s period_start,
                       SUM(CASE WHEN UPPER(u.energy_type)='ELECTRIC' THEN COALESCE(u.usage_value,0) ELSE 0 END) electric_usage,
                       SUM(CASE WHEN UPPER(u.energy_type)='WATER' THEN COALESCE(u.usage_value,0) ELSE 0 END) water_usage,
                       COUNT(DISTINCT CASE WHEN UPPER(u.energy_type)='ELECTRIC' THEN u.platform_device_id END) electric_devices,
                       COUNT(DISTINCT CASE WHEN UPPER(u.energy_type)='WATER' THEN u.platform_device_id END) water_devices
                FROM %s u LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                WHERE %s BETWEEN ? AND ? %s
                GROUP BY period_start ORDER BY period_start
                """.formatted(bucket,sourceTable,current.joins(),dateField,current.sql()), current.args().toArray());
        List<Map<String, Object>> ranking = jdbc.queryForList("""
                SELECT u.platform_device_id,d.device_name,g.group_name,UPPER(u.energy_type) energy_type,
                       SUM(COALESCE(u.usage_value,0)) total_usage,AVG(COALESCE(u.usage_value,0)) avg_period_usage,
                       MAX(COALESCE(u.usage_value,0)) peak_period_usage,COUNT(DISTINCT %s) covered_periods
                FROM %s u JOIN platform_device_current d ON d.platform_device_id=u.platform_device_id
                LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                LEFT JOIN device_group g ON g.id=a.group_id WHERE %s BETWEEN ? AND ? %s
                GROUP BY u.platform_device_id,d.device_name,g.group_name,u.energy_type ORDER BY energy_type,total_usage DESC
                """.formatted(dateField,sourceTable,current.joins(),dateField,current.sql()), current.args().toArray());
        List<Map<String,Object>> breakdown = dimensionId == null ? List.of() : dimensionBreakdown(start,end,normalizedPeriod,dimensionId,dimensionValueId,deviceId);
        long rangeDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(rangeDays - 1);
        AnalysisFilter previous = analysisFilter(previousStart, previousEnd, groupId, deviceId, dimensionId, dimensionValueId,sourceTable,dateField);
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

    private AnalysisFilter analysisFilter(LocalDate start, LocalDate end, Long groupId, String deviceId, Long dimensionId, Long dimensionValueId,String table,String dateField) {
        List<Object> args = new ArrayList<>(List.of(start, end));String sql = "",joins="";
        if (groupId != null) { sql += " AND a.group_id=?";args.add(groupId); }
        if (deviceId != null && !deviceId.isBlank()) { sql += " AND u.platform_device_id=?";args.add(deviceId.trim()); }
        if(dimensionId!=null){joins=" JOIN device_dimension_assignment da ON da.platform_device_id=u.platform_device_id AND "+dateField+">=da.effective_from AND (da.effective_to IS NULL OR "+dateField+"<=da.effective_to) ";sql+=" AND da.dimension_id=?";args.add(dimensionId);if(dimensionValueId!=null){sql+=" AND da.dimension_value_id=?";args.add(dimensionValueId);}}
        return new AnalysisFilter(table,dateField,joins, sql, args);
    }

    private Map<String, BigDecimal> totals(AnalysisFilter filter) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT UPPER(u.energy_type) energy_type,SUM(COALESCE(u.usage_value,0)) total_usage
                FROM %s u LEFT JOIN device_group_assignment a ON a.platform_device_id=u.platform_device_id %s
                WHERE %s BETWEEN ? AND ? %s GROUP BY UPPER(u.energy_type)
                """.formatted(filter.table(),filter.joins(),filter.dateField(),filter.sql()), filter.args().toArray());
        Map<String, BigDecimal> result = new HashMap<>();
        rows.forEach(v -> result.put(String.valueOf(v.get("energy_type")), decimal(v.get("total_usage"))));return result;
    }

    private String bucketExpression(String period,String dateField) {
        return switch (period) {
            case "WEEK" -> "DATE_SUB("+dateField+", INTERVAL WEEKDAY("+dateField+") DAY)";
            case "QUARTER" -> "STR_TO_DATE(CONCAT(YEAR("+dateField+"),'-',LPAD((QUARTER("+dateField+")-1)*3+1,2,'0'),'-01'),'%Y-%m-%d')";
            case "YEAR" -> "MAKEDATE(YEAR("+dateField+"),1)";
            default -> "DATE_SUB("+dateField+", INTERVAL DAYOFMONTH("+dateField+")-1 DAY)";
        };
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO : null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, java.math.RoundingMode.HALF_UP);
    }

    private List<Map<String,Object>> dimensionBreakdown(LocalDate start,LocalDate end,String period,Long dimensionId,Long valueId,String deviceId){
        String valueFilter=valueId==null?"":" AND x.dimension_value_id="+valueId;String deviceFilter=deviceId==null||deviceId.isBlank()?"":" AND x.platform_device_id=?";
        List<Object> args=new ArrayList<>();Collections.addAll(args,start.withDayOfMonth(1),end.withDayOfMonth(1),dimensionId,dimensionId,start,end,dimensionId,start.withDayOfMonth(1),end.withDayOfMonth(1));if(!deviceFilter.isEmpty())args.add(deviceId.trim());
        String bucket=bucketExpression(period,"x.usage_date");
        return jdbc.queryForList("""
          SELECT %s period_start,v.id dimension_value_id,v.value_name dimension_value_name,v.color,
            SUM(CASE WHEN UPPER(x.energy_type)='ELECTRIC' THEN x.usage_value ELSE 0 END) electric_usage,
            SUM(CASE WHEN UPPER(x.energy_type)='WATER' THEN x.usage_value ELSE 0 END) water_usage
          FROM (
            SELECT s.platform_device_id,s.usage_month usage_date,s.energy_type,s.usage_value,s.dimension_value_id
            FROM energy_usage_monthly_dimension s WHERE s.usage_month BETWEEN ? AND ? AND s.dimension_id=?
            UNION ALL
            SELECT d.platform_device_id,d.usage_date,d.energy_type,COALESCE(d.usage_value,0),a.dimension_value_id
            FROM energy_usage_daily d JOIN device_dimension_assignment a ON a.platform_device_id=d.platform_device_id AND a.dimension_id=? AND d.usage_date>=a.effective_from AND (a.effective_to IS NULL OR d.usage_date<=a.effective_to)
            WHERE d.usage_date BETWEEN ? AND ? AND NOT EXISTS(SELECT 1 FROM energy_usage_monthly_dimension s WHERE s.platform_device_id=d.platform_device_id AND s.usage_month=DATE_SUB(d.usage_date,INTERVAL DAYOFMONTH(d.usage_date)-1 DAY) AND s.identifier=d.identifier AND s.dimension_id=a.dimension_id)
            UNION ALL
            SELECT m.platform_device_id,m.usage_month,m.energy_type,COALESCE(m.usage_value,0),a.dimension_value_id
            FROM energy_usage_monthly m JOIN device_dimension_assignment a ON a.platform_device_id=m.platform_device_id AND a.dimension_id=? AND LAST_DAY(m.usage_month)>=a.effective_from AND (a.effective_to IS NULL OR LAST_DAY(m.usage_month)<=a.effective_to)
            WHERE m.usage_month BETWEEN ? AND ?
              AND NOT EXISTS(SELECT 1 FROM energy_usage_monthly_dimension s WHERE s.platform_device_id=m.platform_device_id AND s.usage_month=m.usage_month AND s.identifier=m.identifier AND s.dimension_id=a.dimension_id)
              AND NOT EXISTS(SELECT 1 FROM energy_usage_daily d WHERE d.platform_device_id=m.platform_device_id AND d.identifier=m.identifier AND d.usage_date BETWEEN m.usage_month AND LAST_DAY(m.usage_month))
          ) x JOIN analysis_dimension_value v ON v.id=x.dimension_value_id
          WHERE 1=1 %s %s GROUP BY period_start,v.id,v.value_name,v.color ORDER BY period_start,v.sort_order,v.id
          """.formatted(bucket,valueFilter,deviceFilter),args.toArray());
    }

    private void syncMonthlyChunks(PlatformConfigService.PlatformConnection c,List<PlatformDevice> devices,String energyType,Metric metric,LocalDate start,LocalDate end,Long batchId){LocalDate cursor=start.withDayOfMonth(1);while(!cursor.isAfter(end)){LocalDate yearEnd=LocalDate.of(cursor.getYear(),12,31);LocalDate chunkEnd=yearEnd.isAfter(end)?end:yearEnd;List<PlatformMonthlyUsage> points=platformAdapter.fetchMonthlyUsage(c.baseUrl(),c.token(),devices,energyType,metric.identifier(),metric.name(),cursor,chunkEnd);upsertMonthlyPoints(points,energyType,batchId);cursor=LocalDate.of(cursor.getYear()+1,1,1);}}
    private BatchResult syncDeviceBatch(PlatformConfigService.PlatformConnection c,List<PlatformDevice> devices,MetricKey key,LocalDate start,LocalDate end,Long batchId,int batchNo,SplitBudget budget){try{syncMonthlyChunks(c,devices,key.energyType(),key.metric(),start,end,batchId);LocalDate dailyStart=start.isAfter(dailyRetentionStart())?start:dailyRetentionStart();LocalDate dailyEnd=end.isBefore(LocalDate.now())?end:LocalDate.now();if(!dailyEnd.isBefore(dailyStart))syncDateChunks(c,devices,key.energyType(),key.metric(),dailyStart,dailyEnd,batchId);return new BatchResult(devices.size(),0,List.of());}catch(Exception ex){if(devices.size()>1&&budget.consume()){int middle=devices.size()/2;BatchResult left=syncDeviceBatch(c,devices.subList(0,middle),key,start,end,batchId,batchNo,budget);BatchResult right=syncDeviceBatch(c,devices.subList(middle,devices.size()),key,start,end,batchId,batchNo,budget);List<String> combined=new ArrayList<>(left.errors());combined.addAll(right.errors());return new BatchResult(left.succeeded()+right.succeeded(),left.failed()+right.failed(),combined);}String suffix=devices.size()==1?"，设备 "+devices.get(0).getDeviceName()+"（"+devices.get(0).getPlatformDeviceId()+"）":"，设备批次 "+batchNo+"（"+devices.size()+" 台）";return new BatchResult(0,devices.size(),List.of("产品 "+productLabel(key)+suffix+": "+ex.getMessage()));}}
    private void upsertMonthlyPoints(List<PlatformMonthlyUsage> points,String energyType,Long batchId){if(points.isEmpty())return;LocalDateTime syncedAt=LocalDateTime.now();transactionTemplate.executeWithoutResult(status->jdbc.batchUpdate("""
      INSERT INTO energy_usage_monthly(platform_device_id,usage_month,energy_type,identifier,identifier_name,usage_value,sync_batch_id,synced_at,raw_json)
      VALUES(?,?,?,?,?,?,?,?,CAST(? AS JSON)) ON DUPLICATE KEY UPDATE energy_type=VALUES(energy_type),identifier_name=VALUES(identifier_name),usage_value=VALUES(usage_value),sync_batch_id=VALUES(sync_batch_id),synced_at=VALUES(synced_at),raw_json=VALUES(raw_json)
      """,points,500,(ps,p)->{ps.setString(1,p.platformDeviceId());ps.setObject(2,p.usageMonth());ps.setString(3,energyType);ps.setString(4,p.identifier());ps.setString(5,p.identifierName());ps.setBigDecimal(6,p.value());ps.setLong(7,batchId);ps.setObject(8,syncedAt);ps.setString(9,p.rawJson());}));}
    private LocalDate dailyRetentionStart(){return LocalDate.now().withDayOfMonth(1).minusMonths(2);}
    private int archiveAndPurgeDaily(Long batchId){LocalDate cutoff=dailyRetentionStart();Integer purged=transactionTemplate.execute(status->{jdbc.update("""
      INSERT INTO energy_usage_monthly(platform_device_id,usage_month,energy_type,identifier,identifier_name,usage_value,sync_batch_id,synced_at,raw_json)
      SELECT u.platform_device_id,DATE_SUB(u.usage_date,INTERVAL DAYOFMONTH(u.usage_date)-1 DAY),u.energy_type,u.identifier,MAX(u.identifier_name),SUM(COALESCE(u.usage_value,0)),?,CURRENT_TIMESTAMP(3),JSON_OBJECT('source','daily-backfill')
      FROM energy_usage_daily u WHERE u.usage_date<? GROUP BY u.platform_device_id,DATE_SUB(u.usage_date,INTERVAL DAYOFMONTH(u.usage_date)-1 DAY),u.energy_type,u.identifier
      ON DUPLICATE KEY UPDATE platform_device_id=VALUES(platform_device_id)
      """,batchId,cutoff);jdbc.update("""
      INSERT INTO energy_usage_monthly_dimension(platform_device_id,usage_month,energy_type,identifier,dimension_id,dimension_value_id,usage_value)
      SELECT u.platform_device_id,DATE_SUB(u.usage_date,INTERVAL DAYOFMONTH(u.usage_date)-1 DAY),u.energy_type,u.identifier,a.dimension_id,a.dimension_value_id,SUM(COALESCE(u.usage_value,0))
      FROM energy_usage_daily u JOIN device_dimension_assignment a ON a.platform_device_id=u.platform_device_id AND u.usage_date>=a.effective_from AND (a.effective_to IS NULL OR u.usage_date<=a.effective_to)
      WHERE u.usage_date<? GROUP BY u.platform_device_id,DATE_SUB(u.usage_date,INTERVAL DAYOFMONTH(u.usage_date)-1 DAY),u.energy_type,u.identifier,a.dimension_id,a.dimension_value_id
      ON DUPLICATE KEY UPDATE usage_value=VALUES(usage_value),updated_at=CURRENT_TIMESTAMP(3)
      """,cutoff);return jdbc.update("DELETE FROM energy_usage_daily WHERE usage_date<?",cutoff);});return purged==null?0:purged;}

    private void syncDateChunks(PlatformConfigService.PlatformConnection c, List<PlatformDevice> devices,
                                String energyType, Metric metric, LocalDate start, LocalDate end, Long batchId) {
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate chunkEnd = YearMonth.from(cursor).atEndOfMonth();
            if (chunkEnd.isAfter(end)) chunkEnd = end;
            List<PlatformDailyUsage> points = platformAdapter.fetchDailyUsage(c.baseUrl(), c.token(), devices,
                    energyType, metric.identifier(), metric.name(), cursor, chunkEnd);
            upsertDailyPoints(points, energyType, metric, batchId);
            cursor = chunkEnd.plusDays(1);
        }
    }

    private void upsertDailyPoints(List<PlatformDailyUsage> points, String energyType, Metric metric, Long batchId) {
        if(points.isEmpty())return;LocalDateTime syncedAt=LocalDateTime.now();transactionTemplate.executeWithoutResult(status->jdbc.batchUpdate("""
                INSERT INTO energy_usage_daily(platform_device_id,usage_date,energy_type,identifier,identifier_name,
                  usage_value,meter_reading,value_mode,sync_batch_id,synced_at,raw_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE energy_type=VALUES(energy_type),identifier_name=VALUES(identifier_name),
                  usage_value=VALUES(usage_value),meter_reading=VALUES(meter_reading),value_mode=VALUES(value_mode),
                  sync_batch_id=VALUES(sync_batch_id),synced_at=VALUES(synced_at),raw_json=VALUES(raw_json)""",
                points,500,(ps,p)->{BigDecimal usage="READING".equals(metric.mode())?null:p.value();BigDecimal reading="READING".equals(metric.mode())?p.value():null;ps.setString(1,p.platformDeviceId());ps.setObject(2,p.usageDate());ps.setString(3,energyType);ps.setString(4,p.identifier());ps.setString(5,p.identifierName());ps.setBigDecimal(6,usage);ps.setBigDecimal(7,reading);ps.setString(8,metric.mode());ps.setLong(9,batchId);ps.setObject(10,syncedAt);ps.setString(11,p.rawJson());}));
    }

    private Metric resolveMetric(PlatformDeviceCurrent d,String energyType) {
        if (blank(d.getProductId()) || blank(d.getCommAddress())) throw new IllegalArgumentException("缺少 productId 或通讯地址");
        if ("ELECTRIC".equals(energyType)) return new Metric("11X", "正向有功电能", "USAGE");
        if ("WATER".equals(energyType)) return new Metric("totalFlow", "累计流量", "USAGE");
        if ("GAS".equals(energyType)) return new Metric("totalFlow", "累计用气量", "USAGE");
        throw new IllegalArgumentException("无法自动识别设备能源类型");
    }

    private List<PlatformDeviceCurrent> selectDevices(Long groupId, List<String> ids) {
        var q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlatformDeviceCurrent>();
        if (ids != null && !ids.isEmpty()) q.in("platform_device_id", ids);
        if (groupId != null) q.exists("SELECT 1 FROM device_group_assignment a WHERE a.platform_device_id=platform_device_current.platform_device_id AND a.group_id=" + groupId);
        return deviceMapper.selectList(q);
    }

    private Set<String> enabledSyncExclusionIds() {
        return new HashSet<>(jdbc.queryForList(
                "SELECT platform_device_id FROM energy_usage_sync_exclusion WHERE enabled=1", String.class));
    }

    private String effectiveEnergyType(PlatformDeviceCurrent d){String product=Optional.ofNullable(d.getProductName()).orElse("").toUpperCase(Locale.ROOT);String productId=Optional.ofNullable(d.getProductId()).orElse("");if(Set.of("437","438").contains(productId)||product.contains("水表"))return "WATER";if(Set.of("436","608").contains(productId)||product.contains("DTSU")||product.contains("DDSU")||product.contains("电表"))return "ELECTRIC";if(product.contains("燃气")||product.contains("气表"))return "GAS";String source=Optional.ofNullable(d.getEnergyType()).orElse("").toUpperCase(Locale.ROOT);return Set.of("ELECTRIC","WATER","GAS").contains(source)?source:"OTHER";}
    private String productLabel(MetricKey key){return (blank(key.productName())?"未知产品":key.productName())+"（"+key.productId()+"）";}
    private PlatformDevice toPlatformDevice(PlatformDeviceCurrent d,String energyType) {
        return PlatformDevice.builder().platformDeviceId(d.getPlatformDeviceId()).deviceName(d.getDeviceName())
                .commAddress(d.getCommAddress()).productId(d.getProductId()).productName(d.getProductName())
                .energyType(energyType).energyTypeName(d.getEnergyTypeName()).build();
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw BizException.badRequest("日期范围无效");
        if (start.plusYears(2).isBefore(end)) throw BizException.badRequest("单次查询范围不能超过两年");
    }
    private void validateAnalysisRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw BizException.badRequest("日期范围无效");
        if (start.plusYears(10).isBefore(end)) throw BizException.badRequest("分析范围不能超过十年");
    }
    private int countDailyPoints(Long batchId) { return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM energy_usage_daily WHERE sync_batch_id=?", Integer.class, batchId)).orElse(0); }
    private int countMonthlyPoints(Long batchId) { return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM energy_usage_monthly WHERE sync_batch_id=?", Integer.class, batchId)).orElse(0); }
    private void updateSyncProgress(Long batchId,int succeeded,int failed){jdbc.update("UPDATE energy_usage_sync_batch SET succeeded=?,failed=?,monthly_points=?,daily_points=? WHERE id=?",succeeded,failed,countMonthlyPoints(batchId),countDailyPoints(batchId),batchId);}
    private int valueOrZero(Integer v) { return v == null ? 0 : v; }
    private String defaultColor(String v) { return blank(v) ? "#409EFF" : v; }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String label(PlatformDeviceCurrent d) { return blank(d.getDeviceName()) ? d.getPlatformDeviceId() : d.getDeviceName(); }
    private String truncate(String v, int n) { return v == null || v.length() <= n ? v : v.substring(0, n); }
    private BigDecimal decimal(Object v) { return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()); }
    private record Metric(String identifier, String name, String mode) {}
    private record MetricKey(Metric metric, String energyType,String productId,String productName) {}
    private record BatchResult(int succeeded,int failed,List<String> errors) {}
    private static final class SplitBudget{private int remaining;private SplitBudget(int remaining){this.remaining=remaining;}private boolean consume(){if(remaining<=0)return false;remaining--;return true;}}
    private record AnalysisFilter(String table,String dateField,String joins, String sql, List<Object> args) {}
}
