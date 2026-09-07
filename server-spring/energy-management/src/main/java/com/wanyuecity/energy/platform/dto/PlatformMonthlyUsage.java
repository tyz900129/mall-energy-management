package com.wanyuecity.energy.platform.dto;
import java.math.BigDecimal;import java.time.LocalDate;
public record PlatformMonthlyUsage(String platformDeviceId,LocalDate usageMonth,String identifier,String identifierName,BigDecimal value,String rawJson){}
