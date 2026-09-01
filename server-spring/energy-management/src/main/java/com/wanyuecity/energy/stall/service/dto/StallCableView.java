package com.wanyuecity.energy.stall.service.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data public class StallCableView {
 private Long cableSpecificationId; private String specCode; private String cableModel; private String specification; private BigDecimal ratedCurrentA; private BigDecimal ratedPowerKw; private Integer quantity; private BigDecimal totalCurrentA; private BigDecimal totalPowerKw;
 private Long powerSwitchSpecificationId; private String switchSpecCode; private String switchBrand; private String switchModel; private String switchPoleCount; private BigDecimal switchRatedCurrentA; private BigDecimal switchBreakingCapacityKa; private String switchType; private Integer switchQuantity; private String switchMatchStatus;
}
