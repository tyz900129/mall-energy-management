package com.wanyuecity.energy.platform.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wanyuecity.energy.platform.entity.PlatformConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformConfigView {

    private Long id;
    private String platformName;
    private String baseUrl;
    private String accountUsername;
    /** Never set on the view; deliberately absent. */
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** True if the password is configured. */
    private boolean passwordConfigured;

    public static PlatformConfigView of(PlatformConfig c, boolean passwordConfigured) {
        PlatformConfigView v = new PlatformConfigView();
        v.id = c.getId();
        v.platformName = c.getPlatformName();
        v.baseUrl = c.getBaseUrl();
        v.accountUsername = c.getAccountUsername();
        v.spaceId = c.getSpaceId();
        v.enabled = c.getEnabled();
        v.autoSync = c.getAutoSync();
        v.syncCron = c.getSyncCron();
        v.connectTimeoutMs = c.getConnectTimeoutMs();
        v.lastConnectAt = c.getLastConnectAt();
        v.lastConnectStatus = c.getLastConnectStatus();
        v.lastSyncAt = c.getLastSyncAt();
        v.lastSyncStatus = c.getLastSyncStatus();
        v.lastSyncBatchId = c.getLastSyncBatchId();
        v.remark = c.getRemark();
        v.createdAt = c.getCreatedAt();
        v.updatedAt = c.getUpdatedAt();
        v.passwordConfigured = passwordConfigured;
        return v;
    }
}
