package com.wanyuecity.energy.workflow.service.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data public class NewMerchantWorkflowRequest {
    @NotBlank @Pattern(regexp = "\\d{6}") private String merchantCode;
    @NotBlank private String merchantName;
    @NotBlank private String contactName;
    @NotBlank private String contactPhone;
    @NotNull private Long stallId;
    @NotNull private LocalDateTime requestedBillingStartTime;
    private String remark;
}

