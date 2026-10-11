package com.wanyuecity.energy.platform.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PlatformAccountBill {
    private String sourceBillId;
    private String platformAccountId;
    private String accountNumber;
    private LocalDate billDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal amount;
    private LocalDateTime sourceUpdatedAt;
}
