package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsageSyncExclusionUpdateRequest(
        @NotNull Boolean enabled,
        @Size(max = 255) String reason) {
}
