package com.wanyuecity.energy.merchant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Six-digit business code, e.g. "000001". VARCHAR(6) is intentional — leading zeros matter. */
    private String merchantCode;
    private String merchantCodeSource;
    private String merchantCodeMatchStatus;

    private String merchantName;
    private String contactName;
    private String contactPhone;

    /** Loose link to a source-platform account (may be null for purely offline merchants). */
    private String sourcePlatformAccountId;

    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

