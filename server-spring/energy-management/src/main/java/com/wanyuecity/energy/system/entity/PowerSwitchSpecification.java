package com.wanyuecity.energy.system.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @TableName("power_switch_specification") public class PowerSwitchSpecification {
 @TableId(type=IdType.AUTO) private Long id; private String specCode; @TableField(updateStrategy=FieldStrategy.ALWAYS) private String brand; private String switchModel; private String poleCount; private BigDecimal ratedCurrentA; @TableField(updateStrategy=FieldStrategy.ALWAYS) private BigDecimal breakingCapacityKa; @TableField(updateStrategy=FieldStrategy.ALWAYS) private String switchType; private Integer status; private Integer sortOrder; @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
