package com.wanyuecity.energy.stall.service;

import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StallNameResolverTest {
    private final StallMapper stallMapper = mock(StallMapper.class);
    private final PlatformAccountCurrentMapper accountMapper = mock(PlatformAccountCurrentMapper.class);
    private final StallDeviceMapper stallDeviceMapper = mock(StallDeviceMapper.class);
    private final PlatformAccountDeviceCurrentMapper relationMapper = mock(PlatformAccountDeviceCurrentMapper.class);
    private final StallNameResolver resolver = new StallNameResolver(stallMapper, accountMapper, stallDeviceMapper, relationMapper);

    @Test
    void matchesAccountByStandaloneStallCodeWithoutUsingDeviceRelations() {
        PlatformAccountCurrent account = new PlatformAccountCurrent();
        account.setAccountName("3066-L528-快乐爱斯米");
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertEquals(account, resolver.findByStallCode("L528"));
    }

    @Test
    void doesNotMatchCodeEmbeddedInsideAnotherCode() {
        PlatformAccountCurrent account = new PlatformAccountCurrent();
        account.setAccountName("3066-L5288-其它商户");
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertNull(resolver.findByStallCode("L528"));
    }

    @Test
    void usesEnabledAccountFromBoundDeviceEvenWhenStallCodeCouldMatchAnotherAccount() {
        Stall stall=new Stall();stall.setId(1L);stall.setStallCode("仓库A");stall.setStallName(StallNameResolver.PENDING_NAME);
        StallDevice binding=new StallDevice();binding.setPlatformDeviceId("D1");
        PlatformAccountDeviceCurrent relation=new PlatformAccountDeviceCurrent();relation.setAccountNumber("A1");
        PlatformAccountCurrent account=new PlatformAccountCurrent();account.setAccountNumber("A1");account.setMerchantCode("000001");account.setUserName("3066-000001-仓库租户");account.setAccountName("其它铺位-错误旧名称");account.setPlatformStatus("1");
        when(stallMapper.selectById(1L)).thenReturn(stall);
        when(stallDeviceMapper.listActiveByStall(1L)).thenReturn(List.of(binding));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertEquals("仓库租户",resolver.refresh(1L));
    }

    @Test
    void extractsMerchantNameFromStandardBillingAccountName() {
        Stall stall=new Stall();stall.setId(1L);stall.setStallCode("L528");stall.setStallName(StallNameResolver.PENDING_NAME);
        StallDevice binding=new StallDevice();binding.setPlatformDeviceId("D1");
        PlatformAccountDeviceCurrent relation=new PlatformAccountDeviceCurrent();relation.setAccountNumber("A1");
        PlatformAccountCurrent account=new PlatformAccountCurrent();account.setAccountNumber("A1");account.setMerchantCode("006804");account.setUserName("3066-006804-柳真真");account.setAccountName("L6-0063-错误旧名称");account.setPlatformStatus("1");
        when(stallMapper.selectById(1L)).thenReturn(stall);
        when(stallDeviceMapper.listActiveByStall(1L)).thenReturn(List.of(binding));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertEquals("柳真真",resolver.refresh(1L));
    }

    @Test
    void doesNotUseLegacyCurrentMerchantFieldAsStallName() {
        Stall stall=new Stall();stall.setId(1L);stall.setStallCode("L423");stall.setStallName("L314-CONVERSE");
        StallDevice binding=new StallDevice();binding.setPlatformDeviceId("D1");
        PlatformAccountDeviceCurrent relation=new PlatformAccountDeviceCurrent();relation.setAccountNumber("A1");
        PlatformAccountCurrent account=new PlatformAccountCurrent();account.setAccountNumber("A1");account.setMerchantCode("000092");account.setAccountName("L314-CONVERSE");account.setPlatformStatus("1");
        when(stallMapper.selectById(1L)).thenReturn(stall);
        when(stallDeviceMapper.listActiveByStall(1L)).thenReturn(List.of(binding));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertEquals(StallNameResolver.PENDING_NAME,resolver.refresh(1L));
    }

    @Test
    void ignoresDisabledAccountFromBoundDevice() {
        Stall stall=new Stall();stall.setId(1L);stall.setStallCode("L1");stall.setStallName("旧名称");
        StallDevice binding=new StallDevice();binding.setPlatformDeviceId("D1");
        PlatformAccountDeviceCurrent relation=new PlatformAccountDeviceCurrent();relation.setAccountNumber("A1");
        PlatformAccountCurrent account=new PlatformAccountCurrent();account.setAccountNumber("A1");account.setUserName("3066-000001-已停用账户");account.setPlatformStatus("DISABLED");
        when(stallMapper.selectById(1L)).thenReturn(stall);
        when(stallDeviceMapper.listActiveByStall(1L)).thenReturn(List.of(binding));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        assertEquals(StallNameResolver.PENDING_NAME,resolver.refresh(1L));
    }
}
