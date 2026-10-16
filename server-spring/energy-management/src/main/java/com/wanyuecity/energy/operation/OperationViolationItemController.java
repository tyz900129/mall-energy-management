package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/violation-items")
public class OperationViolationItemController {
    private final OperationViolationItemService service;
    public OperationViolationItemController(OperationViolationItemService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER')")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(service.list(includeDisabled));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body, Authentication authentication) {
        return ApiResponse.ok(service.create(body, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Map<String,Object>> update(@PathVariable Long id, @RequestBody Map<String,Object> body, Authentication authentication) {
        return ApiResponse.ok(service.update(id, body, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
}
