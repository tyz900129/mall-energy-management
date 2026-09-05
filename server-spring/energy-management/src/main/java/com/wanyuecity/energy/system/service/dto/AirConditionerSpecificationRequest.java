package com.wanyuecity.energy.system.service.dto;
import jakarta.validation.constraints.*;import lombok.Data;
@Data public class AirConditionerSpecificationRequest {
 @NotBlank @Size(max=64) private String specCode;@NotBlank @Size(max=128) private String model;@NotBlank @Size(max=32) private String equipmentType;@NotNull @DecimalMin(value="0",inclusive=false) private java.math.BigDecimal ratedPowerKw;@NotNull @Min(1) private Integer airflowCmh;private Integer status;@Min(0) private Integer sortOrder;@Size(max=500) private String remark;
}
