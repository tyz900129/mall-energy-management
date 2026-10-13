package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/merchant-responsibilities")
public class OperationMerchantResponsibilityController {
    private final OperationMerchantResponsibilityService service;
    public OperationMerchantResponsibilityController(OperationMerchantResponsibilityService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<Map<String,Object>> page(@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Long managerUserId,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.page(keyword,managerUserId,pageNum,pageSize));}
    @GetMapping("/managers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<List<Map<String,Object>>> managers(@RequestParam(defaultValue="")String keyword){return ApiResponse.ok(service.managers(keyword));}
    @GetMapping("/{stallId}/history") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<List<Map<String,Object>>> history(@PathVariable Long stallId){return ApiResponse.ok(service.history(stallId));}
    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ApiResponse<Map<String,Object>> assign(@RequestBody Map<String,Object> body, Authentication authentication){return ApiResponse.ok(service.assign(body,authentication.getName()));}
    @GetMapping("/by-token/{token}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<Map<String,Object>> byToken(@PathVariable String token){return ApiResponse.ok(service.detailByToken(token));}
    @GetMapping(value="/{stallId}/qr",produces=MediaType.IMAGE_PNG_VALUE) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT')")
    public ResponseEntity<byte[]> qr(@PathVariable Long stallId){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_PNG).body(service.qr(stallId));}
}
