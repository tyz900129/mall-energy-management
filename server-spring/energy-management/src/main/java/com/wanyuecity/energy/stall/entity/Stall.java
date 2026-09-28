package com.wanyuecity.energy.stall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stall")
public class Stall {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Business code, e.g. "L503" / "B139". */
    private String stallCode;
    private String stallName;
    private String floorCode;
    private String floorName;
    private String areaName;
    private BigDecimal area;
    private String businessType;
    private String businessCategory;

    /** AVAILABLE / RENOVATING / EXIT_CONSTRUCTION / OCCUPIED / MAINTENANCE / DECOMMISSIONED */
    private String status;

    /** Denormalized "who is in this stall right now" pointer. NULL = empty. */
    private Long currentMerchantId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
