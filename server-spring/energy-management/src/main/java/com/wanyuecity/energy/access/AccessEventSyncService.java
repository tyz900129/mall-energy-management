package com.wanyuecity.energy.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class AccessEventSyncService {
    private final JdbcTemplate jdbc;
    private final HikvisionIsapiClient isapi;
    private final CredentialCryptoService crypto;
    private final ObjectMapper json;
    @Value("${energy.upload.base-dir}") private String baseDir;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private volatile Map<String,Object> lastSummary=Map.of("status","IDLE","message","尚未执行通行记录同步");

    @Scheduled(cron="0 */30 * * * *")
    public void scheduledSync(){try{syncAll("SYSTEM_SCHEDULE");}catch(Exception ignored){}}

    public Map<String,Object> syncAll(String operator){
        if(!running.compareAndSet(false,true))throw BizException.badRequest("通行记录同步正在执行，请稍后查看");
        int devices=0,success=0,failed=0,inserted=0,photos=0;LocalDateTime started=LocalDateTime.now();
        try{
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,ip_address ipAddress,http_port httpPort,login_username loginUsername,password_ciphertext passwordCiphertext,password_iv passwordIv,direction,event_sync_cursor_at eventSyncCursorAt FROM access_device WHERE enabled=1 AND isapi_enabled=1 ORDER BY id");
            devices=rows.size();
            for(Map<String,Object>d:rows){try{Map<String,Integer> one=syncDevice(d);inserted+=one.get("inserted");photos+=one.get("photos");success++;}catch(Exception ex){failed++;String msg=message(ex);jdbc.update("UPDATE access_device SET last_event_sync_at=NOW(),last_event_sync_status='FAILED',last_event_sync_message=? WHERE id=?",msg,longValue(d.get("id")));}}
            String status=failed==0?"SUCCESS":success>0?"PARTIAL":"FAILED",message="设备 "+devices+" 台，成功 "+success+" 台，失败 "+failed+" 台，新增记录 "+inserted+" 条，照片 "+photos+" 张";
            lastSummary=summary(status,message,operator,started,LocalDateTime.now(),devices,success,failed,inserted,photos);return lastSummary;
        }finally{running.set(false);}
    }

    private Map<String,Integer> syncDevice(Map<String,Object>d)throws Exception{
        long deviceId=longValue(d.get("id"));String ip=text(d,"ipAddress"),user=text(d,"loginUsername"),password=crypto.decrypt(text(d,"passwordCiphertext"),text(d,"passwordIv"));int port=intValue(d.get("httpPort"),80);
        LocalDateTime end=LocalDateTime.now();LocalDateTime start=d.get("eventSyncCursorAt") instanceof Timestamp t?t.toLocalDateTime().minusMinutes(5):end.minusDays(1);if(start.isBefore(end.minusDays(31)))start=end.minusDays(31);
        String searchId=UUID.randomUUID().toString();int position=0,total=Integer.MAX_VALUE,inserted=0,photos=0;
        while(position<total){var page=isapi.readAccessEventPage(ip,port,user,password,searchId,position,30,start,end);total=page.total();if(page.rows().isEmpty())break;
            for(Map<String,Object>event:page.rows()){LocalDateTime eventTime=parseTime(first(event,"time","dateTime","eventTime"));if(eventTime==null)continue;String employee=first(event,"employeeNoString","employeeNo","cardNo"),serial=first(event,"serialNo","eventSerialNo");if(serial.isBlank())serial=sha256(deviceId+"|"+eventTime+"|"+employee+"|"+first(event,"major")+"|"+first(event,"minor"));
                Long personId=findPerson(deviceId,employee);String raw=json.writeValueAsString(event),verify=first(event,"currentVerifyMode","verifyMode","attendanceStatus"),eventType=first(event,"eventDescription","subEventType","minor");
                int count=jdbc.update("INSERT IGNORE INTO access_event(device_id,person_id,device_person_no,event_serial_no,event_time,direction,verify_mode,result,event_type,raw_event_json) VALUES(?,?,?,?,?,?,?,?,?,?)",deviceId,personId,employee,serial,Timestamp.valueOf(eventTime),text(d,"direction"),verify,"SUCCESS",eventType,raw);
                if(count>0){inserted++;Long eventId=jdbc.queryForObject("SELECT id FROM access_event WHERE device_id=? AND event_serial_no=?",Long.class,deviceId,serial);String picture=first(event,"pictureURL","picUri","pictureUrl","snapPicURL");if(!picture.isBlank()&&eventId!=null)try{byte[] bytes=isapi.readEventPicture(ip,port,user,password,picture);if(bytes.length>0){String relative=saveSnapshot(deviceId,eventId,eventTime,bytes);jdbc.update("UPDATE access_event SET snapshot_path=?,snapshot_mime_type='image/jpeg' WHERE id=?",relative,eventId);photos++;}}catch(Exception ignored){}
                    if(personId!=null)jdbc.update("UPDATE access_person SET last_passed_at=CASE WHEN last_passed_at IS NULL OR last_passed_at<? THEN ? ELSE last_passed_at END WHERE id=?",Timestamp.valueOf(eventTime),Timestamp.valueOf(eventTime),personId);
                }
            }
            position+=page.rows().size();if(page.rows().size()<30)break;
        }
        jdbc.update("UPDATE access_device SET event_sync_cursor_at=?,last_event_sync_at=NOW(),last_event_sync_status='SUCCESS',last_event_sync_message=? WHERE id=?",Timestamp.valueOf(end),"新增 "+inserted+" 条，照片 "+photos+" 张",deviceId);
        return Map.of("inserted",inserted,"photos",photos);
    }

    private Long findPerson(long deviceId,String employee){if(employee.isBlank())return null;List<Long> ids=jdbc.query("SELECT person_id FROM access_person_device WHERE device_id=? AND (device_employee_no=? OR device_person_no=?) ORDER BY id LIMIT 1",(rs,n)->rs.getLong(1),deviceId,employee,employee);if(!ids.isEmpty())return ids.get(0);ids=jdbc.query("SELECT id FROM access_person WHERE employee_no=? OR person_no=? ORDER BY managed_by_system DESC,id LIMIT 1",(rs,n)->rs.getLong(1),employee,employee);return ids.isEmpty()?null:ids.get(0);}
    private String saveSnapshot(long deviceId,long eventId,LocalDateTime time,byte[]bytes)throws Exception{Path root=Path.of(baseDir).toAbsolutePath().normalize(),dir=root.resolve("access-event").resolve(time.toLocalDate().toString()).resolve(String.valueOf(deviceId)).normalize();if(!dir.startsWith(root))throw new IllegalArgumentException("非法照片路径");Files.createDirectories(dir);Path file=dir.resolve(eventId+".jpg");Files.write(file,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);return root.relativize(file).toString();}

    public Map<String,Object> status(){Map<String,Object> out=new LinkedHashMap<>(lastSummary);out.put("running",running.get());out.put("devices",jdbc.queryForList("SELECT id,device_name deviceName,direction,last_event_sync_at lastEventSyncAt,last_event_sync_status lastEventSyncStatus,last_event_sync_message lastEventSyncMessage FROM access_device WHERE enabled=1 ORDER BY id"));return out;}
    public Snapshot snapshot(long eventId){Map<String,Object> row=one("SELECT snapshot_path path,COALESCE(snapshot_mime_type,'image/jpeg') mime FROM access_event WHERE id=?",eventId);String relative=Objects.toString(row.get("path"),"");if(relative.isBlank())throw BizException.notFound("该通行记录没有照片");Path root=Path.of(baseDir).toAbsolutePath().normalize(),path=root.resolve(relative).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw BizException.notFound("通行记录照片已过保存期或文件不存在");return new Snapshot(path,Objects.toString(row.get("mime"),"image/jpeg"));}
    public byte[] thumbnail(long eventId){try{Snapshot source=snapshot(eventId);BufferedImage image=ImageIO.read(source.path().toFile());if(image==null)throw BizException.notFound("照片格式无法识别");int maxWidth=160,maxHeight=120;double ratio=Math.min((double)maxWidth/image.getWidth(),(double)maxHeight/image.getHeight());ratio=Math.min(1d,ratio);int width=Math.max(1,(int)Math.round(image.getWidth()*ratio)),height=Math.max(1,(int)Math.round(image.getHeight()*ratio));BufferedImage thumb=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=thumb.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.drawImage(image,0,0,width,height,null);}finally{g.dispose();}ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(thumb,"jpg",out);return out.toByteArray();}catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.notFound("缩略图生成失败");}}

    @Scheduled(cron="0 20 2 * * *")
    public void cleanupExpiredSnapshots(){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,snapshot_path path FROM access_event WHERE event_time<DATE_SUB(NOW(),INTERVAL 1 MONTH) AND snapshot_path IS NOT NULL");Path root=Path.of(baseDir).toAbsolutePath().normalize();for(Map<String,Object>row:rows){try{Path path=root.resolve(Objects.toString(row.get("path"),"")).normalize();if(path.startsWith(root))Files.deleteIfExists(path);}catch(Exception ignored){}jdbc.update("UPDATE access_event SET snapshot_path=NULL,snapshot_mime_type=NULL WHERE id=?",row.get("id"));}}

    @Scheduled(cron="0 10 2 * * *")
    public void analyzePersonsDaily(){
        jdbc.update("UPDATE access_person SET person_status='EXPIRED' WHERE person_status='ACTIVE' AND valid_to<NOW()");
        jdbc.update("INSERT INTO access_person_review(person_id,review_type,reason) SELECT p.id,'EXPIRED','门禁权限已到期，请审核删除或续期' FROM access_person p WHERE p.person_status='EXPIRED' AND NOT EXISTS(SELECT 1 FROM access_person_review r WHERE r.person_id=p.id AND r.review_type='EXPIRED' AND r.status='PENDING')");
        jdbc.update("INSERT INTO access_person_review(person_id,review_type,reason) SELECT p.id,'EXPIRING','门禁权限将在30天内到期，请提前审核' FROM access_person p WHERE p.person_status='ACTIVE' AND p.valid_to BETWEEN NOW() AND DATE_ADD(NOW(),INTERVAL 30 DAY) AND NOT EXISTS(SELECT 1 FROM access_person_review r WHERE r.person_id=p.id AND r.review_type='EXPIRING' AND r.status='PENDING')");
        jdbc.update("INSERT INTO access_person_review(person_id,review_type,reason) SELECT p.id,'INACTIVE','连续60天未通行，请审核是否仍需保留门禁权限' FROM access_person p WHERE p.person_type='LONG_TERM' AND p.person_status='ACTIVE' AND (p.last_passed_at IS NULL OR p.last_passed_at<DATE_SUB(NOW(),INTERVAL 60 DAY)) AND NOT EXISTS(SELECT 1 FROM access_person_review r WHERE r.person_id=p.id AND r.review_type='INACTIVE' AND r.status='PENDING')");
    }

    public Map<String,Object> analysisMetrics(){Map<String,Object> out=new LinkedHashMap<>();out.put("expired",count("SELECT COUNT(*) FROM access_person WHERE person_status='EXPIRED'"));out.put("expiring30Days",count("SELECT COUNT(*) FROM access_person WHERE person_status='ACTIVE' AND valid_to BETWEEN NOW() AND DATE_ADD(NOW(),INTERVAL 30 DAY)"));out.put("inactive60Days",count("SELECT COUNT(*) FROM access_person WHERE person_type='LONG_TERM' AND person_status='ACTIVE' AND (last_passed_at IS NULL OR last_passed_at<DATE_SUB(NOW(),INTERVAL 60 DAY))"));out.put("todayPassedPersons",count("SELECT COUNT(DISTINCT person_id) FROM access_event WHERE event_time>=CURRENT_DATE AND person_id IS NOT NULL"));return out;}
    private Map<String,Object> summary(String status,String message,String operator,LocalDateTime started,LocalDateTime completed,int devices,int success,int failed,int inserted,int photos){Map<String,Object>m=new LinkedHashMap<>();m.put("status",status);m.put("message",message);m.put("operator",operator);m.put("startedAt",started);m.put("completedAt",completed);m.put("deviceTotal",devices);m.put("deviceSuccess",success);m.put("deviceFailed",failed);m.put("inserted",inserted);m.put("photos",photos);return m;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>>r=jdbc.queryForList(sql,args);if(r.isEmpty())throw BizException.notFound("数据不存在");return r.get(0);}
    private int count(String sql){Integer n=jdbc.queryForObject(sql,Integer.class);return n==null?0:n;}
    private String first(Map<String,Object>m,String...keys){for(String key:keys){Object v=m.get(key);if(v!=null&&!v.toString().isBlank())return v.toString();}return "";}
    private String text(Map<String,Object>m,String key){return Objects.toString(m.get(key),"").trim();}
    private long longValue(Object value){return value instanceof Number n?n.longValue():Long.parseLong(value.toString());}
    private int intValue(Object value,int fallback){try{return value==null?fallback:Integer.parseInt(value.toString());}catch(Exception e){return fallback;}}
    private LocalDateTime parseTime(String value){if(value.isBlank())return null;try{return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}catch(Exception ignored){}try{return LocalDateTime.parse(value,DateTimeFormatter.ISO_LOCAL_DATE_TIME);}catch(Exception ignored){}try{return LocalDateTime.parse(value.replace(' ','T'));}catch(Exception ignored){return null;}}
    private String sha256(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    private String message(Exception ex){String s=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();return s.substring(0,Math.min(480,s.length()));}
    public record Snapshot(Path path,String mimeType){}
}
