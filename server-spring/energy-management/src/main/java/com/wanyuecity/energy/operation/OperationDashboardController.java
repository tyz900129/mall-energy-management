package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/dashboard")
public class OperationDashboardController {
    private final OperationDashboardService service;
    public OperationDashboardController(OperationDashboardService service){this.service=service;}
    @GetMapping
    public ApiResponse<Map<String,Object>> dashboard(@RequestParam(required=false) String periodDate, Authentication auth){
        LocalDate date=periodDate==null||periodDate.isBlank()?(LocalTime.now().isBefore(LocalTime.of(9,0))?LocalDate.now().minusDays(1):LocalDate.now()):LocalDate.parse(periodDate);
        boolean global=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("operation.dashboard.view.all"));
        return ApiResponse.ok(service.dashboard(date,auth.getName(),global));
    }
}
