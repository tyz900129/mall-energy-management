package com.wanyuecity.energy.system.service.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data public class CableSpecificationRequest {
 @NotBlank @Size(max=64) private String specCode; @NotBlank @Size(max=100) private String cableModel; @NotBlank @Size(max=160) private String specification;
 @NotNull @DecimalMin(value="0",inclusive=false) private BigDecimal ratedCurrentA; @NotNull @DecimalMin(value="0",inclusive=false) private BigDecimal ratedPowerKw;
 private Integer status; @Min(0) private Integer sortOrder; @Size(max=500) private String remark;
}
