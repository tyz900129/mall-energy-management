package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_config")
public class PlatformConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String platformName;
    private String baseUrl;
    private String accountUsername;
    /** AES-GCM ciphertext (Base64). Never logged. */
    private String passwordCiphertext;
    /** GCM IV (Base64, 12 bytes). Never logged. */
    private String passwordIv;

    private String spaceId;
    private Integer enabled;
    private Integer autoSync;
    private String syncCron;
    private Integer connectTimeoutMs;

    private LocalDateTime lastConnectAt;
    private String lastConnectStatus;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;
    private Long lastSyncBatchId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
