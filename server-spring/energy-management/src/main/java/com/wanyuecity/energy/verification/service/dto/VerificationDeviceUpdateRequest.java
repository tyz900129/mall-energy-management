package com.wanyuecity.energy.verification.service.dto;
import jakarta.validation.constraints.*; import lombok.Data; import java.math.BigDecimal;
@Data public class VerificationDeviceUpdateRequest {
 private String onsiteMeterNumber; private String onsiteSerialNumber; @NotNull @DecimalMin("0.0") private BigDecimal onsiteReading;
 @DecimalMin(value="0.0001",message="倍率必须大于0") private BigDecimal onsiteMultiplier;
 private Boolean deviceMatched; private Boolean serialMatched; private Boolean addressMatched; private String verificationResult; private String remark;
}
