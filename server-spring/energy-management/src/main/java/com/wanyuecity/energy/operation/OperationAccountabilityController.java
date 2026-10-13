package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/operations/accountability-orders")
public class OperationAccountabilityController {
    private final OperationAccountabilityService service;
    public OperationAccountabilityController(OperationAccountabilityService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER')")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String keyword){return ApiResponse.ok(service.list(status,keyword));}
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER')")
    public ApiResponse<Map<String,Object>> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
    @PostMapping("/from-inspection/{inspectionId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<Map<String,Object>> create(@PathVariable Long inspectionId,@RequestBody Map<String,Object> body,Authentication auth){return ApiResponse.ok(service.create(inspectionId,body,auth.getName()));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<Map<String,Object>> update(@PathVariable Long id,@RequestBody Map<String,Object> body,Authentication auth){return ApiResponse.ok(service.update(id,body,auth.getName()));}
}
