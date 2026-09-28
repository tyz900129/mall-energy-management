package com.wanyuecity.energy.workflow.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConstructionWorkflowView {
    private Long id;
    private String workflowNo;
    private String workflowType;
    private String permitNo;
    private String publicToken;
    private String merchantName;
    private Long stallId;
    private String stallCode;
    private String stallName;
    private String floorCode;
    private String floorName;
    private String currentStallStatus;
    private LocalDateTime constructionStartTime;
    private LocalDateTime constructionEndTime;
    private String merchantPrincipalName;
    private String merchantPrincipalPhone;
    private String constructionCompanyName;
    private String constructionPrincipalName;
    private String constructionPrincipalPhone;
    private String propertyEngineerName;
    private String propertyEngineerPhone;
    private String commercialManagerName;
    private String commercialManagerPhone;
    private Long relatedWorkflowId;
    private String relatedWorkflowNo;
    private LocalDateTime acceptanceRequestedAt;
    private java.util.List<com.wanyuecity.energy.workflow.entity.ConstructionInspection> inspections;
    private java.util.List<com.wanyuecity.energy.workflow.entity.ConstructionAcceptance> acceptances;
    private String status;
    private String applicantName;
    private String remark;
    private LocalDateTime submittedAt;
    private String stallStatusBefore;
    private String stallStatusAfter;
    private String completedBy;
    private LocalDateTime completedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
