package com.wanyuecity.energy.system.service.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class BusinessDictionaryRequest {
    @NotBlank private String dictType;
    @NotBlank @Size(max=64) private String itemValue;
    @NotBlank @Size(max=64) private String itemLabel;
    private Integer sortOrder = 0;
    private Integer status = 1;
    @Size(max=255) private String remark;
}
