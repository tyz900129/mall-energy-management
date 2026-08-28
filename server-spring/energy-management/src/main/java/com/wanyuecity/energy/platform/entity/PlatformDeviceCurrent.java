package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_device_current")
public class PlatformDeviceCurrent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platformDeviceId;
    private String deviceName;
    private String commAddress;
    private String serialNumber;
    private String gatewayId;
    private String gatewayName;
    private String channelId;
    private String channelName;
    private String productId;
    private String productName;
    private String energyType;
    private String energyTypeName;
    private String deviceSystem;
    private String deviceSystemName;
    private Integer commStatus;
    private LocalDateTime lastAcqTime;
    private Long syncBatchId;
    private LocalDateTime lastSyncedAt;
    private String rawJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
