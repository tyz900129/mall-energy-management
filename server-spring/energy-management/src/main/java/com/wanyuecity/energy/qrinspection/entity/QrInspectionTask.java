package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 联合巡检任务（同时期仅 1 个 ACTIVE，现场随机巡检，不预选铺位） */
@Data
@TableName("qr_inspection_task")
public class QrInspectionTask {
    @TableId(type = IdType.AUTO) private Long id;
    private String taskNo;
    private String name;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** ACTIVE / CLOSED */
    private String status;
    private Long templateId;
    private Integer templateVersion;
    /** 参与部门编码，逗号分隔；为空表示全体可参与 */
    private String participatingDeptCodes;
    private String createdBy;
    private String createdByName;
    private String closedBy;
    private LocalDateTime closedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
