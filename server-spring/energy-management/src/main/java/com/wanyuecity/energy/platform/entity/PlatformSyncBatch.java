package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_sync_batch")
public class PlatformSyncBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** RUNNING / SUCCESS / FAILED / PARTIAL_FAILED */
    private String status;

    private Integer accountCount;
    private Integer deviceCount;
    private Integer relationCount;
    private Integer exceptionCount;

    private String errorMessage;
    /** AUTO / MANUAL */
    private String triggerType;
    private Long triggerUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

