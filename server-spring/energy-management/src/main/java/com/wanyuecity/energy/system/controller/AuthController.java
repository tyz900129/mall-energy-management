package com.wanyuecity.energy.system.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.security.JwtTokenProvider;
import com.wanyuecity.energy.security.LoginUserDetails;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.service.SysUserService;
import com.wanyuecity.energy.system.service.AuditLogService;
import com.wanyuecity.energy.system.service.DeviceCredentialService;
import com.wanyuecity.energy.system.service.PermissionService;
import com.wanyuecity.energy.system.service.dto.DeviceLoginRequest;
import com.wanyuecity.energy.system.service.dto.RegisterRequest;
import com.wanyuecity.energy.system.service.dto.LoginRequest;
import com.wanyuecity.energy.system.service.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Auth endpoints: {@code POST /auth/login}, {@code GET /auth/me}. Logging is done via
 * the {@code security} logger so the audit trail lands in {@code security.log}.
 */
@Slf4j(topic = "security")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;
    private final DeviceCredentialService deviceCredentialService;
    private final PermissionService permissionService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        LoginResponse result = doLogin(req.getUsername(), req.getPassword(), http);
        if (Boolean.TRUE.equals(req.getRememberDevice())) result.setDeviceToken(deviceCredentialService.issue(result.getUser().getId(), req.getFingerprint(), req.getDeviceName()));
        return ApiResponse.ok(result);
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest req) {
        userService.selfRegister(req.getUsername(), req.getPassword(), req.getDisplayName(), req.getPhone());
        return ApiResponse.ok("注册成功，请联系管理员分配权限");
    }

    @PostMapping("/device-login")
    public ApiResponse<LoginResponse> deviceLogin(@Valid @RequestBody DeviceLoginRequest req, HttpServletRequest http) {
        Long userId=deviceCredentialService.authenticate(req.getDeviceToken(),req.getFingerprint());
        SysUser user=userService.findById(userId);
        return ApiResponse.ok(completeLogin(user,http));
    }

    private LoginResponse doLogin(String username, String password, HttpServletRequest http) {
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            log.info("login failed: user not found, ip={}", http.getRemoteAddr());
            auditLogin(username,null,false,"USER_NOT_FOUND",http);
            throw BizException.unauthorized("Invalid username or password");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.info("login failed: bad password, user={}, ip={}", user.getUsername(), http.getRemoteAddr());
            auditLogin(user.getUsername(),user.getId(),false,"BAD_CREDENTIALS",http);
            throw BizException.unauthorized("Invalid username or password");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            log.info("login failed: disabled, user={}, ip={}", user.getUsername(), http.getRemoteAddr());
            auditLogin(user.getUsername(),user.getId(),false,"USER_DISABLED",http);
            throw BizException.forbidden("User disabled");
        }
        return completeLogin(user,http);
    }

    private LoginResponse completeLogin(SysUser user, HttpServletRequest http) {
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) throw BizException.forbidden("账号已停用");
        List<String> roles = userService.findRoleCodes(user.getId());
        List<String> permissions = List.copyOf(permissionService.effectivePermissionCodes(user.getId()));
        if (roles.isEmpty() && permissions.isEmpty()) throw BizException.forbidden("账号尚未分配权限，请联系管理员");
        String token = tokenProvider.createToken(user.getId(), user.getUsername(), roles);
        userService.recordLogin(user.getId());
        log.info("login ok: user={}, roles={}, ip={}", user.getUsername(), roles, http.getRemoteAddr());
        auditLogin(user.getUsername(),user.getId(),true,null,http);

        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(), roles,
                permissions);
        return new LoginResponse(token, tokenProvider.getExpireMinutes(), info);
    }
    private void auditLogin(String username,Long userId,boolean success,String reason,HttpServletRequest http){try{String result="{\"success\":"+success+(reason==null?"":",\"reason\":\""+reason+"\"")+"}";auditLogService.record(username,userId,"LOGIN","AUTH",userId==null?null:userId.toString(),null,result,http.getRemoteAddr(),http.getHeader("User-Agent"));}catch(Exception e){log.error("login audit persistence failed: {}",e.getClass().getSimpleName());}}

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BizException.unauthorized("Not authenticated");
        }
        String username = auth.getName();
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            throw BizException.unauthorized("User not found");
        }
        List<String> roles = userService.findRoleCodes(user.getId());
        LoginResponse.UserInfo info = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(), roles,
                List.copyOf(permissionService.effectivePermissionCodes(user.getId())));
        return ApiResponse.ok(info);
    }

    /** Health probe: returns 200 if the JWT secret loaded, never throws. */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
