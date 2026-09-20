package com.wanyuecity.energy.usage.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UtilityBillServiceTest {
    @Test
    void usesMarchToFebruaryBusinessYearPeriods() {
        assertWindow("2026-03", "QUARTER", "2026-03", "2026-05", 3);
        assertWindow("2026-08", "QUARTER", "2026-06", "2026-08", 3);
        assertWindow("2026-11", "QUARTER", "2026-09", "2026-11", 3);
        assertWindow("2027-02", "QUARTER", "2026-12", "2027-02", 3);
        assertWindow("2026-07", "HALF_YEAR", "2026-03", "2026-08", 6);
        assertWindow("2027-01", "HALF_YEAR", "2026-09", "2027-02", 6);
        assertWindow("2027-02", "YEAR", "2026-03", "2027-02", 12);
    }

    @Test
    void aggregatesBillsAndTheSamePeriodLastYear() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                bill("2025-03-01", "100", "1000", "20", "200"),
                bill("2026-03-01", "150", "1200", "30", "240"),
                bill("2026-04-01", "50", "400", "10", "80")
        ));

        var rows = new UtilityBillService(jdbc).trend("2026-03", "2026-05", "QUARTER");

        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals("2026-03", row.periodStart());
        assertEquals(2, row.currentMonths());
        assertEquals(1, row.previousMonths());
        assertEquals(0, new BigDecimal("200").compareTo(row.electricAmount()));
        assertEquals(0, new BigDecimal("100").compareTo(row.previousElectricAmount()));
        assertEquals(0, new BigDecimal("320").compareTo(row.waterUsage()));
        assertEquals(0, new BigDecimal("200").compareTo(row.previousWaterUsage()));
    }

    private static void assertWindow(String month, String period, String start, String end, int expectedMonths) {
        var window = UtilityBillService.periodWindow(YearMonth.parse(month), period);
        assertEquals(YearMonth.parse(start), window.start());
        assertEquals(YearMonth.parse(end), window.end());
        assertEquals(expectedMonths, window.expectedMonths());
    }

    private static Map<String, Object> bill(String month, String electricAmount, String electricUsage, String waterAmount, String waterUsage) {
        return Map.of("bill_month", Date.valueOf(LocalDate.parse(month)),
                "electric_amount", new BigDecimal(electricAmount), "electric_usage", new BigDecimal(electricUsage),
                "water_amount", new BigDecimal(waterAmount), "water_usage", new BigDecimal(waterUsage));
    }
}
