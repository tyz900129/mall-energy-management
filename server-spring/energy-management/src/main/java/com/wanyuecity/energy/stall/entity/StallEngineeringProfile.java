package com.wanyuecity.energy.stall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stall_engineering_profile")
public class StallEngineeringProfile {
    @TableId(type = IdType.AUTO) private Long id;
    private Long stallId;
    private Integer cableQuantity; private String cableModel; private BigDecimal loadPowerKw; private String meterLocation;
    private Boolean waterSupply; private String waterMeterCaliber; private String waterMeterLocation;
    private Boolean drainageAvailable; private String drainagePipeCaliber; private String drainagePipelineNo;
    private Boolean exhaustDuctAvailable; private String exhaustDuctNo; private String exhaustFanCode;
    private Boolean freshAirDuctAvailable; private String freshAirDuctNo; private String freshAirUnitCode;
    private Integer airConditionerQuantity; private Integer fanCoilQuantity; private Integer hangingCabinetQuantity; private Integer airOutletQuantity; private String airConditionerModel;
    private Boolean gasConditionAvailable; private Boolean naturalGasUsed; private Boolean gasConnected; private String gasType; private Integer gasPipeQuantity; private String gasPipeCaliber; private String naturalGasCaliberCode; private String gasPipelineNo; private String gasInterfaceLocation;
    private String gasMeterNo; private String gasMeterModel; private String gasMeterLocation; private BigDecimal designGasFlow; private BigDecimal designGasLoadKw; private String gasValveNo;
    private Boolean gasAlarm; private Integer gasAlarmQuantity; private Boolean autoShutoffValve; private String gasShutoffLocationCode; private Boolean gasAccepted; private LocalDate gasAcceptanceDate; private String gasAcceptanceOrg;
    private String remark;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
