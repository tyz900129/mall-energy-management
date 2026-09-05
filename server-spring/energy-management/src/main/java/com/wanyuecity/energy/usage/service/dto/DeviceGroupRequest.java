package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceGroupRequest(@NotBlank String groupName, String color, Integer sortOrder, String remark) {}
