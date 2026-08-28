package com.wanyuecity.energy.platform.service;

import com.wanyuecity.energy.platform.adapter.PlatformAdapter;
import com.wanyuecity.energy.platform.adapter.TestConnectionResult;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import com.wanyuecity.energy.platform.entity.PlatformConfig;
import com.wanyuecity.energy.platform.mapper.PlatformConfigMapper;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigUpdateRequest;
import com.wanyuecity.energy.platform.service.dto.PlatformConfigView;
import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final PlatformConfigMapper configMapper;
    private final CredentialCryptoService crypto;
    private final PlatformAdapter adapter;

    public PlatformConfigView get() {
        PlatformConfig c = load();
        boolean hasPw = c.getPasswordCiphertext() != null
                && !c.getPasswordCiphertext().isBlank()
                && !"__PENDING__".equals(c.getPasswordCiphertext());
        return PlatformConfigView.of(c, hasPw);
    }

    @Transactional
    public PlatformConfigView update(PlatformConfigUpdateRequest req) {
        if (!crypto.isAvailable()) {
            throw BizException.badRequest("PLATFORM_CREDENTIAL_SECRET env is not set; cannot save password");
        }
        PlatformConfig c = load();

        c.setBaseUrl(req.getBaseUrl().trim());
        c.setAccountUsername(req.getAccountUsername().trim());
        c.setSpaceId(req.getSpaceId().trim());
        if (req.getEnabled() != null) c.setEnabled(req.getEnabled());
        if (req.getAutoSync() != null) c.setAutoSync(req.getAutoSync());
        if (req.getSyncCron() != null && !req.getSyncCron().isBlank()) c.setSyncCron(req.getSyncCron().trim());
        if (req.getConnectTimeoutMs() != null) c.setConnectTimeoutMs(req.getConnectTimeoutMs());
        if (req.getRemark() != null) c.setRemark(req.getRemark());

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            CredentialCryptoService.Encrypted enc = crypto.encrypt(req.getPassword());
            c.setPasswordCiphertext(enc.ciphertext());
            c.setPasswordIv(enc.iv());
        }
        configMapper.updateById(c);
        log.info("platform config updated: baseUrl={}, username={}, spaceId={}, pwChanged={}",
                c.getBaseUrl(), c.getAccountUsername(), c.getSpaceId(),
                req.getPassword() != null && !req.getPassword().isBlank());
        return get();
    }

    public TestConnectionResult testConnection() {
        PlatformConfig c = load();
        if (c.getPasswordCiphertext() == null || "__PENDING__".equals(c.getPasswordCiphertext())) {
            throw BizException.badRequest("Password not configured yet");
        }
        String pw = crypto.decrypt(c.getPasswordCiphertext(), c.getPasswordIv());
        TestConnectionResult r;
        try {
            r = adapter.testConnection(c.getBaseUrl(), c.getAccountUsername(), pw, c.getSpaceId());
        } catch (Exception ex) {
            r = new TestConnectionResult(false, ex.getMessage(), null);
        }
        c.setLastConnectAt(LocalDateTime.now());
        c.setLastConnectStatus(r.success() ? "SUCCESS" : "FAILED: " + truncate(r.message(), 200));
        configMapper.updateById(c);
        log.info("platform test-connection: success={}, message={}", r.success(), r.message());
        return r;
    }

    private PlatformConfig load() {
        PlatformConfig c = configMapper.findSingleton();
        if (c == null) {
            // Defensive: Flyway guarantees id=1, but if a manual wipe happened, recreate.
            c = new PlatformConfig();
            c.setId(1L);
            c.setPlatformName("LeoEnergy");
            c.setBaseUrl("https://app.leotechnology.cn");
            c.setAccountUsername("");
            c.setPasswordCiphertext("__PENDING__");
            c.setPasswordIv("__PENDING__");
            c.setSpaceId("4801");
            c.setEnabled(0);
            c.setAutoSync(0);
            c.setSyncCron("0 0 2 * * ?");
            c.setConnectTimeoutMs(10000);
            configMapper.insert(c);
        }
        return c;
    }

    private String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n);
    }
}

