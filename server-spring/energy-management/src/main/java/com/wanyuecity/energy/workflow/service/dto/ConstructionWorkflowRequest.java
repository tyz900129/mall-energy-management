package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConstructionWorkflowRequest {
    @Size(max = 64) private String requestKey;
    @NotBlank @Size(max = 48) private String workflowType;
    @NotNull private Long stallId;
    @NotNull private LocalDateTime constructionStartTime;
    @NotNull private LocalDateTime constructionEndTime;
    @NotBlank @Size(max = 128) private String merchantName;
    @NotBlank @Size(max = 64) private String merchantPrincipalName;
    @NotBlank @Size(max = 32) private String merchantPrincipalPhone;
    @NotBlank @Size(max = 128) private String constructionCompanyName;
    @NotBlank @Size(max = 64) private String constructionPrincipalName;
    @NotBlank @Size(max = 32) private String constructionPrincipalPhone;
    @Size(max = 64) private String propertyEngineerName;
    @Size(max = 32) private String propertyEngineerPhone;
    @Size(max = 64) private String commercialManagerName;
    @Size(max = 32) private String commercialManagerPhone;
    @Size(max = 500) private String remark;
}
