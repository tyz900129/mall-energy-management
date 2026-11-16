package com.wanyuecity.energy.stall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.stall.service.StallService;
import com.wanyuecity.energy.stall.service.StallExcelService;
import com.wanyuecity.energy.stall.service.dto.StallImportResult;
import com.wanyuecity.energy.stall.service.dto.StallUpsertRequest;
import com.wanyuecity.energy.stall.service.dto.StallView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/stalls")
@RequiredArgsConstructor
public class StallController {

    private final StallService service;
    private final StallExcelService excelService;

    @GetMapping
    public ApiResponse<Page<StallView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String floorCode,
            @RequestParam(required = false) String businessType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(keyword, status, floorCode, businessType, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<StallView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<StallView> create(@Valid @RequestBody StallUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<StallView> update(@PathVariable Long id, @Valid @RequestBody StallUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable Long id) {
        service.softDelete(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/export")
    public void export(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String floorCode,@RequestParam(required=false) String businessType,HttpServletResponse response)throws IOException {
        excelService.export(keyword,status,floorCode,businessType,response);
    }

    @PostMapping("/import")
    public ApiResponse<StallImportResult> importExcel(@RequestParam("file") MultipartFile file){return ApiResponse.ok(excelService.importFile(file));}
}
