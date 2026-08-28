package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_account_device_current")
public class PlatformAccountDeviceCurrent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountNumber;
    private String platformDeviceId;
    private Long syncBatchId;
    private LocalDateTime lastSyncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

