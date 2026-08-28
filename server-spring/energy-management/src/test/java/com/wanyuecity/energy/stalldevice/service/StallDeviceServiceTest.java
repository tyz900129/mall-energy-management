package com.wanyuecity.energy.stalldevice.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.service.StallNameResolver;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import com.wanyuecity.energy.stalldevice.service.dto.StallDeviceBindRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StallDeviceServiceTest {

    @Mock private StallDeviceMapper stallDeviceMapper;
    @Mock private StallMapper stallMapper;
    @Mock private PlatformDeviceCurrentMapper deviceMapper;
    @Mock private StallNameResolver stallNameResolver;

    private StallDeviceService service;
    private StallDeviceBindRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StallDeviceService(stallDeviceMapper, stallMapper, deviceMapper, stallNameResolver);
        when(stallNameResolver.refresh(1L)).thenReturn("测试商户");

        Stall stall = new Stall();
        stall.setId(1L);
        stall.setStallCode("L101");
        stall.setStatus("AVAILABLE");
        when(stallMapper.selectById(1L)).thenReturn(stall);

        PlatformDeviceCurrent device = new PlatformDeviceCurrent();
        device.setPlatformDeviceId("DEVICE-1");
        device.setEnergyType("ELECTRIC");
        when(deviceMapper.selectOne(any())).thenReturn(device);

        request = new StallDeviceBindRequest();
        request.setStallId(1L);
        request.setPlatformDeviceId("DEVICE-1");
        request.setBoundBy("tester");
    }

    @Test
    void rebindingSameDeviceToSameStallIsIdempotent() {
        StallDevice existing = activeBinding(1L);
        when(stallDeviceMapper.findActiveByDeviceId("DEVICE-1")).thenReturn(existing);

        assertEquals(existing.getId(), service.bind(request).getId());
        verify(stallDeviceMapper, never()).insert(any(StallDevice.class));
    }

    @Test
    void bindingDeviceToDifferentStallIsRejected() {
        when(stallDeviceMapper.findActiveByDeviceId("DEVICE-1")).thenReturn(activeBinding(2L));

        BizException error = assertThrows(BizException.class, () -> service.bind(request));
        assertEquals(400, error.getCode());
        verify(stallDeviceMapper, never()).insert(any(StallDevice.class));
    }

    @Test
    void databaseRaceIsReportedAsBusinessError() {
        when(stallDeviceMapper.findActiveByDeviceId("DEVICE-1")).thenReturn(null);
        when(stallDeviceMapper.insert(any(StallDevice.class))).thenThrow(new DuplicateKeyException("uk_sd_active"));

        BizException error = assertThrows(BizException.class, () -> service.bind(request));
        assertEquals(400, error.getCode());
    }

    private StallDevice activeBinding(Long stallId) {
        StallDevice binding = new StallDevice();
        binding.setId(10L);
        binding.setStallId(stallId);
        binding.setPlatformDeviceId("DEVICE-1");
        binding.setStatus("ACTIVE");
        return binding;
    }
}
