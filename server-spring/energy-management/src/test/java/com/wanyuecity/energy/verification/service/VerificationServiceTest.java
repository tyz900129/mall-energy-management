package com.wanyuecity.energy.verification.service;
import com.wanyuecity.energy.stall.mapper.StallMapper; import com.wanyuecity.energy.verification.entity.*; import com.wanyuecity.energy.verification.mapper.*; import com.wanyuecity.energy.workflow.entity.*; import com.wanyuecity.energy.workflow.mapper.*; import com.wanyuecity.energy.workflow.service.WorkflowStateMachine; import org.junit.jupiter.api.Test; import java.util.List;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.ArgumentMatchers.any; import static org.mockito.Mockito.*;
class VerificationServiceTest {
 private final FieldVerificationTaskMapper taskMapper=mock(FieldVerificationTaskMapper.class); private final FieldVerificationDeviceMapper deviceMapper=mock(FieldVerificationDeviceMapper.class);
 private final WorkflowMapper workflowMapper=mock(WorkflowMapper.class); private final WorkflowDeviceMapper workflowDeviceMapper=mock(WorkflowDeviceMapper.class); private final StallMapper stallMapper=mock(StallMapper.class);
 private final VerificationService service=new VerificationService(taskMapper,deviceMapper,mock(AttachmentMapper.class),workflowMapper,workflowDeviceMapper,stallMapper,new WorkflowStateMachine());
 @Test void createsRandomTokenAndDeviceSnapshots(){Workflow w=new Workflow();w.setId(8L);when(workflowMapper.selectById(8L)).thenReturn(w);WorkflowDevice wd=new WorkflowDevice();wd.setId(9L);wd.setPlatformDeviceId("DEV-1");wd.setEnergyTypeSnapshot("WATER");when(workflowDeviceMapper.selectList(any())).thenReturn(List.of(wd));when(taskMapper.insert(any(FieldVerificationTask.class))).thenAnswer(i->{((FieldVerificationTask)i.getArgument(0)).setId(10L);return 1;});
  FieldVerificationTask task=service.createForWorkflow(8L);assertEquals(43,task.getAccessToken().length());assertEquals("PENDING",task.getStatus());verify(deviceMapper).insert(argThat((FieldVerificationDevice d)->"DEV-1".equals(d.getPlatformDeviceId())&&"m³".equals(d.getReadingUnit())));}
 @Test void creationIsIdempotent(){FieldVerificationTask existing=new FieldVerificationTask();existing.setId(3L);when(taskMapper.selectOne(any())).thenReturn(existing);assertSame(existing,service.createForWorkflow(8L));verify(taskMapper,never()).insert(any(FieldVerificationTask.class));verify(deviceMapper,never()).insert(any(FieldVerificationDevice.class));}
}

