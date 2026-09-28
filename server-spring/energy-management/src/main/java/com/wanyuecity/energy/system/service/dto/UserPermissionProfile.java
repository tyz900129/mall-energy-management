package com.wanyuecity.energy.system.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionProfile {
    private Long userId;
    private String username;
    private List<PermissionModule> modules;
    private List<String> rolePermissionCodes;
    private List<String> effectivePermissionCodes;
    private List<UserOverride> overrides;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionModule {
        private String code;
        private String name;
        private List<PermissionItem> permissions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionItem {
        private Long id;
        private String code;
        private String name;
        private String actionType;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserOverride {
        private String permissionCode;
        private String effect;
        private LocalDateTime expiresAt;
        private String reason;
    }
}
