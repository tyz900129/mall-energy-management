package com.wanyuecity.energy.workflow.service;

import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.*;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import com.wanyuecity.energy.workflow.service.dto.NewMerchantPrecheckRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowServicePrecheckTest {
    private final StallMapper stallMapper=mock(StallMapper.class);
    private final StallDeviceMapper stallDeviceMapper=mock(StallDeviceMapper.class);
    private final MerchantMapper merchantMapper=mock(MerchantMapper.class);
    private final PlatformAccountCurrentMapper accountMapper=mock(PlatformAccountCurrentMapper.class);
    private final PlatformDeviceCurrentMapper deviceMapper=mock(PlatformDeviceCurrentMapper.class);
    private final PlatformAccountDeviceCurrentMapper relationMapper=mock(PlatformAccountDeviceCurrentMapper.class);
    private final WorkflowService service=new WorkflowService(null,null,null,stallMapper,stallDeviceMapper,merchantMapper,
            accountMapper,deviceMapper,relationMapper,null,null,null,null,null);

    @Test void allowsUnboundStallDevices() {
        arrangeBase(); when(relationMapper.selectList(any())).thenReturn(List.of());
        var result=service.precheckNewMerchant(request());
        assertTrue(result.isAllowed()); assertEquals(1,result.getDevices().size()); assertFalse(result.getDevices().get(0).isConflict());
    }

    @Test void blocksDeviceStillBoundToOldAccount() {
        arrangeBase(); PlatformAccountDeviceCurrent rel=new PlatformAccountDeviceCurrent(); rel.setAccountNumber("OLD-ACCOUNT");
        when(relationMapper.selectList(any())).thenReturn(List.of(rel));
        var result=service.precheckNewMerchant(request());
        assertFalse(result.isAllowed()); assertTrue(result.getDevices().get(0).isConflict());
        assertTrue(result.getReasons().stream().anyMatch(x->x.contains("OLD-ACCOUNT")));
    }

    private void arrangeBase(){ Stall stall=new Stall();stall.setId(1L);stall.setStallCode("L503");stall.setStatus("AVAILABLE");when(stallMapper.selectById(1L)).thenReturn(stall);
        when(merchantMapper.selectCount(any())).thenReturn(0L);when(accountMapper.selectCount(any())).thenReturn(0L);
        StallDevice binding=new StallDevice();binding.setPlatformDeviceId("DEV-1");when(stallDeviceMapper.listActiveByStall(1L)).thenReturn(List.of(binding));
        PlatformDeviceCurrent device=new PlatformDeviceCurrent();device.setPlatformDeviceId("DEV-1");device.setDeviceName("L503电表");when(deviceMapper.selectOne(any())).thenReturn(device);}
    private NewMerchantPrecheckRequest request(){NewMerchantPrecheckRequest r=new NewMerchantPrecheckRequest();r.setMerchantCode("000001");r.setStallId(1L);return r;}
}

