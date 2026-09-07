package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UsageSyncExclusionRequest(
        @NotEmpty List<@NotBlank @Size(max = 64) String> deviceIds,
        @Size(max = 255) String reason) {
}
