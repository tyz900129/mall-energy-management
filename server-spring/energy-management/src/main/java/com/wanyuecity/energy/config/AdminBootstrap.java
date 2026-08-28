package com.wanyuecity.energy.config;

import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On first boot, ensure the default {@code admin} user exists and has a valid
 * BCrypt password. The default password is sourced from the
 * {@code energy.bootstrap.default-admin-password} property (env
 * {@code ENERGY_DEFAULT_ADMIN_PASSWORD}); no production default is provided.
 *
 * <p>Override the env var in any non-dev environment. The placeholder
 * {@code __BOOTSTRAP__} hash makes accidental login impossible — this runner
 * re-hashes the password before the application becomes reachable.
 */
@Slf4j(topic = "security")
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private static final String PLACEHOLDER_HASH = "$2a$10$__BOOTSTRAP__PLACEHOLDER______________________________";
    private static final String DEFAULT_USERNAME = "admin";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${energy.bootstrap.default-admin-password}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        SysUser existing = userMapper.findByUsername(DEFAULT_USERNAME);
        if (existing == null) {
            SysUser u = new SysUser();
            u.setUsername(DEFAULT_USERNAME);
            u.setPasswordHash(passwordEncoder.encode(defaultPassword));
            u.setDisplayName("系统管理员");
            u.setStatus(1);
            userMapper.insert(u);
            log.info("bootstrap: created default admin user (id={})", u.getId());
            return;
        }
        if (PLACEHOLDER_HASH.equals(existing.getPasswordHash())) {
            existing.setPasswordHash(passwordEncoder.encode(defaultPassword));
            userMapper.updateById(existing);
            log.info("bootstrap: re-hashed default admin password (id={})", existing.getId());
        }
    }
}

