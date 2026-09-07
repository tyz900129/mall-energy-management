package com.wanyuecity.energy.usage.service.dto;
import jakarta.validation.constraints.*;
public record AnalysisDimensionValueRequest(@NotBlank @Size(max=64) String valueCode,@NotBlank @Size(max=100) String valueName,@Size(max=16) String color,@Min(0) Integer sortOrder,Integer status,@Size(max=255) String remark){}
