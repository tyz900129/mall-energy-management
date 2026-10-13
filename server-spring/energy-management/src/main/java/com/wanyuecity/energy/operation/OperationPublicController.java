package com.wanyuecity.energy.operation;
import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/operation-public")
public class OperationPublicController{
 private final OperationMerchantResponsibilityService service;public OperationPublicController(OperationMerchantResponsibilityService service){this.service=service;}
 @GetMapping("/stalls/{token}") public ApiResponse<Map<String,Object>> stall(@PathVariable String token){return ApiResponse.ok(service.publicStall(token));}
}
