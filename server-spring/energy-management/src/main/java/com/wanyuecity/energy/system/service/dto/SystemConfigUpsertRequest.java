package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemConfigUpsertRequest {

    @NotBlank
    @Size(max = 64)
    private String configKey;

    @Size(max = 4096)
    private String configValue;

    @Size(max = 255)
    private String description;
}

