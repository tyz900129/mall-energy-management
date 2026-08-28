package com.wanyuecity.energy.platform.service.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlatformAccountView {
    private String platformAccountId;
    private String accountNumber;
    private String accountName;
    private String merchantCode;
    private String merchantCodeSource;
    private String userName;
    private String mobilePhone;
    private BigDecimal balance;
    private BigDecimal arrears;
    private String platformStatus;
    private List<String> stallCodes;
    private LocalDateTime lastSyncedAt;
}
