package com.wanyuecity.energy.facility.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.facility.service.FacilityTopologyService;
import com.wanyuecity.energy.facility.service.dto.FacilityTopologyNodeRequest;
import com.wanyuecity.energy.facility.service.dto.FacilityTopologyNodeView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/facility-topologies/{systemType}/nodes")
@RequiredArgsConstructor
public class FacilityTopologyController {
    private final FacilityTopologyService service;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ApiResponse<List<FacilityTopologyNodeView>> tree(@PathVariable String systemType) {
        return ApiResponse.ok(service.tree(systemType));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<FacilityTopologyNodeView> create(@PathVariable String systemType, @Valid @RequestBody FacilityTopologyNodeRequest request) {
        return ApiResponse.ok(service.create(systemType, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<FacilityTopologyNodeView> update(@PathVariable String systemType, @PathVariable Long id, @Valid @RequestBody FacilityTopologyNodeRequest request) {
        return ApiResponse.ok(service.update(systemType, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String systemType, @PathVariable Long id) {
        service.delete(systemType, id);
        return ApiResponse.ok();
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER')")
    public ResponseEntity<byte[]> qr(@PathVariable String systemType, @PathVariable Long id) throws Exception {
        service.require(systemType, id);
        String url = publicBaseUrl.replaceAll("/+$", "") + "/facility/" + service.routeSegment(systemType) + "?focus=" + id;
        BitMatrix matrix = new QRCodeWriter().encode(new String(url.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), BarcodeFormat.QR_CODE, 420, 420);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_PNG).body(output.toByteArray());
    }
}
