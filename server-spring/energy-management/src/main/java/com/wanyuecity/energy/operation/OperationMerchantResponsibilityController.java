package com.wanyuecity.energy.operation;

import com.wanyuecity.energy.core.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operations/merchant-responsibilities")
public class OperationMerchantResponsibilityController {
    private final OperationMerchantResponsibilityService service;
    public OperationMerchantResponsibilityController(OperationMerchantResponsibilityService service){this.service=service;}

 @GetMapping 
    public ApiResponse<Map<String,Object>> page(@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Long managerUserId,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return ApiResponse.ok(service.page(keyword,managerUserId,pageNum,pageSize));}
 @GetMapping("/managers") 
    public ApiResponse<List<Map<String,Object>>> managers(@RequestParam(defaultValue="")String keyword){return ApiResponse.ok(service.managers(keyword));}
 @GetMapping("/{stallId}/history") 
    public ApiResponse<List<Map<String,Object>>> history(@PathVariable Long stallId){return ApiResponse.ok(service.history(stallId));}
 @PostMapping 
    public ApiResponse<Map<String,Object>> assign(@RequestBody Map<String,Object> body, Authentication authentication){return ApiResponse.ok(service.assign(body,authentication.getName()));}
 @GetMapping("/by-token/{token}") 
    public ApiResponse<Map<String,Object>> byToken(@PathVariable String token){return ApiResponse.ok(service.detailByToken(token));}
 @GetMapping(value="/{stallId}/qr",produces=MediaType.IMAGE_PNG_VALUE) 
    public ResponseEntity<byte[]> qr(@PathVariable Long stallId){return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_PNG).body(service.qr(stallId));}

    /** 管理端批量导出铺位二维码 zip */
    @PostMapping(value = "/qr-batch", produces = "application/zip")
    public void batchQrZip(@RequestBody Map<String, Object> body, HttpServletResponse response) throws Exception {
        response.setContentType("application/zip");
        String filename = URLEncoder.encode("铺位二维码批量导出.zip", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        @SuppressWarnings("unchecked")
        List<Long> stallIds = (List<Long>) body.get("stallIds");
        service.batchZip(stallIds, response.getOutputStream());
    }

    /** 商管自助下载自己负责铺位的二维码 zip（后端强制过滤，防越权） */
    @GetMapping(value = "/my-stalls-qr", produces = "application/zip")
    public void myStallsQrZip(HttpServletResponse response) throws Exception {
        response.setContentType("application/zip");
        String filename = URLEncoder.encode("我的铺位二维码.zip", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        service.myStallsZip(response.getOutputStream());
    }
}
