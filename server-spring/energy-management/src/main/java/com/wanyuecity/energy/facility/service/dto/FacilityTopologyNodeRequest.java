package com.wanyuecity.energy.facility.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FacilityTopologyNodeRequest {
    private Long parentId;
    @NotBlank @Size(max = 64) private String nodeCode;
    @NotBlank @Size(max = 120) private String nodeName;
    @NotBlank @Size(max = 64) private String nodeType;
    @Size(max = 64) private String lineSpecCode;
    @Size(max = 64) private String equipmentSpecCode;
    @Size(max = 64) private String mediumType;
    @DecimalMin("0") private BigDecimal designFlow;
    @DecimalMin("0") private BigDecimal designPressure;
    @DecimalMin("0") private BigDecimal slopePercent;
    private BigDecimal elevationM;
    @Min(0) private Integer outletQuantity;
    @Size(max = 200) private String controlComponent;
    @Size(max = 200) private String location;
    private Long stallId;
    @Size(max = 100) private String platformDeviceId;
    @Size(max = 20) private String operatingStatus;
    @Min(0) private Integer sortOrder;
    private Integer status;
    @Size(max = 500) private String remark;
}
