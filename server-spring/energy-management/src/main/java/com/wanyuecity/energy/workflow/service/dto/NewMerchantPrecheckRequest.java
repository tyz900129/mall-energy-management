package com.wanyuecity.energy.workflow.service.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
@Data public class NewMerchantPrecheckRequest {
    @NotBlank @Pattern(regexp = "\\d{6}") private String merchantCode;
    @NotNull private Long stallId;
}
