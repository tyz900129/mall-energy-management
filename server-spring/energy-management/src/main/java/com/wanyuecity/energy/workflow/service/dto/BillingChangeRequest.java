package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BillingChangeRequest {
    @NotBlank private String workflowType;
    @NotBlank @Pattern(regexp = "\\d{6}") private String merchantCode;
    private String merchantName;
    private String contactName;
    private String contactPhone;
    private Long stallId;
    private LocalDateTime effectiveTime;
    private Boolean usesWater;
    private String remark;
}

