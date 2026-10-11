package com.wanyuecity.energy.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Read-only mirror of a source-platform account. Field semantics follow
 * 《水电费系统结构.md》; unused JSON fields are captured in {@code rawJson}
 * (omitted from this DTO) for debugging without polluting the schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {

    /** Source-platform internal numeric id (stringified to avoid 64-bit loss). */
    private String platformAccountId;

    /** Public-facing account number (always a string per the source). */
    private String accountNumber;

    /** User-facing account label (often "L404-沪上阿姨"). */
    private String accountName;

    /** Owner display name (often "3066-000001-沪上阿姨"). */
    private String userName;

    private String mobilePhone;

    private BigDecimal balance;
    private BigDecimal arrears;
    private String arrearsStartedAt;

    /** "0" = disabled, "1" = enabled, etc. Strings to keep the source semantics. */
    private String platformStatus;

    /** Project/space id as observed on the source side. */
    private String sourceSpaceId;
}
