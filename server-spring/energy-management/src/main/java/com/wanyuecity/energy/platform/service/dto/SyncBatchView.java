package com.wanyuecity.energy.platform.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncBatchView {

    private Long id;
    private String batchNo;
    private String status;
    private String triggerType;
    private Long triggerUserId;
    private Integer accountCount;
    private Integer deviceCount;
    private Integer relationCount;
    private Integer exceptionCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
}

