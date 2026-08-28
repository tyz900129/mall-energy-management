package com.wanyuecity.energy.stalldevice.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StallDeviceView {
    private Long id;
    private Long stallId;
    private String stallCode;
    private String stallName;
    private String platformDeviceId;
    private String deviceName;
    private String energyType;
    private String commAddress;
    private String serialNumber;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String boundBy;
    private String unboundBy;
    private String remark;
    private LocalDateTime createdAt;
}
