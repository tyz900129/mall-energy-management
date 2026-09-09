package com.wanyuecity.energy.facility.service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FacilityTopologyNodeView {
    private Long id;
    private String systemType;
    private Long parentId;
    private String parentName;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String nodeTypeName;
    private String lineSpecCode;
    private String lineSpecName;
    private String equipmentSpecCode;
    private String equipmentSpecName;
    private String mediumType;
    private String mediumTypeName;
    private BigDecimal designFlow;
    private BigDecimal designPressure;
    private BigDecimal slopePercent;
    private BigDecimal elevationM;
    private Integer outletQuantity;
    private String controlComponent;
    private String location;
    private Long stallId;
    private String stallCode;
    private String stallName;
    private String platformDeviceId;
    private String operatingStatus;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FacilityTopologyNodeView> children = new ArrayList<>();
}
