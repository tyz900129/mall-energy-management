package com.wanyuecity.energy.stall.service.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
@Data @AllArgsConstructor
public class StallImportResult { private int successCount; private int failureCount; private List<String> errors; }
