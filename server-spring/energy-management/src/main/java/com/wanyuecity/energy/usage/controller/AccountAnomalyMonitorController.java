package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.AccountAnomalyMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy/account-anomalies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
public class AccountAnomalyMonitorController {
    private final AccountAnomalyMonitorService service;
    @GetMapping("/overview") public ApiResponse<Map<String,Object>> overview(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date){return ApiResponse.ok(service.overview(date));}
    @GetMapping public ApiResponse<Map<String,Object>> results(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam(required=false)String keyword,@RequestParam(required=false)String status,@RequestParam(required=false)String direction,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.results(date,keyword,status,direction,pageNum,pageSize));}
    @GetMapping("/accounts") public ApiResponse<List<Map<String,Object>>> accounts(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.accounts(keyword));}
    @GetMapping("/trend") public ApiResponse<Map<String,Object>> trend(@RequestParam String accountNumber,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate startDate,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate endDate){return ApiResponse.ok(service.trend(accountNumber,startDate,endDate));}
    @PostMapping("/sync") public ApiResponse<Map<String,Object>> sync(){return ApiResponse.ok(service.syncAndAnalyze("MANUAL"));}
    @GetMapping("/rules") public ApiResponse<List<Map<String,Object>>> rules(@RequestParam(required=false)String keyword){return ApiResponse.ok(service.rules(keyword));}
    @GetMapping("/rules/default") public ApiResponse<Map<String,Object>> defaultRule(){return ApiResponse.ok(service.defaultRule());}
    @PutMapping("/rules/{accountNumber}") public ApiResponse<Void> saveRule(@PathVariable String accountNumber,@RequestBody Map<String,Object> body){service.saveRule(accountNumber,body);return ApiResponse.ok();}
    @PutMapping("/{id}/handle") public ApiResponse<Void> handle(@PathVariable Long id,@RequestBody Map<String,Object> body){service.handle(id,String.valueOf(body.get("status")),body.get("remark")==null?null:String.valueOf(body.get("remark")));return ApiResponse.ok();}
    @GetMapping("/runs") public ApiResponse<List<Map<String,Object>>> runs(){return ApiResponse.ok(service.runs());}
}
