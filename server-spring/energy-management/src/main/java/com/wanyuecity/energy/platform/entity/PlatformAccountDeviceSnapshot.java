package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("platform_account_device_snapshot")
public class PlatformAccountDeviceSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    @TableField("sync_batch_id")
    private Long syncBatchId;

    @TableField("payload")
    private String payload;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

