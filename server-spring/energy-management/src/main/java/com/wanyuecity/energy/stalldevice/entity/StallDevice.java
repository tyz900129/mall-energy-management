package com.wanyuecity.energy.stalldevice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stall_device")
public class StallDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stallId;
    private String platformDeviceId;
    private String energyType;

    /** ACTIVE / INACTIVE. */
    private String status;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    private String boundBy;
    private String unboundBy;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

