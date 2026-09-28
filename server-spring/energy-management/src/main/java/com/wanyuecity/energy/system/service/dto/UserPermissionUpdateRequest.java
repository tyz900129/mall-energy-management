package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserPermissionUpdateRequest {
    @Valid
    private List<OverrideItem> overrides = new ArrayList<>();

    @Data
    public static class OverrideItem {
        @NotBlank
        private String permissionCode;
        @NotBlank
        private String effect;
        private LocalDateTime expiresAt;
        private String reason;
    }
}
