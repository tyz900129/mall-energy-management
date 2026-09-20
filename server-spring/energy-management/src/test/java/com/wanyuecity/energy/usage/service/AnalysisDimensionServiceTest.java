package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.usage.service.dto.DeviceDimensionAssignRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisDimensionServiceTest {
    @Test
    void replacesAnExistingDeviceClassification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("dimension_id", 2L, "dimension_value_id", 8L)));

        int changed = new AnalysisDimensionService(jdbc)
            .assign(new DeviceDimensionAssignRequest(2L, 9L, List.of("device-1")));

        assertEquals(1, changed);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().get(0).startsWith("DELETE FROM device_dimension_assignment"));
        assertTrue(sql.getAllValues().get(1).startsWith("INSERT INTO device_dimension_assignment"));
    }

    @Test
    void clearsAnExistingDeviceClassificationWithoutCreatingAReplacement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("dimension_value_id", 8L)));

        int changed = new AnalysisDimensionService(jdbc)
            .assign(new DeviceDimensionAssignRequest(2L, null, List.of("device-1")));

        assertEquals(1, changed);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().startsWith("DELETE FROM device_dimension_assignment"));
        assertFalse(sql.getValue().startsWith("INSERT INTO device_dimension_assignment"));
    }
}
