package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConstructionAcceptanceRequest {
    @NotNull private LocalDateTime actualCompletionTime;
    @Size(max = 1000) private String applicationNote;
}
