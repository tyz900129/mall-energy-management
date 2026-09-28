package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("construction_acceptance")
public class ConstructionAcceptance {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private Integer roundNo;
    private LocalDateTime actualCompletionTime;
    private String applicationNote;
    private String status;
    private String result;
    private String resultNote;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String inspectedBy;
    private LocalDateTime inspectedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
