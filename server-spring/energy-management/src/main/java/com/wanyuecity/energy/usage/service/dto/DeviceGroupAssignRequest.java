package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DeviceGroupAssignRequest(@NotNull Long groupId, @NotEmpty List<String> deviceIds) {}
