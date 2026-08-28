package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("platform_account_current")
public class PlatformAccountCurrent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platformAccountId;
    private String accountNumber;
    private String accountName;
    private String merchantCode;
    private String merchantCodeSource;
    private String merchantCodeMatchStatus;
    private String userName;
    private String contactName;
    private String mobilePhone;
    private BigDecimal balance;
    private BigDecimal arrears;
    private String platformStatus;
    private String sourceSpaceId;
    private Long syncBatchId;
    private LocalDateTime lastSyncedAt;
    private String rawJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

