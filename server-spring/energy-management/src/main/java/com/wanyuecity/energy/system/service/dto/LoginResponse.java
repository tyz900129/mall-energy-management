package com.wanyuecity.energy.system.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private long expiresInMinutes;
    private UserInfo user;
    private String deviceToken;

    public LoginResponse(String token, long expiresInMinutes, UserInfo user) {
        this(token, expiresInMinutes, user, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private String email;
        private List<String> roles;
    }
}
