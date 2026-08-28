package com.wanyuecity.energy.platform.service.detector;

import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanRelationDetectorTest {

    @Test
    void recordsRelationWhoseDeviceIsMissing() {
        var accountMapper = mock(PlatformAccountCurrentMapper.class);
        var deviceMapper = mock(PlatformDeviceCurrentMapper.class);
        var relationMapper = mock(PlatformAccountDeviceCurrentMapper.class);
        var exceptionMapper = mock(DataExceptionMapper.class);

        PlatformAccountCurrent account = new PlatformAccountCurrent();
        account.setAccountNumber("ACCOUNT-1");
        PlatformAccountDeviceCurrent relation = new PlatformAccountDeviceCurrent();
        relation.setAccountNumber("ACCOUNT-1");
        relation.setPlatformDeviceId("MISSING-DEVICE");

        when(accountMapper.selectList(null)).thenReturn(List.of(account));
        when(deviceMapper.selectList(null)).thenReturn(List.of());
        when(relationMapper.selectList(null)).thenReturn(List.of(relation));

        var detector = new OrphanRelationDetector(
                accountMapper, deviceMapper, relationMapper, exceptionMapper);
        assertEquals(1, detector.detect(7L));

        ArgumentCaptor<DataException> captor = ArgumentCaptor.forClass(DataException.class);
        verify(exceptionMapper).insert(captor.capture());
        assertEquals("RELATION_DEVICE_NOT_FOUND", captor.getValue().getExceptionType());
        assertEquals("MISSING-DEVICE", captor.getValue().getPlatformDeviceId());
    }
}

