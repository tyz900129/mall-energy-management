package com.wanyuecity.energy.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Read-only mirror of a source-platform device. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformDevice {

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

    /** WATER / ELECTRIC / GAS. Mapped from deviceSystemName in the source. */
    private String energyType;
    private String energyTypeName;

    private String deviceSystem;
    private String deviceSystemName;

    private Integer commStatus;
    private String lastAcqTime;
}

