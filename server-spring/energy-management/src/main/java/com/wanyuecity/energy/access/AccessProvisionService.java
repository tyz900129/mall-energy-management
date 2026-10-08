package com.wanyuecity.energy.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccessProvisionService {
    private final JdbcTemplate jdbc;
    private final HikvisionIsapiClient isapi;
    private final CredentialCryptoService crypto;
    private final VisitorAttachmentService attachments;
    private final ObjectMapper json;

    @Transactional
    public void permanentlyDeletePerson(Long personId){
        one("SELECT id FROM access_person WHERE id=?",personId);
        List<Map<String,Object>> mappings=jdbc.queryForList("SELECT x.device_id deviceId,COALESCE(NULLIF(x.device_employee_no,''),NULLIF(x.device_person_no,''),p.person_no) employeeNo,d.device_name deviceName,d.ip_address ipAddress,d.http_port httpPort,d.login_username loginUsername,d.password_ciphertext passwordCiphertext,d.password_iv passwordIv FROM access_person_device x JOIN access_person p ON p.id=x.person_id JOIN access_device d ON d.id=x.device_id WHERE x.person_id=?",personId);
        List<String> errors=new ArrayList<>();
        for(Map<String,Object> device:mappings){
            try{isapi.deletePerson(text(device.get("ipAddress")),number(device.get("httpPort"),80),text(device.get("loginUsername")),password(device),text(device.get("employeeNo")));}
            catch(Exception ex){errors.add(text(device.get("deviceName"))+"："+safe(ex.getMessage()));}
        }
        if(!errors.isEmpty())throw BizException.badRequest("设备人员删除失败，本系统资料未删除："+String.join("；",errors));
        jdbc.update("UPDATE access_event SET person_id=NULL WHERE person_id=?",personId);
        jdbc.update("UPDATE visitor_appointment SET visitor_person_id=NULL WHERE visitor_person_id=?",personId);
        jdbc.update("DELETE FROM access_person_review WHERE person_id=?",personId);
        jdbc.update("DELETE FROM access_person_document WHERE person_id=?",personId);
        jdbc.update("DELETE FROM access_device_sync_task WHERE person_id=?",personId);
        jdbc.update("UPDATE access_person_retention_snapshot SET person_id=NULL WHERE person_id=?",personId);
        jdbc.update("DELETE FROM access_application WHERE person_id=?",personId);
        jdbc.update("DELETE FROM access_person_device WHERE person_id=?",personId);
        jdbc.update("DELETE FROM access_person WHERE id=?",personId);
    }

    @Transactional
    public Map<String,Object> approveVisitor(Long appointmentId,String operator,String note){
        Map<String,Object> v=one("SELECT * FROM visitor_appointment WHERE id=? FOR UPDATE",appointmentId);
        String status=text(v.get("status"));
        if(!Set.of("PENDING_APPROVAL","SYNC_FAILED").contains(status))throw BizException.badRequest("当前预约状态不能审批");
        List<Map<String,Object>> devices=provisioningDevices();
        if(devices.size()<2||devices.stream().noneMatch(x->"ENTRY".equals(text(x.get("direction"))))||devices.stream().noneMatch(x->"EXIT".equals(text(x.get("direction")))))throw BizException.badRequest("入口、出口设备配置不完整，暂不能审批");
        List<String> protectedDevices=devices.stream().filter(x->flag(x.get("readOnlyMode"))).map(x->text(x.get("deviceName"))).toList();
        if(!protectedDevices.isEmpty())throw BizException.badRequest("以下设备仍处于只读保护，暂不能审批："+String.join("、",protectedDevices));
        // Approval cannot begin unless all privacy-sensitive materials are complete.
        attachments.bytes(appointmentId,"ID_FRONT");attachments.bytes(appointmentId,"ID_BACK");byte[]face=attachments.faceJpeg(appointmentId);
        Long personId=longValue(v.get("visitor_person_id"));
        if(personId==null){
            jdbc.update("INSERT INTO access_person(person_no,person_name,gender,person_type,phone,id_number_ciphertext,id_number_iv,id_number_last4,organization_name,affiliation_type,stall_id,valid_from,valid_to,face_status,source_type,managed_by_system,person_status,review_status,approval_status,approved_by,approved_at,remark) VALUES('PENDING',?,?,?,?,?,?,?,?,?,?,?,?,?,'SYSTEM',1,'SYNCING','NONE','APPROVED',?,NOW(),'由门禁预约审批创建')",
                    v.get("visitor_name"),v.get("gender"),"VISITOR",v.get("visitor_phone"),v.get("id_number_ciphertext"),v.get("id_number_iv"),v.get("id_number_last4"),v.get("visitor_company"),v.get("affiliation_type"),v.get("stall_id"),v.get("valid_from"),v.get("valid_to"),"PRESENT",operator);
            personId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            jdbc.update("UPDATE access_person SET person_no=CAST(id AS CHAR),employee_no=CAST(id AS CHAR) WHERE id=?",personId);
            jdbc.update("UPDATE visitor_appointment SET visitor_person_id=? WHERE id=?",personId,appointmentId);
        }
        Long applicationId=application(personId,appointmentId,v,operator);
        jdbc.update("UPDATE visitor_appointment SET status='SYNCING',approved_by=?,approved_at=NOW(),approval_note=? WHERE id=?",operator,note,appointmentId);
        String employeeNo=String.valueOf(personId);
        String batch="AC"+System.currentTimeMillis();List<Map<String,Object>>completed=new ArrayList<>();String failure=null;
        for(Map<String,Object>d:devices){Long deviceId=longValue(d.get("id"));task(batch,applicationId,personId,deviceId,"UPSERT");try{String password=password(d);isapi.createPerson(text(d.get("ipAddress")),number(d.get("httpPort"),80),text(d.get("loginUsername")),password,employeeNo,text(v.get("visitor_name")),timestamp(v.get("valid_from")).toLocalDateTime(),timestamp(v.get("valid_to")).toLocalDateTime(),face);completed.add(d);taskDone(batch,deviceId,"UPSERT");jdbc.update("INSERT INTO access_person_device(person_id,device_id,device_person_no,device_employee_no,device_person_name,device_phone,device_valid_from,device_valid_to,face_present,sync_status,last_read_at) VALUES(?,?,?,?,?,?,?,?,1,'SYNCED',NOW()) ON DUPLICATE KEY UPDATE device_person_no=VALUES(device_person_no),device_employee_no=VALUES(device_employee_no),device_person_name=VALUES(device_person_name),device_phone=VALUES(device_phone),device_valid_from=VALUES(device_valid_from),device_valid_to=VALUES(device_valid_to),face_present=1,sync_status='SYNCED',last_error=NULL,last_read_at=NOW()",personId,deviceId,employeeNo,employeeNo,v.get("visitor_name"),v.get("visitor_phone"),v.get("valid_from"),v.get("valid_to"));}catch(Exception ex){failure=safe(ex.getMessage());try{isapi.deletePerson(text(d.get("ipAddress")),number(d.get("httpPort"),80),text(d.get("loginUsername")),password(d),employeeNo);}catch(Exception ignored){}taskFailed(batch,deviceId,"UPSERT",failure);break;}}
        if(failure!=null){for(Map<String,Object>d:completed)try{isapi.deletePerson(text(d.get("ipAddress")),number(d.get("httpPort"),80),text(d.get("loginUsername")),password(d),employeeNo);}catch(Exception ignored){}markFailed(appointmentId,personId,applicationId,"设备同步失败，已回滚："+failure);return result(appointmentId);}
        jdbc.update("UPDATE visitor_appointment SET status='ACTIVE',face_status='PRESENT' WHERE id=?",appointmentId);
        jdbc.update("UPDATE access_person SET person_status='ACTIVE',approval_status='APPROVED',managed_by_system=1 WHERE id=?",personId);
        jdbc.update("UPDATE access_application SET status='ACTIVE' WHERE id=?",applicationId);
        return result(appointmentId);
    }

    @Transactional
    public Map<String,Object> rejectVisitor(Long appointmentId,String operator,String note){if(note==null||note.isBlank())throw BizException.badRequest("拒绝时必须填写原因");int n=jdbc.update("UPDATE visitor_appointment SET status='REJECTED',approved_by=?,approved_at=NOW(),approval_note=? WHERE id=? AND status IN ('PENDING_APPROVAL','SYNC_FAILED')",operator,note.trim(),appointmentId);if(n==0)throw BizException.badRequest("当前预约状态不能拒绝");return result(appointmentId);}

    @Transactional
    public Map<String,Object> decideRenewal(Long applicationId,String decision,String operator,String note){
        Map<String,Object> app=one("SELECT a.*,p.person_no personNo,p.person_name personName,p.valid_from personValidFrom FROM access_application a JOIN access_person p ON p.id=a.person_id WHERE a.id=? FOR UPDATE",applicationId);
        if(!"RENEWAL".equals(text(app.get("application_type"))))throw BizException.badRequest("该申请不是续期申请");
        if(!Set.of("PENDING_APPROVAL","SYNC_FAILED").contains(text(app.get("status"))))throw BizException.badRequest("当前续期状态不能审批");
        if("REJECTED".equals(decision)){if(note==null||note.isBlank())throw BizException.badRequest("拒绝时必须填写原因");jdbc.update("UPDATE access_application SET status='REJECTED',approved_by=?,approved_at=NOW(),approval_note=? WHERE id=?",operator,note.trim(),applicationId);return renewalResult(applicationId);}
        if(!"APPROVED".equals(decision))throw BizException.badRequest("审批结果不正确");
        List<Map<String,Object>>devices=provisioningDevices();ensureWritablePair(devices);
        Long personId=longValue(app.get("person_id"));String employee=text(app.get("personNo"));String batch="RN"+System.currentTimeMillis();String failure=null;
        for(Map<String,Object>d:devices){Long deviceId=longValue(d.get("id"));task(batch,applicationId,personId,deviceId,"UPDATE_VALIDITY");try{isapi.updatePersonValidity(text(d.get("ipAddress")),number(d.get("httpPort"),80),text(d.get("loginUsername")),password(d),employee,text(app.get("personName")),dateTime(app.get("personValidFrom")),dateTime(app.get("requested_valid_to")));taskDone(batch,deviceId,"UPDATE_VALIDITY");jdbc.update("UPDATE access_person_device SET device_valid_to=?,sync_status='SYNCED',last_error=NULL,last_read_at=NOW() WHERE person_id=? AND device_id=?",app.get("requested_valid_to"),personId,deviceId);}catch(Exception ex){failure=safe(ex.getMessage());taskFailed(batch,deviceId,"UPDATE_VALIDITY",failure);break;}}
        if(failure!=null){jdbc.update("UPDATE access_application SET status='SYNC_FAILED',approved_by=?,approved_at=NOW(),approval_note=? WHERE id=?",operator,"续期下发失败："+failure,applicationId);return renewalResult(applicationId);}
        jdbc.update("UPDATE access_person SET valid_to=?,person_status='ACTIVE',approval_status='APPROVED',approved_by=?,approved_at=NOW() WHERE id=?",app.get("requested_valid_to"),operator,personId);
        jdbc.update("UPDATE access_application SET status='ACTIVE',approved_by=?,approved_at=NOW(),approval_note=? WHERE id=?",operator,note,applicationId);
        return renewalResult(applicationId);
    }

    @Transactional
    public Map<String,Object> revokeVisitor(Long appointmentId,String operator,String reason){Map<String,Object>v=one("SELECT * FROM visitor_appointment WHERE id=? FOR UPDATE",appointmentId);Long personId=longValue(v.get("visitor_person_id"));if(personId==null||!Set.of("ACTIVE","SYNC_FAILED","REVOKE_FAILED").contains(text(v.get("status"))))throw BizException.badRequest("当前预约没有可撤销的设备权限");String removalError=removeFromDevices(personId);if(removalError!=null){jdbc.update("UPDATE visitor_appointment SET status='REVOKE_FAILED',approval_note=? WHERE id=?","部分设备删除失败，可重试："+removalError,appointmentId);jdbc.update("UPDATE access_person SET person_status='REVOKE_PENDING',approval_status='REVOKE_FAILED' WHERE id=?",personId);return result(appointmentId);}retainSnapshot(personId,appointmentId,"REVOKED",operator,reason);jdbc.update("UPDATE visitor_appointment SET status='REVOKED',terminated_at=NOW(),approval_note=CONCAT(COALESCE(approval_note,''),' 撤销：',?) WHERE id=?",reason,appointmentId);jdbc.update("UPDATE access_person SET person_status='DISABLED',approval_status='REVOKED',revoked_at=NOW(),retention_until=DATE_ADD(NOW(),INTERVAL 6 MONTH) WHERE id=?",personId);jdbc.update("UPDATE access_application SET status='REVOKED',revoked_at=NOW(),revoke_reason=?,snapshot_retention_until=DATE_ADD(NOW(),INTERVAL 6 MONTH) WHERE appointment_id=?",reason,appointmentId);return result(appointmentId);}

    @Scheduled(cron="0 */10 * * * *")
    public void expireDueVisitors(){List<Long>ids=jdbc.queryForList("SELECT id FROM visitor_appointment WHERE status IN ('ACTIVE','REVOKE_FAILED') AND valid_to<=NOW() ORDER BY id LIMIT 50",Long.class);for(Long id:ids)try{Map<String,Object>done=revokeVisitor(id,"SYSTEM","有效期已届满");if("REVOKED".equals(done.get("status")))jdbc.update("UPDATE visitor_appointment SET status='EXPIRED' WHERE id=?",id);}catch(Exception ignored){}jdbc.update("UPDATE access_person_retention_snapshot SET snapshot_ciphertext='',snapshot_iv='',purged_at=NOW() WHERE retained_until<NOW() AND purged_at IS NULL");}

    private String removeFromDevices(Long personId){Map<String,Object>p=one("SELECT person_no personNo FROM access_person WHERE id=?",personId);String employee=text(p.get("personNo"));List<Map<String,Object>>rows=jdbc.queryForList("SELECT d.id,d.ip_address ipAddress,d.http_port httpPort,d.login_username loginUsername,d.password_ciphertext passwordCiphertext,d.password_iv passwordIv FROM access_device d JOIN access_person_device x ON x.device_id=d.id WHERE x.person_id=? AND x.sync_status IN ('SYNCED','DELETE_FAILED')",personId);List<String>errors=new ArrayList<>();for(Map<String,Object>d:rows){try{isapi.deletePerson(text(d.get("ipAddress")),number(d.get("httpPort"),80),text(d.get("loginUsername")),password(d),employee);jdbc.update("UPDATE access_person_device SET sync_status='REMOVED',face_present=0,last_error=NULL,last_read_at=NOW() WHERE person_id=? AND device_id=?",personId,d.get("id"));}catch(Exception ex){String error=safe(ex.getMessage());errors.add(error);jdbc.update("UPDATE access_person_device SET sync_status='DELETE_FAILED',last_error=? WHERE person_id=? AND device_id=?",error,personId,d.get("id"));}}return errors.isEmpty()?null:String.join("；",errors);
    }

    private void retainSnapshot(Long personId,Long appointmentId,String type,String operator,String reason){try{Map<String,Object>data=new LinkedHashMap<>(one("SELECT person_no,person_name,gender,person_type,phone,id_number_last4,organization_name,affiliation_type,stall_id,valid_from,valid_to FROM access_person WHERE id=?",personId));data.put("operator",operator);data.put("reason",reason);var e=crypto.encrypt(json.writeValueAsString(data));jdbc.update("INSERT INTO access_person_retention_snapshot(person_id,application_id,snapshot_type,snapshot_ciphertext,snapshot_iv,retained_until) VALUES(?,(SELECT id FROM access_application WHERE appointment_id=? ORDER BY id DESC LIMIT 1),?,?,?,DATE_ADD(NOW(),INTERVAL 6 MONTH))",personId,appointmentId,type,e.ciphertext(),e.iv());}catch(Exception ex){throw new IllegalStateException("人员快照保存失败",ex);}}
    private Long application(Long personId,Long appointmentId,Map<String,Object>v,String username){List<Long>ids=jdbc.queryForList("SELECT id FROM access_application WHERE appointment_id=?",Long.class,appointmentId);if(!ids.isEmpty())return ids.get(0);String no="AA"+System.currentTimeMillis();jdbc.update("INSERT INTO access_application(application_no,application_type,person_id,appointment_id,requested_valid_from,requested_valid_to,status,applicant_username,approved_by,approved_at) VALUES(?,'VISITOR',?,?,?,?,'SYNCING',?,?,NOW())",no,personId,appointmentId,v.get("valid_from"),v.get("valid_to"),v.get("applicant_username"),username);return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    private List<Map<String,Object>> provisioningDevices(){return jdbc.queryForList("SELECT id,device_name deviceName,direction,read_only_mode readOnlyMode,ip_address ipAddress,http_port httpPort,login_username loginUsername,password_ciphertext passwordCiphertext,password_iv passwordIv FROM access_device WHERE enabled=1 AND isapi_enabled=1 ORDER BY id");}
    private void ensureWritablePair(List<Map<String,Object>>devices){if(devices.size()<2||devices.stream().noneMatch(x->"ENTRY".equals(text(x.get("direction"))))||devices.stream().noneMatch(x->"EXIT".equals(text(x.get("direction")))))throw BizException.badRequest("入口、出口设备配置不完整，暂不能审批");List<String>locked=devices.stream().filter(x->flag(x.get("readOnlyMode"))).map(x->text(x.get("deviceName"))).toList();if(!locked.isEmpty())throw BizException.badRequest("以下设备仍处于只读保护，暂不能审批："+String.join("、",locked));}
    private String password(Map<String,Object>d){return crypto.decrypt(text(d.get("passwordCiphertext")),text(d.get("passwordIv")));}
    private void task(String batch,Long app,Long person,Long device,String op){jdbc.update("INSERT INTO access_device_sync_task(batch_no,application_id,person_id,device_id,operation_type,status,attempt_count,started_at) VALUES(?,?,?,?,?,'RUNNING',1,NOW())",batch,app,person,device,op);}
    private void taskDone(String batch,Long device,String op){jdbc.update("UPDATE access_device_sync_task SET status='SUCCESS',completed_at=NOW(),device_response='写入并校验成功' WHERE batch_no=? AND device_id=? AND operation_type=?",batch,device,op);}
    private void taskFailed(String batch,Long device,String op,String error){jdbc.update("UPDATE access_device_sync_task SET status='FAILED',completed_at=NOW(),last_error=? WHERE batch_no=? AND device_id=? AND operation_type=?",error,batch,device,op);}
    private void markFailed(Long appointment,Long person,Long app,String error){jdbc.update("UPDATE visitor_appointment SET status='SYNC_FAILED',approval_note=? WHERE id=?",error,appointment);jdbc.update("UPDATE access_person SET person_status='PENDING_REVIEW',approval_status='SYNC_FAILED' WHERE id=?",person);jdbc.update("UPDATE access_application SET status='SYNC_FAILED',approval_note=? WHERE id=?",error,app);}
    private Map<String,Object>result(Long id){return one("SELECT id,appointment_no appointmentNo,status,visitor_person_id visitorPersonId,approved_by approvedBy,approved_at approvedAt,approval_note approvalNote FROM visitor_appointment WHERE id=?",id);}
    private Map<String,Object>renewalResult(Long id){return one("SELECT id,application_no applicationNo,application_type applicationType,status,person_id personId,requested_valid_from requestedValidFrom,requested_valid_to requestedValidTo,approved_by approvedBy,approved_at approvedAt,approval_note approvalNote FROM access_application WHERE id=?",id);}
    private Map<String,Object>one(String sql,Object...args){List<Map<String,Object>>rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw BizException.notFound("记录不存在");return rows.get(0);}
    private static String text(Object o){return o==null?"":String.valueOf(o).trim();}private static Long longValue(Object o){return o instanceof Number n?n.longValue():o==null?null:Long.valueOf(String.valueOf(o));}private static int number(Object o,int d){return o instanceof Number n?n.intValue():o==null?d:Integer.parseInt(String.valueOf(o));}private static boolean flag(Object o){return o instanceof Boolean b?b:o instanceof Number n&&n.intValue()!=0||o!=null&&Set.of("1","true","yes").contains(String.valueOf(o).toLowerCase(Locale.ROOT));}private static Timestamp timestamp(Object o){return o instanceof Timestamp t?t:o instanceof LocalDateTime t?Timestamp.valueOf(t):o instanceof java.util.Date d?new Timestamp(d.getTime()):Timestamp.valueOf(String.valueOf(o));}private static LocalDateTime dateTime(Object o){return timestamp(o).toLocalDateTime();}private static String safe(String s){if(s==null)return "设备操作失败";return s.length()>900?s.substring(0,900):s;}
}
