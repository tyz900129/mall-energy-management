package com.wanyuecity.energy.power.service.dto;
import lombok.Data;import java.math.BigDecimal;import java.time.LocalDateTime;import java.util.ArrayList;import java.util.List;
@Data public class PowerTopologyNodeView {
 private Long id;private Long parentId;private String nodeCode;private String nodeName;private String nodeType;private String nodeTypeName;private Long switchSpecificationId;private String switchSpecificationName;private BigDecimal switchRatedCurrentA;private BigDecimal ratedVoltageV;private BigDecimal ratedCurrentA;private BigDecimal ratedPowerKw;private String location;private Long stallId;private String stallCode;private String platformDeviceId;private Integer sortOrder;private Integer status;private String remark;private LocalDateTime createdAt;private LocalDateTime updatedAt;private List<PowerTopologyNodeView> children=new ArrayList<>();
}
