package com.wanyuecity.energy.complaint.service;

import com.wanyuecity.energy.complaint.service.dto.ComplaintStatsQuery;
import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** 客诉多维度报表。 */
@Service
@RequiredArgsConstructor
public class ComplaintStatsService {

    private final JdbcTemplate jdbc;

    public Map<String, Object> overview(ComplaintStatsQuery q) {
        Filter f = filter(q);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) total,
                       COUNT(DISTINCT CASE WHEN c.stall_id IS NOT NULL THEN CONCAT('S', c.stall_id)
                                           ELSE CONCAT('B', COALESCE(c.brand_id, 0)) END) brandCount,
                       COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) totalCost,
                       COALESCE(SUM(c.company_cost),0) companyCost,
                       COALESCE(SUM(c.merchant_cost),0) merchantCost,
                       COALESCE(SUM(CASE WHEN c.on_site_handle=1 THEN 1 ELSE 0 END),0) onSiteCount,
                       COALESCE(SUM(CASE WHEN c.status='closed' THEN 1 ELSE 0 END),0) closedCount,
                       COALESCE(SUM(CASE WHEN c.status IN ('pending','processing') THEN 1 ELSE 0 END),0) openingCount,
                       COALESCE(SUM(CASE WHEN c.satisfaction='SATISFIED' THEN 1 ELSE 0 END),0) satisfiedCount,
                       COALESCE(ROUND(AVG(c.handle_days),1),0) avgHandleDays
                FROM cs_complaint c """ + f.whereClause(), f.args().toArray());
        long total = number(row.get("total")).longValue();
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("onSiteRate", total == 0 ? 0 : BigDecimal.valueOf(number(row.get("onSiteCount")).doubleValue() / total * 100).setScale(1, RoundingMode.HALF_UP));
        result.put("satisfactionRate", total == 0 ? 0 : BigDecimal.valueOf(number(row.get("satisfiedCount")).doubleValue() / total * 100).setScale(1, RoundingMode.HALF_UP));
        return result;
    }

    /** 单维度分布（类别/业态/楼层/状态/渠道/满意度/品牌/责任人）。 */
    public List<Map<String, Object>> distribution(ComplaintStatsQuery q) {
        String dimension = dimension(q.getDimension());
        if ("manager".equals(dimension) || "handler".equals(dimension)) {
            String type = "manager".equals(dimension) ? "MANAGER" : "HANDLER";
            Filter f = filter(q);
            return jdbc.queryForList("""
                    SELECT p.user_name `key`, COUNT(DISTINCT c.id) value,
                           COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) amount
                    FROM cs_complaint_participant p
                    JOIN cs_complaint c ON c.id=p.complaint_id
                    """ + append(f, "p.participant_type='" + type + "'")
                    + " GROUP BY p.user_id, p.user_name ORDER BY value DESC", f.args().toArray());
        }
        String expr = dimensionExpr(dimension);
        Filter f = filter(q);
        String sql = "SELECT " + expr + " `key`, COUNT(*) value, "
                + "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) amount, "
                + "COALESCE(SUM(c.company_cost),0) companyCost, COALESCE(SUM(c.merchant_cost),0) merchantCost, "
                + "COALESCE(ROUND(AVG(c.handle_days),1),0) avgHandleDays "
                + "FROM cs_complaint c " + DICT_JOINS + f.whereClause()
                + " GROUP BY " + expr + " ORDER BY value DESC";
        return jdbc.queryForList(sql, f.args().toArray());
    }

    public List<Map<String, Object>> trend(ComplaintStatsQuery q) {
        String granularity = q.getGranularity() == null ? "date" : q.getGranularity();
        String format = switch (granularity) {
            case "month" -> "%Y-%m";
            case "week" -> "%x-W%v";
            default -> "%Y-%m-%d";
        };
        Filter f = filter(q);
        String sql = "SELECT DATE_FORMAT(c.occur_date, '" + format + "') `key`, COUNT(*) value, "
                + "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) amount "
                + "FROM cs_complaint c " + f.whereClause() + " GROUP BY `key` ORDER BY `key`";
        return jdbc.queryForList(sql, f.args().toArray());
    }

    /** 交叉分析：两个维度任意组合。 */
    public List<Map<String, Object>> cross(ComplaintStatsQuery q) {
        String dim1 = dimension(q.getDim1());
        String dim2 = dimension(q.getDim2());
        if ("manager".equals(dim1) || "handler".equals(dim1) || "manager".equals(dim2) || "handler".equals(dim2)) {
            throw BizException.badRequest("责任人维度暂不支持交叉分析，请使用排名分析");
        }
        String expr1 = dimensionExpr(dim1);
        String expr2 = dimensionExpr(dim2);
        String metric = q.getMetric();
        String valueExpr = "cost".equals(metric) ? "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0)"
                : "COUNT(*)";
        Filter f = filter(q);
        String sql = "SELECT " + expr1 + " dim1, " + expr2 + " dim2, " + valueExpr + " value "
                + "FROM cs_complaint c " + DICT_JOINS + f.whereClause()
                + " GROUP BY dim1, dim2 ORDER BY value DESC LIMIT 500";
        return jdbc.queryForList(sql, f.args().toArray());
    }

    /** 排名：品牌 / 责任商管 / 经办人。 */
    public List<Map<String, Object>> ranking(ComplaintStatsQuery q) {
        String dimension = dimension(q.getRankingDimension());
        int limit = q.getLimit() == null || q.getLimit() < 1 ? 10 : Math.min(q.getLimit(), 50);
        String metric = q.getMetric();
        String valueExpr = "cost".equals(metric) ? "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0)"
                : "COUNT(DISTINCT c.id)";
        Filter f = filter(q);
        List<Object> args = new ArrayList<>(f.args());
        args.add(limit);
        if ("manager".equals(dimension) || "handler".equals(dimension)) {
            String type = "manager".equals(dimension) ? "MANAGER" : "HANDLER";
            return jdbc.queryForList("""
                    SELECT p.user_name `key`,
                           COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) cost,
                           COUNT(DISTINCT c.id) count, """ + valueExpr + """
                            value
                    FROM cs_complaint_participant p
                    JOIN cs_complaint c ON c.id=p.complaint_id
                    """ + append(f, "p.participant_type='" + type + "'")
                    + " GROUP BY p.user_id, p.user_name ORDER BY value DESC LIMIT ?", args.toArray());
        }
        String expr = dimensionExpr(dimension);
        String sql = "SELECT " + expr + " `key`, "
                + "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) cost, "
                + "COUNT(*) count, " + valueExpr + " value "
                + "FROM cs_complaint c " + DICT_JOINS + f.whereClause()
                + " GROUP BY `key` ORDER BY value DESC LIMIT ?";
        return jdbc.queryForList(sql, args.toArray());
    }

    /** 时效分析：平均/中位处理天数、时效分布、现场解决率、积压量。 */
    public Map<String, Object> efficiency(ComplaintStatsQuery q) {
        Filter f = filter(q);
        List<Integer> days = jdbc.queryForList("SELECT c.handle_days FROM cs_complaint c "
                + append(f, "c.handle_days IS NOT NULL"), Integer.class, f.args().toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        long total = days.size();
        double average = days.stream().mapToInt(Integer::intValue).average().orElse(0);
        int median = 0;
        if (total > 0) {
            List<Integer> sorted = new ArrayList<>(days);
            Collections.sort(sorted);
            median = sorted.get((int) ((sorted.size() - 1) / 2));
        }
        result.put("closedCount", total);
        result.put("avgHandleDays", BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
        result.put("medianHandleDays", median);
        result.put("buckets", jdbc.queryForList("""
                SELECT CASE WHEN c.handle_days=0 THEN '0天'
                            WHEN c.handle_days=1 THEN '1天'
                            WHEN c.handle_days=2 THEN '2天'
                            WHEN c.handle_days BETWEEN 3 AND 5 THEN '3-5天'
                            WHEN c.handle_days BETWEEN 6 AND 10 THEN '6-10天'
                            ELSE '10天以上' END bucket, COUNT(*) value
                FROM cs_complaint c """ + append(f, "c.handle_days IS NOT NULL") + " GROUP BY bucket ORDER BY bucket", f.args().toArray()));
        Map<String, Object> extra = overview(q);
        result.put("onSiteRate", extra.get("onSiteRate"));
        result.put("openingCount", extra.get("openingCount"));
        return result;
    }

    /** 费用分析。 */
    public Map<String, Object> cost(ComplaintStatsQuery q) {
        Filter f = filter(q);
        Map<String, Object> result = new LinkedHashMap<>(jdbc.queryForMap("""
                SELECT COALESCE(SUM(c.company_cost),0) companyCost, COALESCE(SUM(c.merchant_cost),0) merchantCost,
                       COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) totalCost,
                       COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 1 ELSE 0 END),0) noCostCount,
                       COALESCE(SUM(CASE WHEN c.no_cost=0 AND c.company_cost+c.merchant_cost>0 THEN 1 ELSE 0 END),0) costCount
                FROM cs_complaint c """ + f.whereClause(), f.args().toArray()));
        long count = number(result.get("costCount")).longValue();
        BigDecimal total = decimal(result.get("totalCost"));
        result.put("avgCost", count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        result.put("buckets", jdbc.queryForList("""
                SELECT bucket, COUNT(*) value FROM (
                  SELECT CASE WHEN total=0 THEN '0元'
                              WHEN total<100 THEN '100元以下'
                              WHEN total<500 THEN '100-500元'
                              WHEN total<1000 THEN '500-1000元'
                              WHEN total<5000 THEN '1000-5000元'
                              ELSE '5000元以上' END bucket
                  FROM (SELECT CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END total
                        FROM cs_complaint c """ + f.whereClause() + ") x ) t GROUP BY bucket", f.args().toArray()));
        result.put("trend", trend(q));
        return result;
    }

    /**
     * 周期同环比：客诉数量与金额。
     * granularity 支持 week/month/quarter/half；季度按 12-2、3-5、6-8、9-11 划分，半年按 3-8、9-2 划分。
     */
    public Map<String, Object> periodCompare(ComplaintStatsQuery q) {
        String granularity = normalizeGranularity(q.getGranularity());
        LocalDate lastDate = jdbc.queryForObject("SELECT MAX(occur_date) FROM cs_complaint", LocalDate.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("granularity", granularity);
        if (lastDate == null) {
            result.put("periods", List.of());
            return result;
        }
        List<Period> periods;
        if (q.getStart() != null && q.getEnd() != null) {
            periods = periodsBetween(granularity, periodOf(granularity, q.getStart()), periodOf(granularity, q.getEnd()));
        } else {
            int limit = switch (granularity) {
                case "week" -> 12;
                case "month" -> 12;
                case "quarter" -> 8;
                default -> 4;
            };
            periods = periodsEndingAt(granularity, periodOf(granularity, lastDate), limit);
        }
        if (periods.isEmpty()) {
            result.put("periods", List.of());
            return result;
        }
        // 同比需回看一年，环比需回看一期，统一多取一个月保证覆盖
        LocalDate begin = periods.get(0).start().minusYears(1).minusMonths(1);
        LocalDate end = periods.get(periods.size() - 1).end();
        Filter f = filter(withoutDateRange(q));
        String bounds = "c.occur_date>=? AND c.occur_date<=?";
        String where = f.whereClause().isBlank() ? " WHERE " + bounds : f.whereClause() + " AND " + bounds;
        List<Object> args = new ArrayList<>(f.args());
        args.add(begin);
        args.add(end);
        String keyExpr = switch (granularity) {
            case "week" -> "DATE_FORMAT(c.occur_date,'%x-W%v')";
            case "month" -> "DATE_FORMAT(c.occur_date,'%Y-%m')";
            case "quarter" -> QUARTER_KEY;
            default -> HALF_KEY;
        };
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT " + keyExpr + " k, COUNT(*) cnt, "
                        + "COALESCE(SUM(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END),0) amt "
                        + "FROM cs_complaint c" + where + " GROUP BY k", args.toArray())) {
            rows.put(String.valueOf(row.get("k")), row);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Period p : periods) {
            Map<String, Object> cur = rowOf(rows, p.key());
            Map<String, Object> prev = rowOf(rows, periodOf(granularity, p.start().minusDays(1)).key());
            Map<String, Object> yoy = rowOf(rows, periodOf(granularity, p.start().minusYears(1)).key());
            long count = number(cur.get("cnt")).longValue();
            BigDecimal amount = decimal(cur.get("amt"));
            long prevCount = number(prev.get("cnt")).longValue();
            BigDecimal prevAmount = decimal(prev.get("amt"));
            long yoyCount = number(yoy.get("cnt")).longValue();
            BigDecimal yoyAmount = decimal(yoy.get("amt"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", p.key());
            item.put("label", p.label());
            item.put("count", count);
            item.put("amount", amount);
            item.put("prevCount", prevCount);
            item.put("yoyCount", yoyCount);
            item.put("prevAmount", prevAmount);
            item.put("yoyAmount", yoyAmount);
            item.put("countChainRate", rate(count, prevCount));
            item.put("countYoyRate", rate(count, yoyCount));
            item.put("amountChainRate", rate(amount.doubleValue(), prevAmount.doubleValue()));
            item.put("amountYoyRate", rate(amount.doubleValue(), yoyAmount.doubleValue()));
            items.add(item);
        }
        result.put("periods", items);
        return result;
    }

    // ================= 周期计算 =================

    private record Period(String key, String label, LocalDate start, LocalDate end) { }

    private String normalizeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) return "month";
        return switch (granularity) {
            case "week", "month", "quarter", "half" -> granularity;
            default -> throw BizException.badRequest("不支持的统计周期: " + granularity);
        };
    }

    private ComplaintStatsQuery withoutDateRange(ComplaintStatsQuery q) {
        ComplaintStatsQuery copy = new ComplaintStatsQuery();
        copy.setBusinessType(q.getBusinessType());
        copy.setFloor(q.getFloor());
        copy.setCategory(q.getCategory());
        copy.setStatus(q.getStatus());
        copy.setSatisfaction(q.getSatisfaction());
        copy.setChannel(q.getChannel());
        copy.setBrandName(q.getBrandName());
        return copy;
    }

    private Period periodOf(String granularity, LocalDate date) {
        return switch (granularity) {
            case "week" -> weekPeriod(date);
            case "quarter" -> quarterPeriod(date);
            case "half" -> halfPeriod(date);
            default -> monthPeriod(date);
        };
    }

    private Period weekPeriod(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        String key = String.format("%d-W%02d", date.get(WeekFields.ISO.weekBasedYear()), date.get(WeekFields.ISO.weekOfWeekBasedYear()));
        String label = start + "~" + end.getMonthValue() + "-" + end.getDayOfMonth();
        return new Period(key, label, start, end);
    }

    private Period monthPeriod(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        String label = String.format("%d年%02d月", start.getYear(), start.getMonthValue());
        return new Period(String.format("%d-%02d", start.getYear(), start.getMonthValue()), label, start, end);
    }

    /** 季度：12-2 冬季、3-5 春季、6-8 夏季、9-11 秋季；12 月归入次年冬季。 */
    private Period quarterPeriod(LocalDate date) {
        int month = date.getMonthValue();
        int year = date.getYear();
        String code;
        int startMonth;
        int startYear;
        int seasonYear;
        if (month == 12) {
            code = "W"; startMonth = 12; startYear = year; seasonYear = year + 1;
        } else if (month <= 2) {
            code = "W"; startMonth = 12; startYear = year - 1; seasonYear = year;
        } else if (month <= 5) {
            code = "SP"; startMonth = 3; startYear = year; seasonYear = year;
        } else if (month <= 8) {
            code = "SU"; startMonth = 6; startYear = year; seasonYear = year;
        } else {
            code = "AU"; startMonth = 9; startYear = year; seasonYear = year;
        }
        LocalDate start = LocalDate.of(startYear, startMonth, 1);
        LocalDate end = start.plusMonths(3).minusDays(1);
        String label = String.format("%d.%02d-%d.%02d", startYear, startMonth, end.getYear(), end.getMonthValue());
        return new Period(code + seasonYear, label, start, end);
    }

    /** 半年：3-8 为上半年，9-2 为下半年（跨年）。 */
    private Period halfPeriod(LocalDate date) {
        int month = date.getMonthValue();
        int year = date.getYear();
        LocalDate start;
        int keyYear;
        boolean first;
        if (month >= 3 && month <= 8) {
            first = true; start = LocalDate.of(year, 3, 1); keyYear = year;
        } else if (month <= 2) {
            first = false; start = LocalDate.of(year - 1, 9, 1); keyYear = year - 1;
        } else {
            first = false; start = LocalDate.of(year, 9, 1); keyYear = year;
        }
        LocalDate end = start.plusMonths(6).minusDays(1);
        String label = String.format("%d.%02d-%d.%02d", start.getYear(), start.getMonthValue(), end.getYear(), end.getMonthValue());
        return new Period("H" + (first ? 1 : 2) + keyYear, label, start, end);
    }

    private List<Period> periodsEndingAt(String granularity, Period end, int limit) {
        LinkedList<Period> list = new LinkedList<>();
        Period current = end;
        for (int i = 0; i < limit; i++) {
            list.addFirst(current);
            current = periodOf(granularity, current.start().minusDays(1));
        }
        return list;
    }

    private List<Period> periodsBetween(String granularity, Period from, Period to) {
        List<Period> list = new ArrayList<>();
        Period current = from;
        for (int i = 0; i < 120; i++) {
            list.add(current);
            if (current.key().equals(to.key())) break;
            current = periodOf(granularity, current.end().plusDays(1));
        }
        return list;
    }

    private Map<String, Object> rowOf(Map<String, Map<String, Object>> rows, String key) {
        return rows.getOrDefault(key, Map.of());
    }

    /** 同环比百分比，基数为 0 时返回 null（前端显示 -）。 */
    private BigDecimal rate(double current, double base) {
        if (base == 0) return null;
        return BigDecimal.valueOf((current - base) / base * 100).setScale(1, RoundingMode.HALF_UP);
    }

    // ================= 内部工具 =================

    private String dimension(String dimension) {
        if (dimension == null || dimension.isBlank()) return "category";
        return switch (dimension) {
            case "category", "businessType", "floor", "status", "channel", "satisfaction", "brand", "manager", "handler" -> dimension;
            default -> throw BizException.badRequest("不支持的统计维度: " + dimension);
        };
    }

    private String dimensionExpr(String dimension) {
        return switch (dimension) {
            case "category" -> "COALESCE(cd.item_label, c.category)";
            case "businessType" -> "COALESCE(d.item_label, c.business_type)";
            case "floor" -> "COALESCE(f.item_label, c.floor_code)";
            case "status" -> "COALESCE(sd.item_label, c.status)";
            case "channel" -> "COALESCE(ch.item_label, c.channel)";
            case "satisfaction" -> "COALESCE(sf.item_label, c.satisfaction)";
            case "brand" -> "COALESCE(NULLIF(c.brand_name,''), '-')";
            default -> throw BizException.badRequest("不支持的统计维度: " + dimension);
        };
    }

    private record Filter(String whereClause, List<Object> args) { }

    /** 在已有筛选条件后追加一个 AND 条件（自动补 WHERE）。 */
    private String append(Filter f, String condition) {
        return f.whereClause() == null || f.whereClause().isBlank()
                ? " WHERE " + condition
                : f.whereClause() + " AND " + condition;
    }

    private Filter filter(ComplaintStatsQuery q) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (q.getStart() != null) {
            conditions.add("c.occur_date>=?"); args.add(q.getStart());
        }
        if (q.getEnd() != null) {
            conditions.add("c.occur_date<=?"); args.add(q.getEnd());
        }
        if (q.getBusinessType() != null && !q.getBusinessType().isBlank()) {
            conditions.add("c.business_type=?"); args.add(q.getBusinessType());
        }
        if (q.getFloor() != null && !q.getFloor().isBlank()) {
            conditions.add("c.floor_code=?"); args.add(q.getFloor());
        }
        if (q.getCategory() != null && !q.getCategory().isBlank()) {
            conditions.add("c.category=?"); args.add(q.getCategory());
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            conditions.add("c.status=?"); args.add(q.getStatus());
        }
        if (q.getSatisfaction() != null && !q.getSatisfaction().isBlank()) {
            conditions.add("c.satisfaction=?"); args.add(q.getSatisfaction());
        }
        if (q.getChannel() != null && !q.getChannel().isBlank()) {
            conditions.add("c.channel=?"); args.add(q.getChannel());
        }
        if (q.getBrandName() != null && !q.getBrandName().isBlank()) {
            conditions.add("c.brand_name=?"); args.add(q.getBrandName());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new Filter(where, args);
    }

    private Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static final String QUARTER_KEY = "CONCAT(CASE "
            + "WHEN MONTH(c.occur_date)<=2 THEN 'W' "
            + "WHEN MONTH(c.occur_date)=12 THEN 'W' "
            + "WHEN MONTH(c.occur_date)<=5 THEN 'SP' "
            + "WHEN MONTH(c.occur_date)<=8 THEN 'SU' ELSE 'AU' END, "
            + "CASE WHEN MONTH(c.occur_date)=12 THEN YEAR(c.occur_date)+1 ELSE YEAR(c.occur_date) END)";
    private static final String HALF_KEY = "CONCAT('H', "
            + "CASE WHEN MONTH(c.occur_date) BETWEEN 3 AND 8 THEN '1' ELSE '2' END, "
            + "CASE WHEN MONTH(c.occur_date)<=2 THEN YEAR(c.occur_date)-1 ELSE YEAR(c.occur_date) END)";

    private static final String DICT_JOINS = """
            LEFT JOIN cs_complaint_dict cd ON cd.dict_type='CATEGORY' AND cd.item_value=c.category
            LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=c.business_type
            LEFT JOIN business_dictionary f ON f.dict_type='FLOOR' AND f.item_value=c.floor_code
            LEFT JOIN cs_complaint_dict sd ON sd.dict_type='STATUS' AND sd.item_value=c.status
            LEFT JOIN cs_complaint_dict ch ON ch.dict_type='CHANNEL' AND ch.item_value=c.channel
            LEFT JOIN cs_complaint_dict sf ON sf.dict_type='SATISFACTION' AND sf.item_value=c.satisfaction
            """;
}
