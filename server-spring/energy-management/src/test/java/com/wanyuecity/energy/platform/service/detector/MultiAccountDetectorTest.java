package com.wanyuecity.energy.platform.service.detector;

import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAccountDetectorTest {

    private final PlatformAccountDeviceCurrentMapper relationMapper = mock(PlatformAccountDeviceCurrentMapper.class);
    private final DataExceptionMapper exceptionMapper = mock(DataExceptionMapper.class);
    private final PlatformAccountCurrentMapper accountMapper = mock(PlatformAccountCurrentMapper.class);
    private final MultiAccountDetector detector = new MultiAccountDetector(relationMapper, accountMapper, exceptionMapper);

    @Test
    void insertsOneInfoExceptionForDeviceWithMultipleAccountsWhenAccountsAreDisabled() {
        when(relationMapper.selectList(null)).thenReturn(List.of(
                relation("DEVICE-1", "ACCOUNT-1"),
                relation("DEVICE-1", "ACCOUNT-2"),
                relation("DEVICE-2", "ACCOUNT-3")));
        when(exceptionMapper.selectCount(any())).thenReturn(0L);
        when(accountMapper.selectList(null)).thenReturn(List.of());

        assertEquals(1, detector.detect(99L));
        ArgumentCaptor<DataException> captor = ArgumentCaptor.forClass(DataException.class);
        verify(exceptionMapper).insert(captor.capture());
        assertEquals("DEVICE_MULTI_ACCOUNT", captor.getValue().getExceptionType());
        assertEquals("INFO", captor.getValue().getSeverity());
        assertEquals("DEVICE-1", captor.getValue().getPlatformDeviceId());
        assertEquals(null, captor.getValue().getAccountNumber());
    }

    @Test
    void existingExceptionIsNotCountedAsNew() {
        when(relationMapper.selectList(null)).thenReturn(List.of(
                relation("DEVICE-1", "ACCOUNT-1"),
                relation("DEVICE-1", "ACCOUNT-2")));
        when(exceptionMapper.selectCount(any())).thenReturn(1L);
        when(accountMapper.selectList(null)).thenReturn(List.of());

        assertEquals(0, detector.detect(99L));
        verify(exceptionMapper, never()).insert(any(DataException.class));
    }

    private PlatformAccountDeviceCurrent relation(String deviceId, String accountNumber) {
        PlatformAccountDeviceCurrent relation = new PlatformAccountDeviceCurrent();
        relation.setPlatformDeviceId(deviceId);
        relation.setAccountNumber(accountNumber);
        return relation;
    }
}

