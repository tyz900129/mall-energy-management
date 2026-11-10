package com.wanyuecity.energy.qrinspection.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.qrinspection.entity.QrInspectionIssue;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionIssueMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionPlanMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionPlanRoundMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionRecordMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionTaskMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionTaskTargetMapper;
import com.wanyuecity.energy.qrinspection.mapper.QrInspectionTemplateMapper;
import com.wanyuecity.energy.qrinspection.mapper.SysDepartmentWebhookMapper;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.mapper.SysDepartmentMapper;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.notification.mapper.WebhookConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 已关闭的整改单不能再次提交，避免整改记录与关闭时间被覆盖。 */
class QrInspectionFeedbackTest {

    private final QrInspectionIssueMapper issueMapper = mock(QrInspectionIssueMapper.class);
    private final QrInspectionService service = new QrInspectionService(
            mock(QrInspectionTemplateMapper.class),
            mock(QrInspectionPlanMapper.class),
            mock(QrInspectionPlanRoundMapper.class),
            mock(QrInspectionTaskMapper.class),
            mock(QrInspectionTaskTargetMapper.class),
            mock(QrInspectionRecordMapper.class),
            issueMapper,
            mock(SysDepartmentWebhookMapper.class),
            mock(StallMapper.class),
            mock(SysUserMapper.class),
            mock(SysDepartmentMapper.class),
            mock(WebhookConfigMapper.class),
            mock(NotificationService.class),
            mock(JdbcTemplate.class));

    @Test
    void closedIssueRejectsSecondFeedback() {
        QrInspectionIssue issue = new QrInspectionIssue();
        issue.setStatus("CLOSED");
        when(issueMapper.byToken("token-1")).thenReturn(issue);

        BizException error = assertThrows(BizException.class,
                () -> service.feedback("token-1", "已整改", List.of(), "张三", "1"));

        assertEquals("该整改任务已关闭，不能重复提交", error.getMessage());
        verify(issueMapper, never()).updateById(any(QrInspectionIssue.class));
    }
}
