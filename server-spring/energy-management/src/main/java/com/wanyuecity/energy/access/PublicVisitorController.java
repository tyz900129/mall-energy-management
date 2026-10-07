package com.wanyuecity.energy.access;

import com.wanyuecity.energy.core.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@RestController @RequestMapping("/api/v1/access-public") @RequiredArgsConstructor
public class PublicVisitorController {
    private final AccessControlService service;private final VisitorAttachmentService attachments;private final JdbcTemplate jdbc;
    private final Map<String,Deque<Instant>> attempts=new ConcurrentHashMap<>();

    @GetMapping("/stalls") public ApiResponse<List<Map<String,Object>>>stalls(@RequestParam(defaultValue="")String keyword){String k=clean(keyword,40);if(k.length()<1)return ApiResponse.ok(List.of());String like="%"+k+"%";return ApiResponse.ok(jdbc.queryForList("SELECT s.id,s.stall_code stallCode,s.stall_name stallName,s.floor_code floorCode,s.floor_name floorName,m.merchant_name currentMerchantName FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id WHERE s.status<>'DISABLED' AND (s.stall_code LIKE ? OR s.stall_name LIKE ? OR m.merchant_name LIKE ?) ORDER BY s.stall_code LIMIT 30",like,like,like));}
    @PostMapping("/appointments") public ApiResponse<Map<String,Object>>create(@RequestBody Map<String,Object>body,HttpServletRequest request){String ip=clientIp(request);rateLimit(ip);if(!Objects.toString(body.get("website"),"").isBlank())throw BizException.badRequest("提交失败");Map<String,Object>safe=new LinkedHashMap<>();for(String key:List.of("visitorName","visitorPhone","idNumber","affiliationType","stallId","visitorCompany","validFrom","validTo"))if(body.containsKey(key))safe.put(key,body.get(key));safe.put("visitorName",clean(Objects.toString(safe.get("visitorName"),""),40));safe.put("visitorPhone",clean(Objects.toString(safe.get("visitorPhone"),""),20));safe.put("idNumber",clean(Objects.toString(safe.get("idNumber"),""),18));Map<String,Object>created=service.createVisitor(safe,"PUBLIC");String token=UUID.randomUUID().toString().replace("-","");jdbc.update("UPDATE visitor_appointment SET public_token=? WHERE id=?",token,created.get("id"));return ApiResponse.ok(Map.of("token",token,"appointmentNo",created.get("appointmentNo"),"status",created.get("status")));}
    @PostMapping(value="/appointments/{token}/attachments",consumes="multipart/form-data") public ApiResponse<Map<String,Object>>upload(@PathVariable String token,@RequestParam String photoType,@RequestPart("file")MultipartFile file,HttpServletRequest request){Long id=appointment(token);Map<String,Object>result=new LinkedHashMap<>(attachments.upload(id,photoType,file,"PUBLIC"));if("FACE".equals(photoType.toUpperCase(Locale.ROOT))){try{result.put("faceQuality",service.checkFaceQuality(attachments.faceJpeg(id)));}catch(Exception ex){result.put("faceQuality",Map.of("passed",false,"message","人脸质量检测暂不可用："+ex.getMessage(),"failedStep","QUALITY_CHECK"));}}return ApiResponse.ok(result);}
    @PostMapping(value="/face-quality-check",consumes="multipart/form-data") public ApiResponse<Map<String,Object>>faceQualityCheck(@RequestPart("file")MultipartFile file,HttpServletRequest request){rateLimit(clientIp(request)+":faceqc");if(file==null||file.isEmpty())throw BizException.badRequest("请选择人脸照片");if(file.getSize()>10*1024*1024)throw BizException.badRequest("照片不能超过10MB");String mime=Optional.ofNullable(file.getContentType()).orElse("");if(!Set.of("image/jpeg","image/png").contains(mime))throw BizException.badRequest("只支持JPEG和PNG格式");try{byte[]bytes=file.getBytes();var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw BizException.badRequest("图片文件无法识别");if(image.getWidth()<200||image.getHeight()<200)throw BizException.badRequest("人脸照片分辨率不能低于200×200");return ApiResponse.ok(service.checkFaceQuality(bytes));}catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("人脸质量检测失败："+ex.getMessage());}}
    private Long appointment(String token){if(token==null||!token.matches("[0-9a-f]{32}"))throw BizException.notFound("预约不存在");List<Long>ids=jdbc.queryForList("SELECT id FROM visitor_appointment WHERE public_token=? AND created_at>=DATE_SUB(NOW(),INTERVAL 2 HOUR)",Long.class,token);if(ids.isEmpty())throw BizException.notFound("预约上传凭证已失效");return ids.get(0);}
    private void rateLimit(String ip){Instant now=Instant.now();Deque<Instant>q=attempts.computeIfAbsent(ip,x->new ArrayDeque<>());synchronized(q){while(!q.isEmpty()&&q.peekFirst().isBefore(now.minus(Duration.ofMinutes(10))))q.removeFirst();if(q.size()>=5)throw BizException.badRequest("提交过于频繁，请稍后再试");q.addLast(now);}}
    private String clientIp(HttpServletRequest r){String value=r.getHeader("X-Forwarded-For");if(value==null||value.isBlank())value=r.getRemoteAddr();return clean(value.split(",")[0],64);}
    private String clean(String value,int max){String s=value==null?"":value.trim();if(s.length()>max)s=s.substring(0,max);return s.replaceAll("[\\p{Cntrl}]","");}
}
