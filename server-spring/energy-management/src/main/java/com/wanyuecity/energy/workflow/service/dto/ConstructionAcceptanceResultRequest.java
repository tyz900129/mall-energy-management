package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConstructionAcceptanceResultRequest {
    @NotBlank @Size(max = 24) private String result;
    @Size(max = 1000) private String resultNote;
}
