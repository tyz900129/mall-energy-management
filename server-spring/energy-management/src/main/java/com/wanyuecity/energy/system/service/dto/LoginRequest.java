package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    private Boolean rememberDevice;
    @Size(max = 512) private String fingerprint;
    @Size(max = 120) private String deviceName;
}
