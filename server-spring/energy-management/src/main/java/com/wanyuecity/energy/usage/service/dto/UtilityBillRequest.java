package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UtilityBillRequest(
        @NotNull @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "账单月份格式应为YYYY-MM") String billMonth,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal electricAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 6) BigDecimal electricUsage,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal waterAmount,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 6) BigDecimal waterUsage
) {}
