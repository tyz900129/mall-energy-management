package com.wanyuecity.energy.merchant.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantView {
    private Long id;
    private String merchantCode;
    private String merchantCodeSource;
    private String merchantCodeMatchStatus;
    private String merchantName;
    private String contactName;
    private String contactPhone;
    private String sourcePlatformAccountId;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

