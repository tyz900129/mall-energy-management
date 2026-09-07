package com.wanyuecity.energy.usage.service.dto;
import jakarta.validation.constraints.*;import java.time.LocalDate;import java.util.List;
public record DeviceDimensionAssignRequest(@NotNull Long dimensionId,@NotNull Long dimensionValueId,@NotNull LocalDate effectiveFrom,@NotEmpty List<String> deviceIds){}
