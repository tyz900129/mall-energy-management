package com.wanyuecity.energy.stall.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("stall_engineering_air_conditioner") public class StallEngineeringAirConditioner {@TableId(type=IdType.AUTO) private Long id;private Long stallEngineeringProfileId;private String equipmentSpecCode;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
