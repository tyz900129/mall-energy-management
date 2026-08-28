package com.wanyuecity.energy.merchant.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MerchantUpsertRequest {

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "merchantCode must be exactly 6 digits")
    private String merchantCode;

    private String merchantCodeSource;
    private String merchantCodeMatchStatus;

    @NotBlank
    private String merchantName;

    private String contactName;
    private String contactPhone;
    private String sourcePlatformAccountId;
    private String status;
    private String remark;
}

