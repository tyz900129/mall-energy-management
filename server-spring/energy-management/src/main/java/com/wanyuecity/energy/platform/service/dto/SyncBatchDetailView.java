package com.wanyuecity.energy.platform.service.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SyncBatchDetailView {
    private SyncBatchView batch;
    private boolean currentDataAvailable;
    private Integer completedWorkflowCount;
    private List<Map<String, Object>> exceptionSummary = new ArrayList<>();
    private List<Map<String, Object>> exceptions = new ArrayList<>();
}
