package com.wanyuecity.energy.opspatrol;

import com.wanyuecity.energy.patrol.PatrolAttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 问题单流水号必须基于已存在的最大号，删除历史问题单后不能回退。 */
class OpsPatrolIssueNoTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OpsPatrolService service = new OpsPatrolService(jdbc,
            mock(PatrolAttachmentService.class), mock(OpsPatrolNotifyService.class));

    @Test
    void nextIssueNoUsesMaxExistingSequence() {
        String prefix = "OP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(prefix.length() + 1), eq(prefix + "%")))
                .thenReturn(3L);

        assertEquals(prefix + "004", service.nextIssueNo());
    }
}
