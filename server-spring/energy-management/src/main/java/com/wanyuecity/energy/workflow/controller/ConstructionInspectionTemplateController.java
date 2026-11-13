package com.wanyuecity.energy.workflow.controller;

import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.workflow.service.ConstructionInspectionTemplateService;
import com.wanyuecity.energy.workflow.service.dto.ConstructionInspectionTemplateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/construction-inspection-templates")
@RequiredArgsConstructor
public class ConstructionInspectionTemplateController {
    private final ConstructionInspectionTemplateService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return ApiResponse.ok(service.list(enabledOnly));
    }

    @PutMapping("/{type}")
    public ApiResponse<Map<String, Object>> save(@PathVariable String type,
            @Valid @RequestBody ConstructionInspectionTemplateRequest request) {
        return ApiResponse.ok(service.save(type, request));
    }
}
