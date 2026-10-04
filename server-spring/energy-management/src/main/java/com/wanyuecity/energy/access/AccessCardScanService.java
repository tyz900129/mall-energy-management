package com.wanyuecity.energy.access;

import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessCardScanService {
    private final JdbcTemplate jdbc;
    private final HikvisionIsapiClient isapi;
    private final CredentialCryptoService crypto;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    @Async
    public void scan(Long jobId) {
        try {
            jdbc.update("UPDATE access_card_scan_job SET status='RUNNING',started_at=NOW() WHERE id=?",jobId);
            List<Map<String,Object>> devices=jdbc.queryForList("SELECT id,direction,ip_address ipAddress,http_port httpPort,login_username loginUsername,password_ciphertext passwordCiphertext,password_iv passwordIv FROM access_device WHERE enabled=1 ORDER BY id");
            jdbc.update("UPDATE access_card_scan_job SET device_total=? WHERE id=?",devices.size(),jobId);
            int completed=0;
            for(Map<String,Object> device:devices){scanDevice(jobId,device);completed++;jdbc.update("UPDATE access_card_scan_job SET device_completed=? WHERE id=?",completed,jobId);}
            summarize(jobId,devices.size());
        } catch(Exception ex) {
            log.warn("access card read-only scan failed: job={}, type={}",jobId,ex.getClass().getSimpleName());
            jdbc.update("UPDATE access_card_scan_job SET status='FAILED',message=?,completed_at=NOW() WHERE id=?",safe(ex.getMessage()),jobId);
        }
    }

    private void scanDevice(Long jobId,Map<String,Object> device) {
        Long deviceId=((Number)device.get("id")).longValue();
        String password=crypto.decrypt(String.valueOf(device.get("passwordCiphertext")),String.valueOf(device.get("passwordIv")));
        String searchId=UUID.randomUUID().toString();int position=0,total=Integer.MAX_VALUE;
        while(position<total){HikvisionIsapiClient.CardPage page=readPageWithRetry(device,password,searchId,position);total=page.total();if(page.rows().isEmpty())break;transactions.executeWithoutResult(status->{for(Map<String,Object>card:page.rows())save(jobId,deviceId,card);});position+=page.rows().size();jdbc.update("UPDATE access_card_scan_job SET card_rows=(SELECT COUNT(*) FROM access_card_snapshot WHERE scan_job_id=?) WHERE id=?",jobId,jobId);}
    }

    private HikvisionIsapiClient.CardPage readPageWithRetry(Map<String,Object>device,String password,String searchId,int position){RuntimeException failure=null;for(int attempt=1;attempt<=4;attempt++){try{return isapi.readCardPage(String.valueOf(device.get("ipAddress")),((Number)device.get("httpPort")).intValue(),String.valueOf(device.get("loginUsername")),password,searchId,position,30);}catch(RuntimeException ex){failure=ex;try{Thread.sleep(attempt*500L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw ex;}}}throw failure==null?new IllegalStateException("读取卡片分页失败"):failure;}

    private void save(Long jobId,Long deviceId,Map<String,Object> card) {
        String cardNo=text(card.get("cardNo")),employeeNo=text(card.get("employeeNo"));if(cardNo.isBlank()||employeeNo.isBlank())return;
        var encrypted=crypto.encrypt(cardNo);String last4=cardNo.length()<=4?cardNo:cardNo.substring(cardNo.length()-4);
        String raw;try{raw=json.writeValueAsString(card);}catch(Exception ex){raw="{}";}jdbc.update("INSERT INTO access_card_snapshot(scan_job_id,device_id,employee_no,card_no_hash,card_no_ciphertext,card_no_iv,card_no_last4,card_length,id_number_format,mobile_format,card_type,raw_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE employee_no=VALUES(employee_no),card_no_ciphertext=VALUES(card_no_ciphertext),card_no_iv=VALUES(card_no_iv),card_no_last4=VALUES(card_no_last4),card_length=VALUES(card_length),id_number_format=VALUES(id_number_format),mobile_format=VALUES(mobile_format),card_type=VALUES(card_type),raw_json=VALUES(raw_json)",jobId,deviceId,employeeNo,sha256(cardNo),encrypted.ciphertext(),encrypted.iv(),last4,cardNo.length(),validChineseId(cardNo)?1:0,cardNo.matches("1\\d{10}")?1:0,text(card.get("cardType")),raw);
    }

    private void summarize(Long jobId,int deviceCount){int unique=count("SELECT COUNT(DISTINCT card_no_hash) FROM access_card_snapshot WHERE scan_job_id=?",jobId);int common=count("SELECT COUNT(*) FROM (SELECT card_no_hash FROM access_card_snapshot WHERE scan_job_id=? GROUP BY card_no_hash HAVING COUNT(DISTINCT device_id)=?) x",jobId,deviceCount);int onlyEntry=countDirection(jobId,"ENTRY","EXIT"),onlyExit=countDirection(jobId,"EXIT","ENTRY");int idCards=count("SELECT COUNT(DISTINCT card_no_hash) FROM access_card_snapshot WHERE scan_job_id=? AND id_number_format=1",jobId);int conflicts=count("SELECT COUNT(*) FROM (SELECT card_no_hash FROM access_card_snapshot WHERE scan_job_id=? GROUP BY card_no_hash HAVING COUNT(DISTINCT employee_no)>1) x",jobId);jdbc.update("UPDATE access_card_scan_job SET status='COMPLETED',unique_cards=?,common_cards=?,only_entry=?,only_exit=?,id_number_cards=?,conflict_cards=?,message=?,completed_at=NOW() WHERE id=?",unique,common,onlyEntry,onlyExit,idCards,conflicts,"仅生成加密卡片比对，未修改设备",jobId);}
    private int countDirection(Long jobId,String present,String absent){return count("SELECT COUNT(*) FROM (SELECT s.card_no_hash FROM access_card_snapshot s JOIN access_device d ON d.id=s.device_id WHERE s.scan_job_id=? GROUP BY s.card_no_hash HAVING SUM(d.direction=?)>0 AND SUM(d.direction=?)=0) x",jobId,present,absent);}
    private int count(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private static String safe(String value){if(value==null||value.isBlank())return "卡片扫描失败";return value.length()>900?value.substring(0,900):value;}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static boolean validChineseId(String value){String id=value.toUpperCase(Locale.ROOT);if(!id.matches("\\d{17}[0-9X]"))return false;int[]weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};char[]checks={'1','0','X','9','8','7','6','5','4','3','2'};int sum=0;for(int i=0;i<17;i++)sum+=(id.charAt(i)-'0')*weights[i];return id.charAt(17)==checks[sum%11];}
}
