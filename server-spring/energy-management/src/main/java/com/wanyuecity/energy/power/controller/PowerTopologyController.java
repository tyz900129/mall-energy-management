package com.wanyuecity.energy.power.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.power.service.PowerTopologyService;
import com.wanyuecity.energy.power.service.dto.PowerTopologyNodeRequest;
import com.wanyuecity.energy.power.service.dto.PowerTopologyNodeView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/v1/power-topology/nodes") @RequiredArgsConstructor
public class PowerTopologyController {
 private final PowerTopologyService service;
 @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;
 @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')") public ApiResponse<List<PowerTopologyNodeView>> tree(){return ApiResponse.ok(service.tree());}
 @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerTopologyNodeView> create(@Valid @RequestBody PowerTopologyNodeRequest r){return ApiResponse.ok(service.create(r));}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<PowerTopologyNodeView> update(@PathVariable Long id,@Valid @RequestBody PowerTopologyNodeRequest r){return ApiResponse.ok(service.update(id,r));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
 @GetMapping(value="/{id}/qr",produces=MediaType.IMAGE_PNG_VALUE) @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','VIEWER')")
 public ResponseEntity<byte[]> qr(@PathVariable Long id) throws Exception {
  service.require(id);
  String url=publicBaseUrl.replaceAll("/+$","")+"/power/topology?focus="+id;
  BitMatrix matrix=new QRCodeWriter().encode(new String(url.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8),BarcodeFormat.QR_CODE,420,420);
  ByteArrayOutputStream output=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",output);
  return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_PNG).body(output.toByteArray());
 }
}
