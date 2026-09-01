package com.wanyuecity.energy.system.service.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data public class PowerSwitchSpecificationRequest {
 @NotBlank @Size(max=64) private String specCode; @Size(max=100) private String brand; @NotBlank @Size(max=120) private String switchModel; @NotBlank @Size(max=20) private String poleCount;
 @NotNull @DecimalMin(value="0",inclusive=false) private BigDecimal ratedCurrentA; @DecimalMin(value="0",inclusive=false) private BigDecimal breakingCapacityKa; @Size(max=40) private String switchType;
 private Integer status; @Min(0) private Integer sortOrder; @Size(max=500) private String remark;
}
