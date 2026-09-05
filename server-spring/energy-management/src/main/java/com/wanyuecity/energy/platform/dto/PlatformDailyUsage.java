package com.wanyuecity.energy.platform.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlatformDailyUsage(
        String platformDeviceId,
        LocalDate usageDate,
        String identifier,
        String identifierName,
        BigDecimal value,
        String rawJson) {
}
