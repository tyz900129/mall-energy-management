package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConstructionInspectionTemplateRequest {
    @NotBlank @Size(max = 100) private String name;
    @NotBlank private String fieldsJson;
    private Boolean enabled;
}
