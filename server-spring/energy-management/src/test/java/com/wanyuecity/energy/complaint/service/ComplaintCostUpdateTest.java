package com.wanyuecity.energy.complaint.service;

import com.wanyuecity.energy.complaint.entity.CsComplaint;
import com.wanyuecity.energy.complaint.entity.CsComplaintLog;
import com.wanyuecity.energy.complaint.mapper.CsBrandMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintDictMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintLogMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintParticipantMapper;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.patrol.PatrolAttachmentService;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 更新导入（费用修正）：空值保留、归零规则、状态保护与审计。 */
class ComplaintCostUpdateTest {

    private final CsComplaintMapper complaintMapper = mock(CsComplaintMapper.class);
    private final CsComplaintParticipantMapper participantMapper = mock(CsComplaintParticipantMapper.class);
    private final CsComplaintLogMapper logMapper = mock(CsComplaintLogMapper.class);
    private final CsComplaintDictMapper dictMapper = mock(CsComplaintDictMapper.class);
    private final CsBrandMapper brandMapper = mock(CsBrandMapper.class);
    private final StallMapper stallMapper = mock(StallMapper.class);
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PatrolAttachmentService attachmentService = mock(PatrolAttachmentService.class);
    private final ComplaintStaffService staffService = mock(ComplaintStaffService.class);
    private final ComplaintService service = new ComplaintService(complaintMapper, participantMapper, logMapper,
            dictMapper, brandMapper, stallMapper, merchantMapper, userMapper, jdbc, attachmentService, staffService);

    @Test
    void blankCostColumnsKeepOriginalValues() {
        when(complaintMapper.selectList(any())).thenReturn(List.of(
                complaint("CS-202601-001", "pending", "100.00", "200.00", false)));
        stubStatus("pending", "待处理");

        ComplaintService.CostUpdatePlan plan = service.previewCostUpdate("CS-202601-001", null, null, null, false);

        assertTrue(plan.matched());
        assertFalse(plan.changed());
        assertTrue(plan.blockedReason() == null);
        assertTrue(plan.changes().isEmpty());
    }

    @Test
    void noCostTrueForcesBothCostsToZero() {
        when(complaintMapper.selectList(any())).thenReturn(List.of(
                complaint("CS-202601-002", "processing", "80.00", "20.00", false)));
        stubStatus("processing", "处理中");

        ComplaintService.CostUpdatePlan plan = service.previewCostUpdate("CS-202601-002", new BigDecimal("50"), null, true, false);

        assertTrue(plan.changed());
        assertTrue(plan.forcedZero());
        assertEquals(0, plan.newCompanyCost().compareTo(BigDecimal.ZERO));
        assertEquals(0, plan.newMerchantCost().compareTo(BigDecimal.ZERO));
        assertTrue(plan.note().contains("归零"));
    }

    @Test
    void closedComplaintIsRejectedAndNotWritten() {
        when(complaintMapper.selectList(any())).thenReturn(List.of(
                complaint("CS-202601-003", "closed", "10.00", "0.00", false)));
        stubStatus("closed", "已关闭");

        ComplaintService.CostUpdatePlan plan = service.applyCostUpdate("CS-202601-003", new BigDecimal("99"), null, null, false, null);

        assertTrue(plan.matched());
        assertNotNull(plan.blockedReason());
        verify(complaintMapper, never()).updateById(any(CsComplaint.class));
        verify(logMapper, never()).insert(any(CsComplaintLog.class));
    }

    @Test
    void closedComplaintIsWritableWhenAllowClosedIsOn() {
        when(complaintMapper.selectList(any())).thenReturn(List.of(
                complaint("CS-202601-005", "closed", "10.00", "0.00", false)));
        stubStatus("closed", "已关闭");

        ComplaintService.CostUpdatePlan plan = service.applyCostUpdate(
                "CS-202601-005", null, new BigDecimal("9.90"), null, true, null);

        assertTrue(plan.changed());
        assertTrue(plan.blockedReason() == null);
        ArgumentCaptor<CsComplaint> captor = ArgumentCaptor.forClass(CsComplaint.class);
        verify(complaintMapper).updateById(captor.capture());
        CsComplaint written = captor.getValue();
        assertEquals(0, written.getMerchantCost().compareTo(new BigDecimal("9.90")));
        assertEquals(null, written.getStatus());
        assertEquals(null, written.getClosedAt());
        assertEquals(null, written.getHandleDays());
    }

    @Test
    void changedCostsAreWrittenWithAuditLog() {
        when(complaintMapper.selectList(any())).thenReturn(List.of(
                complaint("CS-202601-004", "processing", "10.00", "20.00", false)));
        stubStatus("processing", "处理中");
        SysUser operator = new SysUser();
        operator.setId(9L);
        operator.setUsername("tester");
        operator.setDisplayName("测试员");

        ComplaintService.CostUpdatePlan plan = service.applyCostUpdate(
                "CS-202601-004", new BigDecimal("30.00"), null, null, false, operator);

        assertTrue(plan.changed());
        ArgumentCaptor<CsComplaint> captor = ArgumentCaptor.forClass(CsComplaint.class);
        verify(complaintMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getCompanyCost().compareTo(new BigDecimal("30.00")));
        assertEquals(0, captor.getValue().getMerchantCost().compareTo(new BigDecimal("20.00")));
        ArgumentCaptor<CsComplaintLog> logCaptor = ArgumentCaptor.forClass(CsComplaintLog.class);
        verify(logMapper).insert(logCaptor.capture());
        assertEquals("import_update", logCaptor.getValue().getAction());
        assertTrue(logCaptor.getValue().getNote().contains("公司承担费用"));
    }

    @Test
    void unknownComplaintNoIsNeverCreated() {
        when(complaintMapper.selectList(any())).thenReturn(List.of());

        ComplaintService.CostUpdatePlan plan = service.previewCostUpdate("CS-404", new BigDecimal("1"), null, null, false);

        assertFalse(plan.matched());
        assertTrue(plan.reason().contains("未找到"));
        verify(complaintMapper, never()).insert(any(CsComplaint.class));
    }

    private void stubStatus(String code, String label) {
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(code))).thenReturn(label);
    }

    private static CsComplaint complaint(String no, String status, String company, String merchant, boolean noCost) {
        CsComplaint c = new CsComplaint();
        c.setId(100L);
        c.setComplaintNo(no);
        c.setBrandName("星巴克");
        c.setStatus(status);
        c.setCompanyCost(new BigDecimal(company));
        c.setMerchantCost(new BigDecimal(merchant));
        c.setNoCost(noCost);
        return c;
    }
}
