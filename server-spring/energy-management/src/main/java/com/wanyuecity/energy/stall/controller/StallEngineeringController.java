package com.wanyuecity.energy.stall.controller;
import com.wanyuecity.energy.core.ApiResponse;import com.wanyuecity.energy.stall.service.StallEngineeringService;import com.wanyuecity.energy.stall.service.dto.*;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stalls/{stallId}/engineering") @RequiredArgsConstructor public class StallEngineeringController {private final StallEngineeringService service;
 @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<StallEngineeringView> get(@PathVariable Long stallId){return ApiResponse.ok(service.get(stallId));}
 @PutMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<StallEngineeringView> save(@PathVariable Long stallId,@Valid @RequestBody StallEngineeringRequest req){return ApiResponse.ok(service.save(stallId,req));}
}
