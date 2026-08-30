package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow")
public class Workflow {
    @TableId(type = IdType.AUTO) private Long id;
    private String workflowNo;
    private String requestKey;
    private String workflowType;
    private String merchantCode;
    private String merchantName;
    private String contactName;
    private String contactPhone;
    private Long stallId;
    private String targetAccountNumber;
    private LocalDateTime requestedBillingStartTime;
    private Long applicantId;
    private String applicantName;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime fieldVerifiedAt;
    private LocalDateTime waitingPlatformAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime completedAt;
    private String expectedAccountAction;
    private String remark;
    private String extraData;
    private Boolean usesWater;
    private String oaProcessNo;
    private String completionRemark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
