package com.wanyuecity.energy.stall.service.dto;
import jakarta.validation.constraints.Min;import jakarta.validation.constraints.NotNull;import lombok.Data;
@Data public class StallAirConditionerRequest {@NotNull private Long airConditionerSpecificationId;@NotNull @Min(0) private Integer airOutletQuantity;}
