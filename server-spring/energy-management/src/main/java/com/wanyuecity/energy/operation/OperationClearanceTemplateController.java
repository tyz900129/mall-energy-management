package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/clearance-templates")
public class OperationClearanceTemplateController {
    private final OperationClearanceTemplateService service;

    public OperationClearanceTemplateController(OperationClearanceTemplateService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/stall/{token}")
    public ApiResponse<Map<String, Object>> stall(@PathVariable String token) { return ApiResponse.ok(service.resolveByToken(token)); }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.ok(service.create(body, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
