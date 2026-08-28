package com.wanyuecity.energy.platform.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformConfigUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String baseUrl;

    @NotBlank
    @Size(max = 64)
    private String accountUsername;

    /**
     * Plaintext password. Only valid in this request payload, never persisted in this form.
     * If null/empty, the existing password is left unchanged.
     */
    @Size(max = 128)
    private String password;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "\\d{1,10}", message = "spaceId must be digits")
    private String spaceId;

    private Integer enabled;
    private Integer autoSync;

    @Size(max = 64)
    private String syncCron;

    private Integer connectTimeoutMs;

    @Size(max = 255)
    private String remark;
}
