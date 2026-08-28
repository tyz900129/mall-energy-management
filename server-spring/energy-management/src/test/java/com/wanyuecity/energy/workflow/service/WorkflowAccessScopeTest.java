package com.wanyuecity.energy.workflow.service;
import com.wanyuecity.energy.core.BizException;import com.wanyuecity.energy.workflow.entity.Workflow;import com.wanyuecity.energy.workflow.mapper.WorkflowMapper;import com.wanyuecity.energy.workflow.mapper.WorkflowDeviceMapper;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.any;import static org.mockito.Mockito.*;
class WorkflowAccessScopeTest{
 private final WorkflowMapper mapper=mock(WorkflowMapper.class);private final WorkflowDeviceMapper devices=mock(WorkflowDeviceMapper.class);private final WorkflowService service=new WorkflowService(mapper,devices,null,null,null,null,null,null,null,null,null,null,null,null);
 @Test void applicantCanReadOwnWorkflow(){Workflow w=row("alice");when(mapper.selectById(1L)).thenReturn(w);when(devices.selectList(any())).thenReturn(java.util.List.of());assertDoesNotThrow(()->service.devicesAccessible(1L,"alice",true));}
 @Test void applicantCannotReadAnotherApplicantsWorkflow(){Workflow w=row("bob");when(mapper.selectById(1L)).thenReturn(w);BizException e=assertThrows(BizException.class,()->service.devicesAccessible(1L,"alice",true));assertEquals(403,e.getCode());}
 private Workflow row(String owner){Workflow w=new Workflow();w.setId(1L);w.setApplicantName(owner);w.setStallId(99L);return w;}
}
