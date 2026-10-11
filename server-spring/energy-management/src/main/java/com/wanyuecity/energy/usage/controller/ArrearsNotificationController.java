package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.ArrearsNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy/arrears")
@RequiredArgsConstructor
public class ArrearsNotificationController {
    private final ArrearsNotificationService service;

    @GetMapping("/accounts") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> accounts(@RequestParam(required=false)String keyword,@RequestParam(required=false)Boolean excluded,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.accounts(keyword,excluded,pageNum,pageSize));}
    @GetMapping("/runs") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<List<Map<String,Object>>> runs(){return ApiResponse.ok(service.runs());}
    @PostMapping("/sync") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Map<String,Object>> sync(){return ApiResponse.ok(service.sync("MANUAL"));}
    @PutMapping("/accounts/{accountNumber}/exclusion") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> exclusion(@PathVariable String accountNumber,@RequestBody Map<String,Object> body){service.setExcluded(accountNumber,Boolean.TRUE.equals(body.get("excluded")),body.get("remark")==null?null:String.valueOf(body.get("remark")));return ApiResponse.ok();}
}
