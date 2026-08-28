package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceLoginRequest {
    @NotBlank @Size(max=128) private String deviceToken;
    @NotBlank @Size(max=512) private String fingerprint;
}
