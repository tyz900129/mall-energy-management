package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cs_complaint")
public class CsComplaint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String complaintNo;
    private LocalDate occurDate;

    private Long stallId;
    private Long brandId;
    private String brandName;

    private Long merchantId;
    private String merchantName;

    private String businessType;
    private String businessCategory;
    private String floorCode;

    private String category;
    private String channel;
    private String content;
    private String solution;

    private BigDecimal companyCost;
    private BigDecimal merchantCost;
    private Boolean noCost;
    private Boolean onSiteHandle;

    private Integer handleDays;

    private String status;
    private String satisfaction;
    private LocalDateTime closedAt;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
