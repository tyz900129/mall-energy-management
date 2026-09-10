package com.wanyuecity.energy.stall.service.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class StallEngineeringRequest {
    @Valid private List<StallCableRequest> cables;
    @Valid private List<StallAirConditionerRequest> airConditioners;
    @Min(0) private Integer cableQuantity; private String cableModel; @DecimalMin("0") private BigDecimal loadPowerKw; private String meterLocation;
    private Boolean waterSupply; private String waterMeterCaliber; private String waterMeterLocation;
    private Long waterSupplyTopologyNodeId;
    private Boolean drainageAvailable; private String drainagePipeCaliber; private String drainagePipelineNo;
    private Long drainageTopologyNodeId;
    private Boolean exhaustDuctAvailable; private String exhaustDuctNo; private String exhaustFanCode;
    private Long oilExhaustTopologyNodeId;
    private Boolean freshAirDuctAvailable; private String freshAirDuctNo; private String freshAirUnitCode;
    private Long freshAirTopologyNodeId;
    @Min(0) private Integer airConditionerQuantity; @Min(0) private Integer fanCoilQuantity; @Min(0) private Integer hangingCabinetQuantity; @Min(0) private Integer airOutletQuantity; private String airConditionerModel;
    private Boolean gasConditionAvailable; private Boolean naturalGasUsed; private Boolean gasConnected; private String gasType; @Min(0) private Integer gasPipeQuantity; private String gasPipeCaliber; private String naturalGasCaliberCode; private String gasPipelineNo; private String gasInterfaceLocation;
    private String gasMeterNo; private String gasMeterModel; private String gasMeterLocation; @DecimalMin("0") private BigDecimal designGasFlow; @DecimalMin("0") private BigDecimal designGasLoadKw; private String gasValveNo;
    private Boolean gasAlarm; @Min(0) private Integer gasAlarmQuantity; private Boolean autoShutoffValve; private String gasShutoffLocationCode; private Boolean gasAccepted; private LocalDate gasAcceptanceDate; private String gasAcceptanceOrg;
    @Size(max=1000) private String remark;
}
