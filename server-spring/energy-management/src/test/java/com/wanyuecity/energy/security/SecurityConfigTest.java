package com.wanyuecity.energy.security;

import com.wanyuecity.energy.system.controller.AuthController;
import com.wanyuecity.energy.system.service.SysUserService;
import com.wanyuecity.energy.system.service.AuditLogService;
import com.wanyuecity.energy.system.service.DeviceCredentialService;
import com.wanyuecity.energy.system.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        AuthController.class,
        JwtAuthenticationFilter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class
})
class SecurityConfigTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysUserService userService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private DeviceCredentialService deviceCredentialService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void pingIsPublicAndReturnsPong() throws Exception {
        mockMvc.perform(get("/auth/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void meStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
