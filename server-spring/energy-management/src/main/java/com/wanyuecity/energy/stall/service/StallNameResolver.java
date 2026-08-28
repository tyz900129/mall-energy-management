package com.wanyuecity.energy.stall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 以铺位编码匹配源平台账户。设备绑定是独立、稳定的本地主数据，
 * 不允许再通过设备当前所属账户反推铺位租户。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StallNameResolver {
    public static final String PENDING_NAME = "待同步账户";

    private final StallMapper stallMapper;
    private final PlatformAccountCurrentMapper accountMapper;
    private final StallDeviceMapper stallDeviceMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;

    @Transactional
    public String refresh(Long stallId) {
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null) return PENDING_NAME;
        PlatformAccountCurrent account = findByStallCode(stall.getStallCode());
        if (account == null) account = findUniqueAccountByBoundDevices(stallId);
        String derived = account == null ? PENDING_NAME : displayName(account);
        if (!derived.equals(stall.getStallName())) {
            stall.setStallName(derived);
            stallMapper.updateById(stall);
        }
        return derived;
    }

    /**
     * 编码无法匹配时，只在铺位全部已绑定设备共同指向唯一账户时补全名称。
     * 多账户或无关系均不猜测，避免跨铺位设备导致商户名称误配。
     */
    private PlatformAccountCurrent findUniqueAccountByBoundDevices(Long stallId) {
        Set<String> accountNumbers = new LinkedHashSet<>();
        for (StallDevice binding : stallDeviceMapper.listActiveByStall(stallId)) {
            for (PlatformAccountDeviceCurrent relation : relationMapper.selectList(
                    new LambdaQueryWrapper<PlatformAccountDeviceCurrent>()
                            .eq(PlatformAccountDeviceCurrent::getPlatformDeviceId, binding.getPlatformDeviceId()))) {
                if (relation.getAccountNumber() != null && !relation.getAccountNumber().isBlank()) {
                    accountNumbers.add(relation.getAccountNumber());
                }
            }
        }
        if (accountNumbers.size() != 1) return null;
        return accountMapper.selectOne(new LambdaQueryWrapper<PlatformAccountCurrent>()
                .eq(PlatformAccountCurrent::getAccountNumber, accountNumbers.iterator().next()).last("LIMIT 1"));
    }

    public PlatformAccountCurrent findByStallCode(String stallCode) {
        if (stallCode == null || stallCode.isBlank()) return null;
        String code = stallCode.trim().toUpperCase(Locale.ROOT);
        List<PlatformAccountCurrent> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<PlatformAccountCurrent>().orderByDesc(PlatformAccountCurrent::getLastSyncedAt));
        for (PlatformAccountCurrent account : accounts) {
            if (matches(account, code)) return account;
        }
        return null;
    }

    public boolean matches(PlatformAccountCurrent account, String stallCode) {
        if (account == null || stallCode == null || stallCode.isBlank()) return false;
        String code = stallCode.trim().toUpperCase(Locale.ROOT);
        return containsCode(account.getAccountName(), code)
                || containsCode(account.getUserName(), code)
                || containsCode(account.getAccountNumber(), code);
    }

    private boolean containsCode(String value, String code) {
        if (value == null || value.isBlank()) return false;
        String text = value.trim().toUpperCase(Locale.ROOT);
        int from = 0;
        while ((from = text.indexOf(code, from)) >= 0) {
            int end = from + code.length();
            boolean left = from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1));
            boolean right = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            from++;
        }
        return false;
    }

    private String displayName(PlatformAccountCurrent account) {
        if (account.getAccountName() != null && !account.getAccountName().isBlank()) return account.getAccountName().trim();
        if (account.getUserName() != null && !account.getUserName().isBlank()) return account.getUserName().trim();
        return account.getAccountNumber();
    }

    @Transactional
    public void refreshAll() {
        for (Stall stall : stallMapper.selectList(null)) refresh(stall.getId());
    }

    /** 部署后修正历史铺位，之后由平台账户同步持续维护。 */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshExistingAfterStartup() {
        try {
            refreshAll();
            log.info("stall account names refreshed by stall code");
        } catch (Exception ex) {
            log.error("failed to refresh stall names after startup: {}", ex.getMessage(), ex);
        }
    }
}
