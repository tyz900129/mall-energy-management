package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("construction_inspection")
public class ConstructionInspection {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private String inspectionType;
    private String templateSnapshotJson;
    private String answersJson;
    private String result;
    private String problemDescription;
    private String rectificationRequirement;
    private LocalDateTime rectificationDeadline;
    private String rectificationStatus;
    private Long inspectorUserId;
    private String inspectorName;
    private String inspectorPhone;
    private LocalDateTime inspectedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
