package com.wanyuecity.energy.stall.service.dto;
import lombok.AllArgsConstructor;import lombok.Data;
@Data @AllArgsConstructor public class StallAirConditionerView {private Long airConditionerSpecificationId;private String specCode;private String model;private String equipmentType;private String equipmentTypeName;private java.math.BigDecimal ratedPowerKw;private Integer airflowCmh;private Integer airOutletQuantity;}
