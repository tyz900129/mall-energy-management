package com.wanyuecity.energy.system.controller;
import com.wanyuecity.energy.core.ApiResponse;import com.wanyuecity.energy.system.entity.AirConditionerSpecification;import com.wanyuecity.energy.system.service.AirConditionerSpecificationService;import com.wanyuecity.energy.system.service.dto.AirConditionerSpecificationRequest;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/v1/air-conditioner-specifications") @RequiredArgsConstructor public class AirConditionerSpecificationController {
 private final AirConditionerSpecificationService service;
 @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<List<AirConditionerSpecification>> list(@RequestParam(defaultValue="true") boolean enabledOnly){return ApiResponse.ok(service.list(enabledOnly));}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<AirConditionerSpecification> create(@Valid @RequestBody AirConditionerSpecificationRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<AirConditionerSpecification> update(@PathVariable Long id,@Valid @RequestBody AirConditionerSpecificationRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}
