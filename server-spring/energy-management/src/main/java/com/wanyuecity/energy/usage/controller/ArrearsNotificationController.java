package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.ArrearsNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy/arrears")
@RequiredArgsConstructor
public class ArrearsNotificationController {
    private final ArrearsNotificationService service;

 @GetMapping("/accounts") 
    public ApiResponse<Map<String,Object>> accounts(@RequestParam(required=false)String keyword,@RequestParam(required=false)Boolean excluded,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.accounts(keyword,excluded,pageNum,pageSize));}
 @GetMapping("/runs") public ApiResponse<List<Map<String,Object>>> runs(){return ApiResponse.ok(service.runs());}
 @PostMapping("/sync") public ApiResponse<Map<String,Object>> sync(){return ApiResponse.ok(service.sync("MANUAL"));}
 @PutMapping("/accounts/{accountNumber}/exclusion") 
    public ApiResponse<Void> exclusion(@PathVariable String accountNumber,@RequestBody Map<String,Object> body){service.setExcluded(accountNumber,Boolean.TRUE.equals(body.get("excluded")),body.get("remark")==null?null:String.valueOf(body.get("remark")));return ApiResponse.ok();}
}
