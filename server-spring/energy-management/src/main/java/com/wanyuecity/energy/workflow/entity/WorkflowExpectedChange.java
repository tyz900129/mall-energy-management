package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_expected_change")
public class WorkflowExpectedChange {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private String changeType;
    private String platformDeviceId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private LocalDateTime expectedEffectiveTime;
    private String verifyStatus;
    private String actualAccountNumber;
    private LocalDateTime verifiedAt;
    private String exceptionReason;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
