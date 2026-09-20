package com.wanyuecity.energy.usage.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.usage.service.EnergyUsageService;
import com.wanyuecity.energy.usage.service.AnalysisDimensionService;
import com.wanyuecity.energy.usage.service.UtilityBillService;
import com.wanyuecity.energy.usage.service.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/energy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
public class EnergyUsageController {
    private final EnergyUsageService service;
    private final AnalysisDimensionService dimensionService;
    private final UtilityBillService utilityBillService;

    @GetMapping("/dimensions") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')") public ApiResponse<List<Map<String,Object>>> dimensions(){return ApiResponse.ok(dimensionService.dimensions());}
    @PostMapping("/dimensions") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Map<String,Object>> createDimension(@Valid @RequestBody AnalysisDimensionRequest r){return ApiResponse.ok(dimensionService.createDimension(r));}
    @PutMapping("/dimensions/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> updateDimension(@PathVariable Long id,@Valid @RequestBody AnalysisDimensionRequest r){dimensionService.updateDimension(id,r);return ApiResponse.ok(null);}
    @DeleteMapping("/dimensions/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> deleteDimension(@PathVariable Long id){dimensionService.deleteDimension(id);return ApiResponse.ok(null);}
    @PostMapping("/dimensions/{id}/values") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Map<String,Object>> createValue(@PathVariable Long id,@Valid @RequestBody AnalysisDimensionValueRequest r){return ApiResponse.ok(dimensionService.createValue(id,r));}
    @PutMapping("/dimension-values/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> updateValue(@PathVariable Long id,@Valid @RequestBody AnalysisDimensionValueRequest r){dimensionService.updateValue(id,r);return ApiResponse.ok(null);}
    @DeleteMapping("/dimension-values/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> deleteValue(@PathVariable Long id){dimensionService.deleteValue(id);return ApiResponse.ok(null);}
    @GetMapping("/dimensions/{id}/devices") public ApiResponse<List<Map<String,Object>>> dimensionDevices(@PathVariable Long id,@RequestParam(required=false)Long valueId,@RequestParam(required=false)Boolean unassigned,@RequestParam(required=false)String keyword,@RequestParam(required=false)String energyType){return ApiResponse.ok(dimensionService.devices(id,valueId,unassigned,keyword,energyType));}
    @PostMapping("/dimensions/assign") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Integer> assignDimension(@Valid @RequestBody DeviceDimensionAssignRequest r){return ApiResponse.ok(dimensionService.assign(r));}

    @GetMapping("/groups") public ApiResponse<List<Map<String,Object>>> groups() { return ApiResponse.ok(service.groups()); }
    @PostMapping("/groups") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody DeviceGroupRequest r) { return ApiResponse.ok(service.createGroup(r)); }
    @PutMapping("/groups/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody DeviceGroupRequest r) { service.updateGroup(id,r); return ApiResponse.ok(null); }
    @DeleteMapping("/groups/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.deleteGroup(id); return ApiResponse.ok(null); }
    @GetMapping("/devices") public ApiResponse<List<Map<String,Object>>> devices(
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Long groupId,
            @RequestParam(required=false) Boolean ungrouped,@RequestParam(required=false) String energyType) {
        return ApiResponse.ok(service.devices(keyword,groupId,ungrouped,energyType));
    }
    @PostMapping("/devices/assign") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Integer> assign(@Valid @RequestBody DeviceGroupAssignRequest r) { return ApiResponse.ok(service.assign(r)); }
    @GetMapping("/bills") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<List<Map<String,Object>>> bills(@RequestParam(required=false) String startMonth,@RequestParam(required=false) String endMonth) { return ApiResponse.ok(utilityBillService.list(startMonth,endMonth)); }
    @GetMapping("/bills/trend") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<List<UtilityBillService.TrendRow>> billTrend(@RequestParam String startMonth,@RequestParam String endMonth,@RequestParam(defaultValue="MONTH") String period) { return ApiResponse.ok(utilityBillService.trend(startMonth,endMonth,period)); }
    @PostMapping("/bills") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> createBill(@Valid @RequestBody UtilityBillRequest r, Authentication auth) { return ApiResponse.ok(utilityBillService.create(r,auth==null?null:auth.getName())); }
    @PutMapping("/bills/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> updateBill(@PathVariable Long id,@Valid @RequestBody UtilityBillRequest r,Authentication auth) { return ApiResponse.ok(utilityBillService.update(id,r,auth==null?null:auth.getName())); }
    @DeleteMapping("/bills/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteBill(@PathVariable Long id) { utilityBillService.delete(id); return ApiResponse.ok(null); }
    @GetMapping("/metrics") public ApiResponse<List<Map<String,Object>>> metrics() { return ApiResponse.ok(service.metrics()); }
    @PutMapping("/metrics") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> metric(@Valid @RequestBody EnergyMetricRequest r) { service.saveMetric(r); return ApiResponse.ok(null); }
    @PostMapping("/usage/sync") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> sync(@Valid @RequestBody UsageSyncRequest r) { return ApiResponse.ok(service.sync(r)); }
    @GetMapping("/usage/sync-batches") public ApiResponse<List<Map<String,Object>>> batches() { return ApiResponse.ok(service.syncBatches()); }
    @GetMapping("/usage/sync-exclusions") public ApiResponse<List<Map<String,Object>>> exclusions() { return ApiResponse.ok(service.syncExclusions()); }
    @PostMapping("/usage/sync-exclusions") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Integer> addExclusions(@Valid @RequestBody UsageSyncExclusionRequest r) { return ApiResponse.ok(service.addSyncExclusions(r)); }
    @PutMapping("/usage/sync-exclusions/{deviceId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> updateExclusion(@PathVariable String deviceId,@Valid @RequestBody UsageSyncExclusionUpdateRequest r) { service.updateSyncExclusion(deviceId,r); return ApiResponse.ok(null); }
    @DeleteMapping("/usage/sync-exclusions/{deviceId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteExclusion(@PathVariable String deviceId) { service.deleteSyncExclusion(deviceId); return ApiResponse.ok(null); }
    @GetMapping("/analysis") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')") public ApiResponse<Map<String,Object>> analysis(
            @RequestParam LocalDate startDate,@RequestParam LocalDate endDate,
            @RequestParam(defaultValue="MONTH") String period,
            @RequestParam(required=false) Long groupId,@RequestParam(required=false) String deviceId,
            @RequestParam(required=false) Long dimensionId,@RequestParam(required=false) Long dimensionValueId) {
        return ApiResponse.ok(service.analysis(startDate,endDate,period,groupId,deviceId,dimensionId,dimensionValueId));
    }
}
