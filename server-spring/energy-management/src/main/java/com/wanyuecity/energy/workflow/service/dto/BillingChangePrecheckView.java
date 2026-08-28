package com.wanyuecity.energy.workflow.service.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class BillingChangePrecheckView {
    private String workflowType;
    private String merchantCode;
    private String merchantName;
    private boolean merchantExists;
    private boolean allowed;
    private boolean hasWaterMeter;
    private boolean requiresWaterConfirmation;
    private List<String> reasons = new ArrayList<>();
    private List<String> notices = new ArrayList<>();
    private List<MerchantStall> merchantStalls = new ArrayList<>();
    private List<DeviceCheck> devices = new ArrayList<>();

    @Data public static class MerchantStall {
        private Long stallId;
        private String stallCode;
        private String stallName;
        private List<String> deviceIds = new ArrayList<>();
    }

    @Data public static class DeviceCheck {
        private String platformDeviceId;
        private String deviceName;
        private String energyType;
        private String commAddress;
        private String serialNumber;
        private String gatewayName;
        private String channelName;
        private String productName;
        private List<String> currentAccountNumbers = new ArrayList<>();
        private List<String> currentAccountNames = new ArrayList<>();
        private boolean conflict;
        private boolean included = true;
    }
}
