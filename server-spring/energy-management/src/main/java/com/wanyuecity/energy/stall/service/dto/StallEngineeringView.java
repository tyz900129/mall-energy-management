package com.wanyuecity.energy.stall.service.dto;
import com.wanyuecity.energy.stall.entity.StallEngineeringProfile;
import lombok.Data;
@Data public class StallEngineeringView {private Long stallId;private String stallCode;private String stallName;private String floorCode;private String businessType;private StallEngineeringProfile profile;}
