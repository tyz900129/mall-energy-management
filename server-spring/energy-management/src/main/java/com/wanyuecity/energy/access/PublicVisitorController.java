package com.wanyuecity.energy.access;

import com.wanyuecity.energy.core.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@RestController @RequestMapping("/api/v1/access-public") @RequiredArgsConstructor
public class PublicVisitorController {
    private final AccessControlService service;private final VisitorAttachmentService attachments;private final JdbcTemplate jdbc;
    private final Map<String,Deque<Instant>> attempts=new ConcurrentHashMap<>();

    @GetMapping("/stalls") public ApiResponse<List<Map<String,Object>>>stalls(@RequestParam(defaultValue="")String keyword){String k=clean(keyword,40);if(k.length()<1)return ApiResponse.ok(List.of());String like="%"+k+"%";return ApiResponse.ok(jdbc.queryForList("SELECT s.id,s.stall_code stallCode,s.stall_name stallName,s.floor_code floorCode,s.floor_name floorName,m.merchant_name currentMerchantName FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id WHERE s.status<>'DISABLED' AND (s.stall_code LIKE ? OR s.stall_name LIKE ? OR m.merchant_name LIKE ?) ORDER BY s.stall_code LIMIT 30",like,like,like));}
    @PostMapping("/appointments") public ApiResponse<Map<String,Object>>create(@RequestBody Map<String,Object>body,HttpServletRequest request){String ip=clientIp(request);rateLimit(ip);if(!Objects.toString(body.get("website"),"").isBlank())throw BizException.badRequest("提交失败");Map<String,Object>safe=new LinkedHashMap<>();for(String key:List.of("visitorName","visitorPhone","idNumber","affiliationType","stallId","visitorCompany","validFrom","validTo"))if(body.containsKey(key))safe.put(key,body.get(key));safe.put("visitorName",clean(Objects.toString(safe.get("visitorName"),""),40));safe.put("visitorPhone",clean(Objects.toString(safe.get("visitorPhone"),""),20));safe.put("idNumber",clean(Objects.toString(safe.get("idNumber"),""),18));Map<String,Object>created=service.createVisitor(safe,"PUBLIC");String token=UUID.randomUUID().toString().replace("-","");jdbc.update("UPDATE visitor_appointment SET public_token=? WHERE id=?",token,created.get("id"));return ApiResponse.ok(Map.of("token",token,"appointmentNo",created.get("appointmentNo"),"status",created.get("status")));}
    @PostMapping(value="/appointments/{token}/attachments",consumes="multipart/form-data") public ApiResponse<Map<String,Object>>upload(@PathVariable String token,@RequestParam String photoType,@RequestPart("file")MultipartFile file){Long id=appointment(token);return ApiResponse.ok(attachments.upload(id,photoType,file,"PUBLIC"));}
    private Long appointment(String token){if(token==null||!token.matches("[0-9a-f]{32}"))throw BizException.notFound("预约不存在");List<Long>ids=jdbc.queryForList("SELECT id FROM visitor_appointment WHERE public_token=? AND created_at>=DATE_SUB(NOW(),INTERVAL 2 HOUR)",Long.class,token);if(ids.isEmpty())throw BizException.notFound("预约上传凭证已失效");return ids.get(0);}
    private void rateLimit(String ip){Instant now=Instant.now();Deque<Instant>q=attempts.computeIfAbsent(ip,x->new ArrayDeque<>());synchronized(q){while(!q.isEmpty()&&q.peekFirst().isBefore(now.minus(Duration.ofMinutes(10))))q.removeFirst();if(q.size()>=5)throw BizException.badRequest("提交过于频繁，请稍后再试");q.addLast(now);}}
    private String clientIp(HttpServletRequest r){String value=r.getHeader("X-Forwarded-For");if(value==null||value.isBlank())value=r.getRemoteAddr();return clean(value.split(",")[0],64);}
    private String clean(String value,int max){String s=value==null?"":value.trim();if(s.length()>max)s=s.substring(0,max);return s.replaceAll("[\\p{Cntrl}]","");}
}
