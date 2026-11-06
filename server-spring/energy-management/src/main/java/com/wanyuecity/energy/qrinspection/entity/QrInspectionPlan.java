package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 周期巡检计划（部门级：每月某日某时推送一次） */
@Data
@TableName("qr_inspection_plan")
public class QrInspectionPlan {
    @TableId(type = IdType.AUTO) private Long id;
    /** WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY */
    private String scene;
    private String deptCode;
    private Long deptId;
    /** 为空时按场景+角色自动匹配模板 */
    private Long templateId;
    /** 每月几号（1-31） */
    private Integer dayOfMonth;
    /** HH:mm */
    private String pushTime;
    private Integer enabled;
    private LocalDate lastRunDate;
    private LocalDateTime nextDueAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
