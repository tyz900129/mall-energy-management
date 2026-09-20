package com.wanyuecity.energy.usage.service.dto;
import jakarta.validation.constraints.*;import java.util.List;
public record DeviceDimensionAssignRequest(@NotNull Long dimensionId,Long dimensionValueId,@NotEmpty List<String> deviceIds){}
