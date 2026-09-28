package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/inspection-equipment") @RequiredArgsConstructor
public class InspectionEquipmentController {
 private final InspectionEquipmentService service;
 @GetMapping("/categories")public ApiResponse<List<Map<String,Object>>>categories(){return ApiResponse.ok(service.categories());}
 @PostMapping("/categories")@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")public ApiResponse<Map<String,Object>>createCategory(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveCategory(null,b));}
 @PutMapping("/categories/{id}")@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")public ApiResponse<Map<String,Object>>updateCategory(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveCategory(id,b));}
 @GetMapping public ApiResponse<List<Map<String,Object>>>list(@RequestParam(required=false)String keyword,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)Integer enabled){return ApiResponse.ok(service.equipment(keyword,categoryId,enabled));}
 @GetMapping("/{id}")public ApiResponse<Map<String,Object>>get(@PathVariable Long id){return ApiResponse.ok(service.equipmentById(id));}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")public ApiResponse<Map<String,Object>>create(@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveEquipment(null,b));}
 @PutMapping("/{id}")@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")public ApiResponse<Map<String,Object>>update(@PathVariable Long id,@RequestBody Map<String,Object>b){return ApiResponse.ok(service.saveEquipment(id,b));}
 @GetMapping("/scan/{token}")public ApiResponse<Map<String,Object>>scan(@PathVariable String token){return ApiResponse.ok(service.byToken(token));}
 @GetMapping(value="/public-qr/{token}",produces=MediaType.IMAGE_PNG_VALUE)public byte[]qr(@PathVariable String token){return service.qr(token);}
}
