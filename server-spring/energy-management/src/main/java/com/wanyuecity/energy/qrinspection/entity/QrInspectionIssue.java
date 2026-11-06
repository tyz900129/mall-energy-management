package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 部门级整改任务（一次巡检按部门聚合） */
@Data
@TableName("qr_inspection_issue")
public class QrInspectionIssue {
    @TableId(type = IdType.AUTO) private Long id;
    private Long recordId;
    private Long taskId;
    private Long departmentId;
    private String deptCode;
    private String deptName;
    private Long stallId;
    private String summaryText;
    /** [{fieldCode,fieldLabel,category,abnormalValue,photoUrl}] */
    private String itemsJson;
    private String accessToken;
    private LocalDateTime deadlineAt;
    /** PENDING / FEEDBACK / CLOSED / OVERDUE */
    private String status;
    private String feedbackText;
    private String feedbackPhotosJson;
    private String feedbackBy;
    private String feedbackByName;
    private LocalDateTime feedbackAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
}
