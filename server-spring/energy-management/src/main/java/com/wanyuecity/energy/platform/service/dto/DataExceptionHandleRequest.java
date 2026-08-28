package com.wanyuecity.energy.platform.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataExceptionHandleRequest {
    @NotBlank
    private String status;
    private String handleResult;
}

