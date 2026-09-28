package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("construction_workflow_detail")
public class ConstructionWorkflowDetail {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private String permitNo;
    private String publicToken;
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
    private LocalDateTime acceptanceRequestedAt;
    private String stallStatusBefore;
    private String stallStatusAfter;
    private Long previousCurrentMerchantId;
    private String completedBy;
    private LocalDateTime completedAt;
    private LocalDateTime archivedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
