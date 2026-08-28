package com.wanyuecity.energy.workflow.service.dto;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data public class NewMerchantPrecheckView {
    private String merchantCode;
    private Long stallId;
    private String stallCode;
    private boolean merchantCodeExists;
    private boolean allowed;
    private List<String> reasons = new ArrayList<>();
    private List<DeviceCheck> devices = new ArrayList<>();
    @Data public static class DeviceCheck {
        private String platformDeviceId;
        private String deviceName;
        private String energyType;
        private String commAddress;
        private String serialNumber;
        private List<String> currentAccountNumbers = new ArrayList<>();
        private boolean conflict;
    }
}
