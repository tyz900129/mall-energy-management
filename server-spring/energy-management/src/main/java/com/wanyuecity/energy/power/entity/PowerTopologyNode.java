package com.wanyuecity.energy.power.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.math.BigDecimal;import java.time.LocalDateTime;
@Data @TableName("power_topology_node") public class PowerTopologyNode {
 @TableId(type=IdType.AUTO) private Long id; private Long parentId; private String nodeCode; private String nodeName; private String nodeType; private Long switchSpecificationId; private BigDecimal ratedVoltageV; private BigDecimal ratedCurrentA; private BigDecimal ratedPowerKw; private String location; private Long stallId; private String platformDeviceId; private Integer sortOrder; private Integer status; private String remark; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
