package com.wanyuecity.energy.verification.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("field_verification_task") public class FieldVerificationTask {
 @TableId(type=IdType.AUTO) private Long id; private String taskNo; private Long workflowId; private String accessToken; private String status;
 private Long assignedUserId; private String assignedUserName; private LocalDateTime openedAt; private LocalDateTime startedAt; private LocalDateTime completedAt;
 private String verificationResult; private String remark; @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}

