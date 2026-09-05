package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotBlank;

public record EnergyMetricRequest(@NotBlank String productId, @NotBlank String energyType,
                                  @NotBlank String identifier, @NotBlank String identifierName,
                                  String valueMode) {}
