package com.wanyuecity.energy.dashboard.service;

import com.wanyuecity.energy.dashboard.service.dto.DashboardStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    public DashboardStatsView stats() {
        LocalDate firstDay = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        LocalDateTime monthStart = firstDay.atStartOfDay();
        LocalDateTime nextMonthStart = firstDay.plusMonths(1).atStartOfDay();
        return new DashboardStatsView(
            count("SELECT COUNT(*) FROM platform_account_current WHERE platform_status IS NULL OR UPPER(platform_status) NOT IN ('0','DISABLED','INACTIVE')"),
            count("SELECT COUNT(*) FROM stall"),
            count("SELECT COUNT(*) FROM stall WHERE business_type='CK'"),
            count("SELECT COUNT(DISTINCT stall_id) FROM stall_device WHERE status='ACTIVE'"),
            count("SELECT COUNT(*) FROM workflow WHERE submitted_at>=? AND submitted_at<?", monthStart, nextMonthStart)
        );
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
