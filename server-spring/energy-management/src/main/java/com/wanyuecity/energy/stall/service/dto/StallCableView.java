package com.wanyuecity.energy.stall.service.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data public class StallCableView {
 private Long cableSpecificationId; private String specCode; private String cableModel; private String specification; private BigDecimal ratedCurrentA; private BigDecimal ratedPowerKw; private Integer quantity; private BigDecimal totalCurrentA; private BigDecimal totalPowerKw;
}
