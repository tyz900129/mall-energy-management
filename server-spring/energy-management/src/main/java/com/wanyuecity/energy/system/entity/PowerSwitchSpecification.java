package com.wanyuecity.energy.system.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @TableName("power_switch_specification") public class PowerSwitchSpecification {
 @TableId(type=IdType.AUTO) private Long id; private String specCode; private String brand; private String switchModel; private String poleCount; private BigDecimal ratedCurrentA; private BigDecimal breakingCapacityKa; private String switchType; private Integer status; private Integer sortOrder; private String remark; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
