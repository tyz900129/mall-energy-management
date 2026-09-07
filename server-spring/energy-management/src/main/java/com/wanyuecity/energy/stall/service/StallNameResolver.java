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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 以本地铺位—设备绑定关系为入口，派生设备当前所属的启用计费账户名称。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StallNameResolver {
    public static final String PENDING_NAME = "待同步账户";
    public static final String AMBIGUOUS_NAME = "多账户待核对";
    private static final Pattern BILLING_ACCOUNT_NAME = Pattern.compile(
            "^3066\\s*[-—－_]\\s*\\d{6}\\s*[-—－_]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private final StallMapper stallMapper;
    private final PlatformAccountCurrentMapper accountMapper;
    private final StallDeviceMapper stallDeviceMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;

    @Transactional
    public String refresh(Long stallId) {
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null) return PENDING_NAME;
        AccountResolution resolution = resolveByBoundDevices(stallId);
        PlatformAccountCurrent account = resolution.account();
        String derived = resolution.ambiguous() ? AMBIGUOUS_NAME
                : account == null ? PENDING_NAME : displayName(account);
        if (!derived.equals(stall.getStallName())) {
            stall.setStallName(derived);
            stallMapper.updateById(stall);
        }
        return derived;
    }

    /** 只接受铺位已绑定设备共同指向的唯一启用账户；多账户时明确提示，不做猜测。 */
    private AccountResolution resolveByBoundDevices(Long stallId) {
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
        if (accountNumbers.isEmpty()) return new AccountResolution(null, false);
        List<PlatformAccountCurrent> enabledAccounts = accountMapper.selectList(
                new LambdaQueryWrapper<PlatformAccountCurrent>()
                        .in(PlatformAccountCurrent::getAccountNumber, accountNumbers))
                .stream().filter(account -> isEnabled(account.getPlatformStatus())).toList();
        Set<String> enabledNumbers = new LinkedHashSet<>();
        enabledAccounts.forEach(account -> enabledNumbers.add(account.getAccountNumber()));
        if (enabledNumbers.size() > 1) return new AccountResolution(null, true);
        if (enabledNumbers.isEmpty()) return new AccountResolution(null, false);
        String onlyNumber = enabledNumbers.iterator().next();
        return new AccountResolution(enabledAccounts.stream()
                .filter(account -> onlyNumber.equals(account.getAccountNumber())).findFirst().orElse(null), false);
    }

    /** 供铺位详情使用，确保展示资料与铺位名称采用同一套绑定规则。 */
    public PlatformAccountCurrent findByBoundDevices(Long stallId) {
        AccountResolution resolution = resolveByBoundDevices(stallId);
        return resolution.ambiguous() ? null : resolution.account();
    }

    private boolean isEnabled(String status) {
        return status == null || !Set.of("0", "DISABLED", "INACTIVE").contains(status.toUpperCase(Locale.ROOT));
    }

    private record AccountResolution(PlatformAccountCurrent account, boolean ambiguous) {}

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
        if (account.getAccountName() != null && !account.getAccountName().isBlank()) {
            String accountName = account.getAccountName().trim();
            Matcher matcher = BILLING_ACCOUNT_NAME.matcher(accountName);
            // 标准计费账户名为 3066-六位商户代码-商户名称，铺位只展示商户名称。
            if (matcher.matches() && !matcher.group(1).isBlank()) return matcher.group(1).trim();
            return accountName;
        }
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
            log.info("stall account names refreshed by active device bindings");
        } catch (Exception ex) {
            log.error("failed to refresh stall names after startup: {}", ex.getMessage(), ex);
        }
    }
}
