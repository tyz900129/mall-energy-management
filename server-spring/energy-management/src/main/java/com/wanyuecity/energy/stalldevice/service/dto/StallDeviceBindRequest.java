package com.wanyuecity.energy.stalldevice.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StallDeviceBindRequest {

    /** 由路径参数填充；body 不再要求，避免先校验后赋值。 */
    private Long stallId;

    @NotBlank
    private String platformDeviceId;

    private String boundBy;
    private String remark;
}

