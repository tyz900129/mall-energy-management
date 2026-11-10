package com.wanyuecity.energy.complaint.service;

import com.wanyuecity.energy.complaint.entity.CsComplaint;
import com.wanyuecity.energy.complaint.mapper.CsBrandMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintDictMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintLogMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintParticipantMapper;
import com.wanyuecity.energy.complaint.service.dto.ComplaintRequest;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.patrol.PatrolAttachmentService;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商管角色只能访问本人作为责任商管的客诉（与列表数据范围一致）。 */
class ComplaintServiceScopeTest {

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
    void scopedManagerCannotReadComplaintOutsideScope() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(7L), eq(7L))).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.detail(42L, 7L));

        assertEquals("客诉不存在: 42", error.getMessage());
    }

    @Test
    void scopedManagerCanReadOwnComplaint() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(7L), eq(7L))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), eq(42L))).thenReturn(new LinkedHashMap<>());
        when(attachmentService.list(ComplaintService.ATTACHMENT_TYPE, 42L)).thenReturn(List.of());

        Map<String, Object> detail = service.detail(42L, 7L);

        assertTrue(detail.containsKey("managers"));
        assertTrue(detail.containsKey("handlers"));
    }

    @Test
    void scopedManagerCannotUpdateComplaintOutsideScope() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(7L), eq(7L))).thenReturn(0);

        assertThrows(BizException.class, () -> service.update(42L, new ComplaintRequest(), null, 7L));

        verify(complaintMapper, never()).updateById(any(CsComplaint.class));
    }
}
