package com.wanyuecity.energy.stall.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("stall_engineering_cable") public class StallEngineeringCable {
 @TableId(type=IdType.AUTO) private Long id; private Long stallEngineeringProfileId; private Long cableSpecificationId; private Long powerSwitchSpecificationId; private Integer quantity; private Integer switchQuantity; private String meterLocationCode; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
