package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 周期计划轮次（每推送一次生成一条，用于追踪当月是否完成巡检） */
@Data
@TableName("qr_inspection_plan_round")
public class QrInspectionPlanRound {
    @TableId(type = IdType.AUTO) private Long id;
    private Long planId;
    private LocalDate dueDate;
    /** PENDING / COMPLETED */
    private String status;
    private LocalDateTime pushedAt;
    private LocalDateTime completedAt;
    private Integer recordCount;
    private LocalDateTime createdAt;
}
