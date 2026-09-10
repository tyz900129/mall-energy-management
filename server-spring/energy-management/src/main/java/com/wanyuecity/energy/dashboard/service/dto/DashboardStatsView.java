package com.wanyuecity.energy.dashboard.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardStatsView {
    private long billingAccounts;
    private long stalls;
    private long warehouses;
    private long boundStalls;
    private long monthlySubmittedWorkflows;
}
