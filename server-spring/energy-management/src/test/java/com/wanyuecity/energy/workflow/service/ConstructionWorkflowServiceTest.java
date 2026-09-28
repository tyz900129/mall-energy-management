package com.wanyuecity.energy.workflow.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.system.service.SysUserService;
import com.wanyuecity.energy.workflow.entity.ConstructionWorkflowDetail;
import com.wanyuecity.energy.workflow.entity.Workflow;
import com.wanyuecity.energy.workflow.mapper.ConstructionWorkflowDetailMapper;
import com.wanyuecity.energy.workflow.mapper.WorkflowMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionInspectionMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionAcceptanceMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConstructionWorkflowServiceTest {
    private final WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
    private final ConstructionWorkflowDetailMapper detailMapper = mock(ConstructionWorkflowDetailMapper.class);
    private final StallMapper stallMapper = mock(StallMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserService userService = mock(SysUserService.class);
    private final ConstructionInspectionMapper inspectionMapper = mock(ConstructionInspectionMapper.class);
    private final ConstructionAcceptanceMapper acceptanceMapper = mock(ConstructionAcceptanceMapper.class);
    private final ConstructionWorkflowService service = new ConstructionWorkflowService(workflowMapper, detailMapper, stallMapper, userMapper, userService, inspectionMapper, acceptanceMapper);

    @Test
    void completingLegacyAcceptanceRecordsOccupiedStatus() {
        Workflow workflow = workflow(ConstructionWorkflowService.ACCEPTANCE);
        ConstructionWorkflowDetail detail = detail();
        Stall stall = stall("AVAILABLE");
        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        when(workflowMapper.update(any(), any())).thenReturn(1);
        when(detailMapper.selectOne(any())).thenReturn(detail);
        when(stallMapper.selectById(9L)).thenReturn(stall);

        service.complete(1L, "admin");

        assertEquals("COMPLETED", workflow.getStatus());
        assertEquals("AVAILABLE", detail.getStallStatusBefore());
        assertEquals("OCCUPIED", detail.getStallStatusAfter());
        assertEquals("admin", detail.getCompletedBy());
        verify(workflowMapper).updateById(workflow);
        verify(detailMapper).updateById(detail);
        verify(stallMapper).update(any(), any());
    }

    @Test
    void completionRequiresPendingAcceptance() {
        Workflow workflow = workflow(ConstructionWorkflowService.ENTRY);
        workflow.setStatus("IN_PROGRESS");
        when(workflowMapper.selectById(1L)).thenReturn(workflow);

        BizException error = assertThrows(BizException.class, () -> service.complete(1L, "admin"));
        assertEquals("新施工流程必须通过验收结果完成并归档", error.getMessage());
    }

    private Workflow workflow(String type) {
        Workflow workflow = new Workflow();
        workflow.setId(1L); workflow.setWorkflowNo("SG1"); workflow.setWorkflowType(type);
        workflow.setStallId(9L); workflow.setMerchantName("测试商户"); workflow.setStatus("SUBMITTED");
        return workflow;
    }

    private ConstructionWorkflowDetail detail() {
        ConstructionWorkflowDetail detail = new ConstructionWorkflowDetail();
        detail.setId(2L); detail.setWorkflowId(1L);
        return detail;
    }

    private Stall stall(String status) {
        Stall stall = new Stall(); stall.setId(9L); stall.setStallCode("L101"); stall.setStatus(status);
        return stall;
    }
}
