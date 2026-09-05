package com.wanyuecity.energy.system.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("air_conditioner_specification") public class AirConditionerSpecification {
 @TableId(type=IdType.AUTO) private Long id;private String specCode;private String model;private String equipmentType;private java.math.BigDecimal ratedPowerKw;private Integer airflowCmh;private Integer status;private Integer sortOrder;private String remark;private LocalDateTime createdAt;private LocalDateTime updatedAt;
}
