package com.wanyuecity.energy.workflow.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConstructionInspectionRequest {
    // The controller fills this value from the trusted URL path.
    @Size(max = 24) private String inspectionType;
    // The result is calculated by the server from the configured normal values.
    @Size(max = 24) private String result;
    private String answersJson;
    @Size(max = 1000) private String problemDescription;
    @Size(max = 1000) private String rectificationRequirement;
    private LocalDateTime rectificationDeadline;
}
