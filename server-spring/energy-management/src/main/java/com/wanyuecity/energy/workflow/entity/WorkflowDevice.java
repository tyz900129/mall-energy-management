package com.wanyuecity.energy.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_device")
public class WorkflowDevice {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private String platformDeviceId;
    private String deviceNameSnapshot;
    private String energyTypeSnapshot;
    private String commAddressSnapshot;
    private String serialNumberSnapshot;
    private String gatewayNameSnapshot;
    private String channelNameSnapshot;
    private String productNameSnapshot;
    private String meterPhaseSnapshot;
    private String oldAccountNumber;
    private String targetAccountNumber;
    private String operationType;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
