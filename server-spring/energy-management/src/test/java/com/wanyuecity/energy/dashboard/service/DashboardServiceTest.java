package com.wanyuecity.energy.dashboard.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    @Test
    void countsDistinctBoundStallsAndMonthlySubmittedWorkflows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
            .thenReturn(100L, 200L, 30L, 88L, 12L);

        var stats = new DashboardService(jdbc).stats();

        assertEquals(88L, stats.getBoundStalls());
        assertEquals(12L, stats.getMonthlySubmittedWorkflows());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(5)).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getAllValues().get(3).contains("COUNT(DISTINCT stall_id)"));
        assertTrue(sql.getAllValues().get(4).contains("submitted_at>=?"));
    }
}
