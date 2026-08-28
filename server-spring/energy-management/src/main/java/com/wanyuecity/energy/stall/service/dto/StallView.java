package com.wanyuecity.energy.stall.service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StallView {
    private Long id;
    private String stallCode;
    private String stallName;
    private String floorCode;
    private String floorName;
    private String areaName;
    private BigDecimal area;
    private String businessType;
    private String businessCategory;
    private String status;
    private Long currentMerchantId;
    private String currentMerchantCode;
    private String currentMerchantName;
    private String accountNumber;
    private String accountMobilePhone;
    private String accountStatus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

