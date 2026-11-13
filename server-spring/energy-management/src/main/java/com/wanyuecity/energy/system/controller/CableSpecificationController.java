package com.wanyuecity.energy.system.controller;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.CableSpecification;
import com.wanyuecity.energy.system.service.CableSpecificationService;
import com.wanyuecity.energy.system.service.dto.CableSpecificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/cable-specifications") @RequiredArgsConstructor
public class CableSpecificationController {
 private final CableSpecificationService service;
 @GetMapping public ApiResponse<List<CableSpecification>> list(@RequestParam(defaultValue="true") boolean enabledOnly){return ApiResponse.ok(service.list(enabledOnly));}
 @PostMapping public ApiResponse<CableSpecification> create(@Valid @RequestBody CableSpecificationRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") public ApiResponse<CableSpecification> update(@PathVariable Long id,@Valid @RequestBody CableSpecificationRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}
