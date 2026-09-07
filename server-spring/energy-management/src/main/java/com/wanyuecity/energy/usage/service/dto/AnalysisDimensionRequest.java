package com.wanyuecity.energy.usage.service.dto;
import jakarta.validation.constraints.*;
public record AnalysisDimensionRequest(@NotBlank @Size(max=64) String dimensionCode,@NotBlank @Size(max=100) String dimensionName,@Min(0) Integer sortOrder,Integer status,@Size(max=255) String remark){}
