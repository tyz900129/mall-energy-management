package com.wanyuecity.energy.usage.service.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record UsageSyncRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                               Long groupId, List<String> deviceIds,
                               Boolean includeExcluded, Boolean monthlyOnly) {}
