package com.wanyuecity.energy.verification.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data @TableName("field_verification_device") public class FieldVerificationDevice {
 @TableId(type=IdType.AUTO) private Long id; private Long verificationTaskId; private Long workflowDeviceId; private String platformDeviceId;
 private String deviceNameSnapshot; private String energyType; private String productNameSnapshot; private String meterPhase; private String expectedCommAddress; private String expectedSerialNumber;
 private String onsiteMeterNumber; private String onsiteSerialNumber; private BigDecimal onsiteReading; private BigDecimal onsiteMultiplier; private String readingUnit;
 private Boolean deviceMatched; private Boolean serialMatched; private Boolean addressMatched; private String verificationResult; private LocalDateTime verifiedAt; private String remark;
 @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt; @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}

