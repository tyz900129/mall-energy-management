package com.wanyuecity.energy.power.service.dto;
import jakarta.validation.constraints.*;import lombok.Data;import java.math.BigDecimal;
@Data public class PowerTopologyNodeRequest {
 private Long parentId; @NotBlank @Size(max=64) private String nodeCode; @NotBlank @Size(max=120) private String nodeName; @NotBlank @Size(max=64) private String nodeType; private Long switchSpecificationId;
 @DecimalMin("0") private BigDecimal ratedVoltageV; @DecimalMin("0") private BigDecimal ratedCurrentA; @DecimalMin("0") private BigDecimal ratedPowerKw; @Size(max=200) private String location; private Long stallId; @Size(max=100) private String platformDeviceId; @Min(0) private Integer sortOrder; private Integer status; @Size(max=500) private String remark;
}
