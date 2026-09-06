package com.wanyuecity.energy.power.controller;
import com.wanyuecity.energy.core.ApiResponse;import com.wanyuecity.energy.power.service.PowerTopologyService;import com.wanyuecity.energy.power.service.dto.*;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/v1/power-topology/nodes") @RequiredArgsConstructor public class PowerTopologyController {
 private final PowerTopologyService service;
 @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<List<PowerTopologyNodeView>> tree(){return ApiResponse.ok(service.tree());}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerTopologyNodeView> create(@Valid @RequestBody PowerTopologyNodeRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerTopologyNodeView> update(@PathVariable Long id,@Valid @RequestBody PowerTopologyNodeRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
}
