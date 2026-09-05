package com.wanyuecity.energy.platform.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformDeviceView {
    private String platformDeviceId;
    private String deviceName;
    private String commAddress;
    private String serialNumber;
    private String gatewayId;
    private String gatewayName;
    private String channelName;
    private String productName;
    private String productId;
    private String energyType;
    private String energyTypeName;
    private Integer commStatus;
    private LocalDateTime lastAcqTime;
    private LocalDateTime lastSyncedAt;
    private Long syncBatchId;
}
