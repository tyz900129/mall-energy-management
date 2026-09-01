package com.wanyuecity.energy.stall.service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StallEngineeringRequest {
    @Min(0) private Integer cableQuantity; private String cableModel; @DecimalMin("0") private BigDecimal loadPowerKw; private String meterLocation;
    private Boolean waterSupply; private String waterMeterCaliber; private String waterMeterLocation;
    private Boolean drainageAvailable; private String drainagePipeCaliber; private String drainagePipelineNo;
    private Boolean exhaustDuctAvailable; private String exhaustDuctNo;
    private Boolean freshAirDuctAvailable; private String freshAirDuctNo;
    @Min(0) private Integer airConditionerQuantity; @Min(0) private Integer fanCoilQuantity; @Min(0) private Integer hangingCabinetQuantity; @Min(0) private Integer airOutletQuantity; private String airConditionerModel;
    private Boolean gasConditionAvailable; private Boolean gasConnected; private String gasType; @Min(0) private Integer gasPipeQuantity; private String gasPipeCaliber; private String gasPipelineNo; private String gasInterfaceLocation;
    private String gasMeterNo; private String gasMeterModel; private String gasMeterLocation; @DecimalMin("0") private BigDecimal designGasFlow; @DecimalMin("0") private BigDecimal designGasLoadKw; private String gasValveNo;
    private Boolean gasAlarm; @Min(0) private Integer gasAlarmQuantity; private Boolean autoShutoffValve; private Boolean gasAccepted; private LocalDate gasAcceptanceDate; private String gasAcceptanceOrg;
    @Size(max=1000) private String remark;
}
