package com.wanyuecity.energy.stall.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StallUpsertRequest {

    @NotBlank
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
    private String remark;
}

