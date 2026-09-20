package com.wanyuecity.energy.system.controller;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.service.BusinessDictionaryService;
import com.wanyuecity.energy.system.service.DictionaryExcelService;
import com.wanyuecity.energy.system.service.dto.BusinessDictionaryRequest;
import com.wanyuecity.energy.system.service.dto.DictionaryImportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
@RestController @RequestMapping("/api/v1/dictionaries") @RequiredArgsConstructor
public class BusinessDictionaryController {
 private final BusinessDictionaryService service;
 private final DictionaryExcelService excelService;
 @GetMapping public ApiResponse<List<BusinessDictionary>> list(@RequestParam String type,@RequestParam(defaultValue="true") boolean enabledOnly){return ApiResponse.ok(service.list(type,enabledOnly));}
 @GetMapping("/excel/export") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ResponseEntity<byte[]> exportExcel(@RequestParam String type){return workbook(type,false);}
 @GetMapping("/excel/template") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ResponseEntity<byte[]> template(@RequestParam String type){return workbook(type,true);}
 @PostMapping(value="/excel/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<DictionaryImportResult> importExcel(@RequestParam String type,@RequestPart("file") MultipartFile file){return ApiResponse.ok(excelService.importWorkbook(type,file));}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<BusinessDictionary> create(@Valid @RequestBody BusinessDictionaryRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<BusinessDictionary> update(@PathVariable Long id,@Valid @RequestBody BusinessDictionaryRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok();}
 private ResponseEntity<byte[]> workbook(String type,boolean template){byte[] body=excelService.export(type,template);String name=excelService.fileName(type,template);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).contentLength(body.length).body(body);}
}
