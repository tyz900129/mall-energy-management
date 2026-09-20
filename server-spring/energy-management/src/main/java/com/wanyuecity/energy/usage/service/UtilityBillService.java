package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.usage.service.dto.UtilityBillRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UtilityBillService {
    private static final Set<String> PERIODS = Set.of("MONTH", "QUARTER", "HALF_YEAR", "YEAR");
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> list(String startMonth, String endMonth) {
        YearMonth start = blank(startMonth) ? null : parseMonth(startMonth);
        YearMonth end = blank(endMonth) ? null : parseMonth(endMonth);
        if (start != null && end != null) validateRange(start, end);
        StringBuilder sql = new StringBuilder("""
                SELECT id,DATE_FORMAT(bill_month,'%Y-%m') bill_month,electric_amount,electric_usage,
                       water_amount,water_usage,created_by,updated_by,created_at,updated_at
                FROM energy_utility_bill WHERE 1=1""");
        List<Object> args = new ArrayList<>();
        if (start != null) { sql.append(" AND bill_month>=?"); args.add(start.atDay(1)); }
        if (end != null) { sql.append(" AND bill_month<=?"); args.add(end.atDay(1)); }
        sql.append(" ORDER BY bill_month DESC LIMIT 240");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> create(UtilityBillRequest request, String operator) {
        YearMonth month = requestMonth(request);
        try {
            jdbc.update("""
                    INSERT INTO energy_utility_bill(bill_month,electric_amount,electric_usage,water_amount,water_usage,created_by,updated_by)
                    VALUES(?,?,?,?,?,?,?)""", month.atDay(1), request.electricAmount(), request.electricUsage(),
                    request.waterAmount(), request.waterUsage(), operator(operator), operator(operator));
        } catch (DuplicateKeyException ex) {
            throw BizException.badRequest(month + " 账单已存在，请使用编辑功能");
        }
        return findByMonth(month);
    }

    @Transactional
    public Map<String, Object> update(Long id, UtilityBillRequest request, String operator) {
        YearMonth month = requestMonth(request);
        try {
            int changed = jdbc.update("""
                    UPDATE energy_utility_bill SET bill_month=?,electric_amount=?,electric_usage=?,water_amount=?,
                      water_usage=?,updated_by=? WHERE id=?""", month.atDay(1), request.electricAmount(),
                    request.electricUsage(), request.waterAmount(), request.waterUsage(), operator(operator), id);
            if (changed == 0) throw BizException.badRequest("账单记录不存在");
        } catch (DuplicateKeyException ex) {
            throw BizException.badRequest(month + " 账单已存在");
        }
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (jdbc.update("DELETE FROM energy_utility_bill WHERE id=?", id) == 0) {
            throw BizException.badRequest("账单记录不存在");
        }
    }

    public List<TrendRow> trend(String startMonth, String endMonth, String period) {
        YearMonth start = parseMonth(startMonth), end = parseMonth(endMonth);
        validateRange(start, end);
        String normalized = normalizePeriod(period);
        SortedSet<YearMonth> requestedStarts = new TreeSet<>();
        for (YearMonth cursor = start; !cursor.isAfter(end); cursor = cursor.plusMonths(1)) {
            requestedStarts.add(periodWindow(cursor, normalized).start());
        }
        if (requestedStarts.isEmpty()) return List.of();
        PeriodWindow first = periodWindow(requestedStarts.first(), normalized);
        PeriodWindow last = periodWindow(requestedStarts.last(), normalized);
        LocalDate queryStart = first.start().minusYears(1).atDay(1);
        LocalDate queryEnd = last.end().atEndOfMonth();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bill_month,electric_amount,electric_usage,water_amount,water_usage
                FROM energy_utility_bill WHERE bill_month BETWEEN ? AND ? ORDER BY bill_month""", queryStart, queryEnd);
        Map<YearMonth, Totals> grouped = new HashMap<>();
        for (Map<String, Object> row : rows) {
            YearMonth month = databaseMonth(row.get("bill_month"));
            YearMonth key = periodWindow(month, normalized).start();
            grouped.computeIfAbsent(key, ignored -> new Totals()).add(row);
        }
        List<TrendRow> result = new ArrayList<>();
        for (YearMonth key : requestedStarts) {
            PeriodWindow window = periodWindow(key, normalized);
            Totals current = grouped.get(key), previous = grouped.get(key.minusYears(1));
            result.add(new TrendRow(key.toString(), window.label(), window.expectedMonths(),
                    current == null ? 0 : current.months, previous == null ? 0 : previous.months,
                    current == null ? null : current.electricAmount, previous == null ? null : previous.electricAmount,
                    current == null ? null : current.electricUsage, previous == null ? null : previous.electricUsage,
                    current == null ? null : current.waterAmount, previous == null ? null : previous.waterAmount,
                    current == null ? null : current.waterUsage, previous == null ? null : previous.waterUsage));
        }
        return result;
    }

    static PeriodWindow periodWindow(YearMonth month, String period) {
        String normalized = normalizePeriod(period);
        if ("MONTH".equals(normalized)) {
            return new PeriodWindow(month, month, month.getYear() + "年" + month.getMonthValue() + "月", 1);
        }
        int fiscalYear = month.getMonthValue() >= 3 ? month.getYear() : month.getYear() - 1;
        if ("YEAR".equals(normalized)) {
            return new PeriodWindow(YearMonth.of(fiscalYear, 3), YearMonth.of(fiscalYear + 1, 2),
                    fiscalYear + "年度（3月—次年2月）", 12);
        }
        if ("HALF_YEAR".equals(normalized)) {
            boolean first = month.getMonthValue() >= 3 && month.getMonthValue() <= 8;
            YearMonth start = YearMonth.of(fiscalYear, first ? 3 : 9);
            YearMonth end = first ? YearMonth.of(fiscalYear, 8) : YearMonth.of(fiscalYear + 1, 2);
            String label = first ? fiscalYear + "年度上半年（3—8月）" : fiscalYear + "年度下半年（9月—次年2月）";
            return new PeriodWindow(start, end, label, 6);
        }
        int monthValue = month.getMonthValue();
        int quarter = monthValue >= 3 && monthValue <= 5 ? 1 : monthValue >= 6 && monthValue <= 8 ? 2 : monthValue >= 9 && monthValue <= 11 ? 3 : 4;
        YearMonth quarterStart = YearMonth.of(fiscalYear, new int[]{3, 6, 9, 12}[quarter - 1]);
        YearMonth quarterEnd = quarterStart.plusMonths(2);
        String range = quarter == 1 ? "3—5月" : quarter == 2 ? "6—8月" : quarter == 3 ? "9—11月" : "12月—次年2月";
        return new PeriodWindow(quarterStart, quarterEnd, fiscalYear + "年度第" + quarter + "季度（" + range + "）", 3);
    }

    static String normalizePeriod(String value) {
        String normalized = value == null ? "MONTH" : value.trim().toUpperCase(Locale.ROOT);
        if (!PERIODS.contains(normalized)) throw BizException.badRequest("账单周期仅支持月、季度、半年和年度");
        return normalized;
    }

    private Map<String, Object> findByMonth(YearMonth month) {
        return jdbc.queryForMap("""
                SELECT id,DATE_FORMAT(bill_month,'%Y-%m') bill_month,electric_amount,electric_usage,
                       water_amount,water_usage,created_by,updated_by,created_at,updated_at
                FROM energy_utility_bill WHERE bill_month=?""", month.atDay(1));
    }

    private Map<String, Object> findById(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,DATE_FORMAT(bill_month,'%Y-%m') bill_month,electric_amount,electric_usage,
                       water_amount,water_usage,created_by,updated_by,created_at,updated_at
                FROM energy_utility_bill WHERE id=?""", id);
        if (rows.isEmpty()) throw BizException.badRequest("账单记录不存在");
        return rows.get(0);
    }

    private YearMonth requestMonth(UtilityBillRequest request) {
        YearMonth month = parseMonth(request.billMonth());
        if (month.isAfter(YearMonth.now())) throw BizException.badRequest("不能录入未来月份的账单");
        return month;
    }

    private static YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) { throw BizException.badRequest("账单月份格式应为YYYY-MM"); }
    }

    private void validateRange(YearMonth start, YearMonth end) {
        if (end.isBefore(start)) throw BizException.badRequest("账单月份范围无效");
        if (ChronoUnit.MONTHS.between(start, end) > 120) throw BizException.badRequest("账单查询范围不能超过10年");
    }

    private YearMonth databaseMonth(Object value) {
        if (value instanceof LocalDate date) return YearMonth.from(date);
        if (value instanceof Date date) return YearMonth.from(date.toLocalDate());
        return YearMonth.parse(String.valueOf(value).substring(0, 7));
    }

    private String operator(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 100)); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }

    static record PeriodWindow(YearMonth start, YearMonth end, String label, int expectedMonths) {}
    public record TrendRow(String periodStart, String periodLabel, int expectedMonths, int currentMonths, int previousMonths,
                           BigDecimal electricAmount, BigDecimal previousElectricAmount,
                           BigDecimal electricUsage, BigDecimal previousElectricUsage,
                           BigDecimal waterAmount, BigDecimal previousWaterAmount,
                           BigDecimal waterUsage, BigDecimal previousWaterUsage) {}

    private static final class Totals {
        private int months;
        private BigDecimal electricAmount = BigDecimal.ZERO, electricUsage = BigDecimal.ZERO;
        private BigDecimal waterAmount = BigDecimal.ZERO, waterUsage = BigDecimal.ZERO;
        private void add(Map<String, Object> row) {
            months++;
            electricAmount = electricAmount.add(decimal(row.get("electric_amount")));
            electricUsage = electricUsage.add(decimal(row.get("electric_usage")));
            waterAmount = waterAmount.add(decimal(row.get("water_amount")));
            waterUsage = waterUsage.add(decimal(row.get("water_usage")));
        }
    }
}
