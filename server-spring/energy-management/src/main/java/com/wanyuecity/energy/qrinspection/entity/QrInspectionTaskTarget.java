package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 联合巡检实际巡检铺位（提交时自动写入） */
@Data
@TableName("qr_inspection_task_target")
public class QrInspectionTaskTarget {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long stallId;
    private Long recordId;
    private String selectedDeptCode;
    /** SUBMITTED */
    private String status;
    private LocalDateTime submittedAt;
    private String submittedBy;
}
