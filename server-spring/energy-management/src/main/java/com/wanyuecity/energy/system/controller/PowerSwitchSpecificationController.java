package com.wanyuecity.energy.system.controller;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.PowerSwitchSpecification;
import com.wanyuecity.energy.system.service.PowerSwitchSpecificationService;
import com.wanyuecity.energy.system.service.dto.PowerSwitchSpecificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/power-switch-specifications") @RequiredArgsConstructor public class PowerSwitchSpecificationController {
 private final PowerSwitchSpecificationService service;
 @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<List<PowerSwitchSpecification>> list(@RequestParam(defaultValue="true") boolean enabledOnly){return ApiResponse.ok(service.list(enabledOnly));}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerSwitchSpecification> create(@Valid @RequestBody PowerSwitchSpecificationRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerSwitchSpecification> update(@PathVariable Long id,@Valid @RequestBody PowerSwitchSpecificationRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}
