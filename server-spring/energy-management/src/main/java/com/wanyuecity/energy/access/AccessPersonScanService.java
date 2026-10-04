package com.wanyuecity.energy.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessPersonScanService {
    private final JdbcTemplate jdbc;
    private final HikvisionIsapiClient isapi;
    private final CredentialCryptoService crypto;
    private final ObjectMapper json;

    @Async
    public void scan(Long jobId) {
        try {
            jdbc.update("UPDATE access_person_scan_job SET status='RUNNING',started_at=NOW() WHERE id=?",jobId);
            List<Map<String,Object>> devices=jdbc.queryForList("SELECT id,direction,ip_address ipAddress,http_port httpPort,login_username loginUsername,password_ciphertext passwordCiphertext,password_iv passwordIv FROM access_device WHERE enabled=1 ORDER BY id");
            jdbc.update("UPDATE access_person_scan_job SET device_total=? WHERE id=?",devices.size(),jobId);
            int rows=0,completed=0;
            for(Map<String,Object>d:devices){scanDevice(jobId,d);rows+=count("SELECT COUNT(*) FROM access_person_snapshot WHERE scan_job_id=? AND device_id=?",jobId,d.get("id"));completed++;jdbc.update("UPDATE access_person_scan_job SET device_completed=?,person_rows=? WHERE id=?",completed,rows,jobId);}
            summarize(jobId,devices);
        } catch(Exception ex) {
            log.warn("access person read-only scan failed: job={}, type={}",jobId,ex.getClass().getSimpleName());
            jdbc.update("UPDATE access_person_scan_job SET status='FAILED',message=?,completed_at=NOW() WHERE id=?",safe(ex.getMessage()),jobId);
        }
    }

    private void scanDevice(Long jobId,Map<String,Object>d) throws Exception {
        Long deviceId=((Number)d.get("id")).longValue();String password=crypto.decrypt(String.valueOf(d.get("passwordCiphertext")),String.valueOf(d.get("passwordIv")));String searchId=UUID.randomUUID().toString();int position=0,total=Integer.MAX_VALUE;
        while(position<total){HikvisionIsapiClient.PersonPage page=isapi.readPersonPage(String.valueOf(d.get("ipAddress")),((Number)d.get("httpPort")).intValue(),String.valueOf(d.get("loginUsername")),password,searchId,position,30);total=page.total();if(page.rows().isEmpty())break;for(Map<String,Object>u:page.rows())save(jobId,deviceId,u);position+=page.rows().size();}
    }

    private void save(Long jobId,Long deviceId,Map<String,Object>u) throws Exception {String employee=text(u.get("employeeNo"));if(employee.isBlank())return;Map<?,?>valid=u.get("Valid") instanceof Map<?,?>m?m:Map.of();jdbc.update("INSERT INTO access_person_snapshot(scan_job_id,device_id,employee_no,person_name,user_type,phone,valid_from,valid_to,face_count,card_count,raw_json) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE person_name=VALUES(person_name),user_type=VALUES(user_type),phone=VALUES(phone),valid_from=VALUES(valid_from),valid_to=VALUES(valid_to),face_count=VALUES(face_count),card_count=VALUES(card_count),raw_json=VALUES(raw_json)",jobId,deviceId,employee,text(u.get("name")),text(u.get("userType")),phone(u),time(valid.get("beginTime")),time(valid.get("endTime")),number(u.get("numOfFace")),number(u.get("numOfCard")),json.writeValueAsString(u));}
    private void summarize(Long jobId,List<Map<String,Object>>devices){int unique=count("SELECT COUNT(DISTINCT employee_no) FROM access_person_snapshot WHERE scan_job_id=?",jobId);int common=count("SELECT COUNT(*) FROM (SELECT employee_no FROM access_person_snapshot WHERE scan_job_id=? GROUP BY employee_no HAVING COUNT(DISTINCT device_id)=?) x",jobId,devices.size());int entry=countByDirection(jobId,"ENTRY"),exit=countByDirection(jobId,"EXIT");int onlyEntry=count("SELECT COUNT(*) FROM (SELECT s.employee_no FROM access_person_snapshot s JOIN access_device d ON d.id=s.device_id WHERE s.scan_job_id=? GROUP BY s.employee_no HAVING SUM(d.direction='ENTRY')>0 AND SUM(d.direction='EXIT')=0) x",jobId);int onlyExit=count("SELECT COUNT(*) FROM (SELECT s.employee_no FROM access_person_snapshot s JOIN access_device d ON d.id=s.device_id WHERE s.scan_job_id=? GROUP BY s.employee_no HAVING SUM(d.direction='EXIT')>0 AND SUM(d.direction='ENTRY')=0) x",jobId);int conflicts=count("SELECT COUNT(*) FROM (SELECT employee_no FROM access_person_snapshot WHERE scan_job_id=? GROUP BY employee_no HAVING COUNT(DISTINCT COALESCE(person_name,''))>1 OR COUNT(DISTINCT COALESCE(valid_to,'1000-01-01'))>1) x",jobId);jdbc.update("UPDATE access_person_scan_job SET status='COMPLETED',unique_persons=?,common_persons=?,only_entry=?,only_exit=?,conflict_persons=?,message=?,completed_at=NOW() WHERE id=?",unique,common,onlyEntry,onlyExit,conflicts,"入口"+entry+"条，出口"+exit+"条；仅生成本地预览，未修改设备",jobId);}
    private int countByDirection(Long jobId,String direction){return count("SELECT COUNT(DISTINCT s.employee_no) FROM access_person_snapshot s JOIN access_device d ON d.id=s.device_id WHERE s.scan_job_id=? AND d.direction=?",jobId,direction);}
    private int count(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    private static String text(Object o){return o==null?"":String.valueOf(o).trim();}private static int number(Object o){return o instanceof Number n?n.intValue():0;}private static Timestamp time(Object o){String s=text(o);if(s.isBlank())return null;try{return Timestamp.valueOf(OffsetDateTime.parse(s).toLocalDateTime());}catch(Exception ignored){}try{return Timestamp.valueOf(s.replace('T',' ').substring(0,19));}catch(Exception ignored){return null;}}private static String phone(Map<String,Object>u){for(String key:List.of("phone","phoneNo","phoneNumber","mobile","mobilePhone","telephone")){String direct=text(u.get(key));if(direct.matches("1\\d{10}"))return direct;}Object ext=u.get("PersonInfoExtends");if(ext instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?>m)for(Object value:m.values()){String v=text(value);if(v.matches("1\\d{10}"))return v;}String employee=text(u.get("employeeNo"));return employee.matches("1\\d{10}")?employee:"";}private static String safe(String s){if(s==null)return "扫描失败";return s.length()>900?s.substring(0,900):s;}
}
