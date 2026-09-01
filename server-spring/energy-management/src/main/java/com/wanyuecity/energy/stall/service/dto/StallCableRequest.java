package com.wanyuecity.energy.stall.service.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class StallCableRequest { @NotNull private Long cableSpecificationId; @NotNull @Min(1) private Integer quantity; }
