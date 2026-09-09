package com.wanyuecity.energy.facility.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("facility_topology_node")
public class FacilityTopologyNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String systemType;
    private Long parentId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String lineSpecCode;
    private String equipmentSpecCode;
    private String mediumType;
    private BigDecimal designFlow;
    private BigDecimal designPressure;
    private BigDecimal slopePercent;
    private BigDecimal elevationM;
    private Integer outletQuantity;
    private String controlComponent;
    private String location;
    private Long stallId;
    private String platformDeviceId;
    private String operatingStatus;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
