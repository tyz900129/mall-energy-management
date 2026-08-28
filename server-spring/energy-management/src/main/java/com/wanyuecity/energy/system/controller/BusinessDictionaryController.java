package com.wanyuecity.energy.system.controller;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.service.BusinessDictionaryService;
import com.wanyuecity.energy.system.service.dto.BusinessDictionaryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/dictionaries") @RequiredArgsConstructor
public class BusinessDictionaryController {
 private final BusinessDictionaryService service;
 @GetMapping public ApiResponse<List<BusinessDictionary>> list(@RequestParam String type,@RequestParam(defaultValue="true") boolean enabledOnly){return ApiResponse.ok(service.list(type,enabledOnly));}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<BusinessDictionary> create(@Valid @RequestBody BusinessDictionaryRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<BusinessDictionary> update(@PathVariable Long id,@Valid @RequestBody BusinessDictionaryRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}

