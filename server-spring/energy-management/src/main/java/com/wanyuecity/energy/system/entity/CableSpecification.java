package com.wanyuecity.energy.system.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @TableName("cable_specification")
public class CableSpecification {
 @TableId(type=IdType.AUTO) private Long id; private String specCode; private String cableModel; private String specification;
 private BigDecimal ratedCurrentA; private BigDecimal ratedPowerKw; private Integer status; private Integer sortOrder; private String remark;
 private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
