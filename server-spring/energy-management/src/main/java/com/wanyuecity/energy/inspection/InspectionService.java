package com.wanyuecity.energy.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import com.wanyuecity.energy.notification.service.NotificationService;
import java.io.ByteArrayOutputStream;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InspectionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NotificationService notificationService;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    public List<Map<String,Object>> types() {
        return jdbc.queryForList("SELECT id,code,name,target_category targetCategory,description,enabled,created_at createdAt,updated_at updatedAt FROM inspection_type ORDER BY enabled DESC,id DESC");
    }

    @Transactional
    public Map<String,Object> saveType(Long id, Map<String,Object> body) {
        require(body, "code", "类型编码不能为空"); require(body, "name", "类型名称不能为空");
        if (id == null) {
            jdbc.update("INSERT INTO inspection_type(code,name,target_category,description,enabled) VALUES(?,?,?,?,?)",
                    text(body,"code").trim().toUpperCase(), text(body,"name"), defaultText(body,"targetCategory","DEVICE"), text(body,"description"), boolInt(body,"enabled",1));
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            jdbc.update("UPDATE inspection_type SET code=?,name=?,target_category=?,description=?,enabled=? WHERE id=?",
                    text(body,"code").trim().toUpperCase(), text(body,"name"), defaultText(body,"targetCategory","DEVICE"), text(body,"description"), boolInt(body,"enabled",1), id);
        }
        return one("SELECT id,code,name,target_category targetCategory,description,enabled FROM inspection_type WHERE id=?", id);
    }

    public List<Map<String,Object>> templates() {
        return jdbc.queryForList("SELECT t.id,t.inspection_type_id inspectionTypeId,y.name inspectionTypeName,t.name,t.version_no versionNo,t.fields_json fieldsJson,t.enabled,t.updated_at updatedAt FROM inspection_template t JOIN inspection_type y ON y.id=t.inspection_type_id ORDER BY t.enabled DESC,t.id DESC");
    }

    @Transactional
    public Map<String,Object> saveTemplate(Long id, Map<String,Object> body) {
        Long typeId = longValue(body.get("inspectionTypeId"));
        if (typeId == null) bad("请选择巡检类型"); require(body,"name","模板名称不能为空");
        String fields = jsonText(body.get("fields"));
        if ("[]".equals(fields)) bad("至少配置一个巡检项");
        if (id == null) {
            jdbc.update("INSERT INTO inspection_template(inspection_type_id,name,version_no,fields_json,enabled) VALUES(?,?,?,?,?)",
                    typeId,text(body,"name"),intValue(body.get("versionNo"),1),fields,boolInt(body,"enabled",1));
            id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        } else {
            jdbc.update("UPDATE inspection_template SET inspection_type_id=?,name=?,version_no=?,fields_json=?,enabled=? WHERE id=?",
                    typeId,text(body,"name"),intValue(body.get("versionNo"),1),fields,boolInt(body,"enabled",1),id);
        }
        return one("SELECT id,inspection_type_id inspectionTypeId,name,version_no versionNo,fields_json fieldsJson,enabled FROM inspection_template WHERE id=?",id);
    }

    public List<Map<String,Object>> targetOptions(String keyword) {
        String key="%"+(keyword==null?"":keyword.trim())+"%";
        return jdbc.queryForList("SELECT CAST(e.id AS CHAR) targetId,e.equipment_code targetCode,e.equipment_name targetName,e.installation_location locationText,c.name categoryName,e.equipment_status equipmentStatus,e.floor_code floorCode,e.area_name areaName FROM inspection_equipment e JOIN inspection_equipment_category c ON c.id=e.category_id WHERE e.enabled=1 AND (?='%%' OR e.equipment_code LIKE ? OR e.equipment_name LIKE ? OR e.serial_number LIKE ? OR e.installation_location LIKE ?) ORDER BY c.sort_order,e.equipment_code LIMIT 500",key,key,key,key,key);
    }

    public List<Map<String,Object>> plans() {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id,p.name,p.inspection_type_id inspectionTypeId,y.name inspectionTypeName,p.template_id templateId,t.name templateName,p.frequency_type frequencyType,p.frequency_value frequencyValue,p.schedule_config_json scheduleConfigJson,p.start_date startDate,p.end_date endDate,p.execute_time executeTime,p.overdue_minutes overdueMinutes,p.assignee_user_id assigneeUserId,p.assignee_name assigneeName,p.webhook_config_ids webhookConfigIds,p.enabled,p.last_generated_at lastGeneratedAt,p.updated_at updatedAt,(SELECT COUNT(*) FROM inspection_plan_target x WHERE x.plan_id=p.id AND x.enabled=1) targetCount FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id JOIN inspection_template t ON t.id=p.template_id ORDER BY p.enabled DESC,p.id DESC");
        for(Map<String,Object> row:rows) row.put("targets",jdbc.queryForList("SELECT id,target_type targetType,target_id targetId,target_code targetCode,target_name targetName,location_text locationText,enabled FROM inspection_plan_target WHERE plan_id=? ORDER BY id",row.get("id")));
        return rows;
    }

    @Transactional
    public Map<String,Object> savePlan(Long id, Map<String,Object> body) {
        require(body,"name","计划名称不能为空");
        Long typeId=longValue(body.get("inspectionTypeId")),templateId=longValue(body.get("templateId"));
        if(typeId==null||templateId==null) bad("请选择巡检类型和表单模板");
        LocalDate start=parseDate(body.get("startDate")); if(start==null) bad("请选择开始日期");
        LocalTime execute=parseTime(body.get("executeTime")); if(execute==null) bad("请选择执行时间");
        Object[] args={text(body,"name"),typeId,templateId,defaultText(body,"frequencyType","DAILY"),intValue(body.get("frequencyValue"),1),jsonText(body.get("scheduleConfig")),Date.valueOf(start),sqlDate(body.get("endDate")),Time.valueOf(execute),intValue(body.get("overdueMinutes"),1440),longValue(body.get("assigneeUserId")),text(body,"assigneeName"),jsonText(body.get("webhookConfigIds")),boolInt(body,"enabled",1)};
        if(id==null){jdbc.update("INSERT INTO inspection_plan(name,inspection_type_id,template_id,frequency_type,frequency_value,schedule_config_json,start_date,end_date,execute_time,overdue_minutes,assignee_user_id,assignee_name,webhook_config_ids,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",args);id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
        else {Object[] update=Arrays.copyOf(args,args.length+1);update[args.length]=id;jdbc.update("UPDATE inspection_plan SET name=?,inspection_type_id=?,template_id=?,frequency_type=?,frequency_value=?,schedule_config_json=?,start_date=?,end_date=?,execute_time=?,overdue_minutes=?,assignee_user_id=?,assignee_name=?,webhook_config_ids=?,enabled=? WHERE id=?",update);jdbc.update("DELETE FROM inspection_plan_target WHERE plan_id=?",id);}
        Object targets=body.get("targets");
        if(targets instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> m){
            jdbc.update("INSERT INTO inspection_plan_target(plan_id,target_type,target_id,target_code,target_name,location_text,enabled) VALUES(?,?,?,?,?,?,1)",id,defaultText(m,"targetType","PLATFORM_DEVICE"),text(m,"targetId"),text(m,"targetCode"),text(m,"targetName"),text(m,"locationText"));
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target WHERE plan_id=?",Integer.class,id)==0) bad("至少选择一个巡检对象");
        Integer invalid=jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target x JOIN inspection_equipment e ON e.id=CAST(x.target_id AS UNSIGNED) LEFT JOIN inspection_category_template ct ON ct.category_id=e.category_id AND ct.template_id=? AND ct.enabled=1 WHERE x.plan_id=? AND x.target_type='INSPECTION_EQUIPMENT' AND ct.id IS NULL",Integer.class,templateId,id);
        if(invalid!=null&&invalid>0)bad("所选设备分类尚未关联该巡检表单，请先在设备分类中配置关联表单");
        generatePlan(id,LocalDate.now());
        Long savedId = id;
        return plans().stream().filter(p->Objects.equals(longValue(p.get("id")),savedId)).findFirst().orElseThrow();
    }

    @Scheduled(cron="0 */5 * * * *")
    public void scheduledGenerate(){ generateAll(); markOverdue(); sendReminders(); }

    public int generateAll(){int total=0;for(Map<String,Object> p:jdbc.queryForList("SELECT id FROM inspection_plan WHERE enabled=1 AND start_date<=CURRENT_DATE AND (end_date IS NULL OR end_date>=CURRENT_DATE)")) total+=generatePlan(longValue(p.get("id")),LocalDate.now());return total;}

    @Transactional
    public int generatePlan(Long planId, LocalDate day){
        Map<String,Object> p=one("SELECT * FROM inspection_plan WHERE id=?",planId);
        if(intValue(p.get("enabled"),0)!=1||day.isBefore(toDate(p.get("start_date")))||(p.get("end_date")!=null&&day.isAfter(toDate(p.get("end_date"))))) return 0;
        if(!due(p,day)) return 0;
        LocalTime execute=((Time)p.get("execute_time")).toLocalTime(); LocalDateTime scheduled=LocalDateTime.of(day,execute); String period=periodKey(p,day);
        int count=0;
        for(Map<String,Object> t:jdbc.queryForList("SELECT * FROM inspection_plan_target WHERE plan_id=? AND enabled=1",planId)) try{
            String no="XJ"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+String.format("%04d",Math.abs(UUID.randomUUID().hashCode())%10000);
            jdbc.update("INSERT INTO inspection_task(task_no,plan_id,inspection_type_id,template_id,target_type,target_id,target_code,target_name,location_text,equipment_snapshot_json,period_key,scheduled_at,due_at,assignee_user_id,assignee_name,access_token,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING')",no,planId,p.get("inspection_type_id"),p.get("template_id"),t.get("target_type"),t.get("target_id"),t.get("target_code"),t.get("target_name"),t.get("location_text"),equipmentSnapshot(t),period,Timestamp.valueOf(scheduled),Timestamp.valueOf(scheduled.plusMinutes(intValue(p.get("overdue_minutes"),1440))),p.get("assignee_user_id"),p.get("assignee_name"),UUID.randomUUID().toString().replace("-","") );count++;
        }catch(DuplicateKeyException ignored){}
        jdbc.update("UPDATE inspection_plan SET last_generated_at=NOW(3) WHERE id=?",planId);return count;
    }

    public List<Map<String,Object>> tasks(String status,String keyword,String username,boolean ownOnly){
        StringBuilder sql=new StringBuilder("SELECT k.id,k.task_no taskNo,k.plan_id planId,p.name planName,y.name inspectionTypeName,k.target_type targetType,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.status,k.started_at startedAt,k.submitted_at submittedAt,k.result_summary resultSummary,k.completed_by completedBy FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id WHERE 1=1");
        List<Object> args=new ArrayList<>(); if(status!=null&&!status.isBlank()){sql.append(" AND k.status=?");args.add(status);} if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (k.task_no LIKE ? OR k.target_name LIKE ? OR k.target_code LIKE ?)");String x="%"+keyword+"%";args.add(x);args.add(x);args.add(x);} if(ownOnly){sql.append(" AND (k.assignee_name=? OR k.assignee_user_id=(SELECT id FROM sys_user WHERE username=? LIMIT 1))");args.add(username);args.add(username);} sql.append(" ORDER BY k.scheduled_at DESC,k.id DESC LIMIT 500");return jdbc.queryForList(sql.toString(),args.toArray());
    }

    public Map<String,Object> task(Long id){
        Map<String,Object> r=one("SELECT k.*,k.task_no taskNo,k.plan_id planId,k.target_type targetType,k.target_id targetId,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.equipment_snapshot_json equipmentSnapshotJson,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.started_at startedAt,k.submitted_at submittedAt,k.result_json resultJson,k.result_summary resultSummary,k.completed_by completedBy,p.name planName,y.name inspectionTypeName,t.name templateName,t.fields_json fieldsJson FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id JOIN inspection_template t ON t.id=k.template_id WHERE k.id=?",id);
        r.put("issues",issues(id,null));return r;
    }
    public Map<String,Object> taskByToken(String token){Map<String,Object> row=one("SELECT id FROM inspection_task WHERE access_token=?",token);return task(longValue(row.get("id")));}
    public byte[] qr(Long id){Map<String,Object> t=task(id);try{String url=publicBaseUrl.replaceAll("/$","")+"/inspection/tasks?taskToken="+t.get("accessToken");BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,420,420);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}catch(Exception e){bad("巡检二维码生成失败");return new byte[0];}}
    public byte[] qrByToken(String token){Map<String,Object> row=one("SELECT id FROM inspection_task WHERE access_token=?",token);return qr(longValue(row.get("id")));}

    @Transactional
    public Map<String,Object> submit(Long id,Map<String,Object> body,String username){
        Map<String,Object> task=task(id);String status=String.valueOf(task.get("status"));if(Set.of("COMPLETED","CLOSED","CANCELLED").contains(status)) bad("该任务已结束，不能重复提交");
        String result=jsonText(body.get("result"));Object issues=body.get("issues");int issueCount=issues instanceof List<?> l?l.size():0;
        jdbc.update("UPDATE inspection_task SET status=?,started_at=COALESCE(started_at,NOW(3)),submitted_at=NOW(3),result_json=?,result_summary=?,completed_by=? WHERE id=?",issueCount>0?"PENDING_RECTIFICATION":"COMPLETED",result,text(body,"summary"),username,id);
        jdbc.update("DELETE FROM inspection_issue WHERE task_id=? AND status='PENDING'",id);
        if(issues instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> m) jdbc.update("INSERT INTO inspection_issue(task_id,field_key,field_label,level,description,rectification_requirement,responsible_user_id,responsible_name,due_at,status) VALUES(?,?,?,?,?,?,?,?,?,'PENDING')",id,text(m,"fieldKey"),text(m,"fieldLabel"),defaultText(m,"level","WARNING"),text(m,"description"),text(m,"rectificationRequirement"),longValue(m.get("responsibleUserId")),text(m,"responsibleName"),sqlTimestamp(m.get("dueAt")));
        return task(id);
    }

    public List<Map<String,Object>> issues(Long taskId,String status){String sql="SELECT i.id,i.task_id taskId,k.task_no taskNo,k.target_name targetName,i.field_key fieldKey,i.field_label fieldLabel,i.level,i.description,i.rectification_requirement rectificationRequirement,i.responsible_user_id responsibleUserId,i.responsible_name responsibleName,i.due_at dueAt,i.status,i.rectification_note rectificationNote,i.rectified_by rectifiedBy,i.rectified_at rectifiedAt,i.reviewed_by reviewedBy,i.reviewed_at reviewedAt,i.created_at createdAt FROM inspection_issue i JOIN inspection_task k ON k.id=i.task_id WHERE (? IS NULL OR i.task_id=?) AND (? IS NULL OR i.status=?) ORDER BY i.id DESC";return jdbc.queryForList(sql,taskId,taskId,status,status);}

    @Transactional public Map<String,Object> rectify(Long id,String note,String user){if(note==null||note.isBlank())bad("请填写整改说明");jdbc.update("UPDATE inspection_issue SET status='RECTIFIED',rectification_note=?,rectified_by=?,rectified_at=NOW(3) WHERE id=?",note,user,id);return one("SELECT * FROM inspection_issue WHERE id=?",id);}
    @Transactional public Map<String,Object> review(Long id,boolean passed,String user){Map<String,Object> issue=one("SELECT * FROM inspection_issue WHERE id=?",id);jdbc.update("UPDATE inspection_issue SET status=?,reviewed_by=?,reviewed_at=NOW(3) WHERE id=?",passed?"CLOSED":"PENDING",user,id);Long taskId=longValue(issue.get("task_id"));if(passed&&jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE task_id=? AND status<>'CLOSED'",Integer.class,taskId)==0)jdbc.update("UPDATE inspection_task SET status='CLOSED' WHERE id=?",taskId);else if(!passed)jdbc.update("UPDATE inspection_task SET status='PENDING_RECTIFICATION' WHERE id=?",taskId);return one("SELECT * FROM inspection_issue WHERE id=?",id);}

    public Map<String,Object> dashboard(){
        Map<String,Object> r=new LinkedHashMap<>();r.put("summary",one("SELECT COUNT(*) total,SUM(status='PENDING') pending,SUM(status='IN_PROGRESS') inProgress,SUM(status IN ('COMPLETED','CLOSED')) completed,SUM(status='OVERDUE') overdue,SUM(status='PENDING_RECTIFICATION') rectification FROM inspection_task WHERE scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01')"));r.put("typeStats",jdbc.queryForList("SELECT y.name typeName,COUNT(*) total,SUM(k.status IN ('COMPLETED','CLOSED')) completed,SUM(k.status='OVERDUE') overdue FROM inspection_task k JOIN inspection_type y ON y.id=k.inspection_type_id WHERE k.scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') GROUP BY y.id,y.name ORDER BY total DESC"));r.put("recent",jdbc.queryForList("SELECT DATE(scheduled_at) day,COUNT(*) total,SUM(status IN ('COMPLETED','CLOSED')) completed FROM inspection_task WHERE scheduled_at>=CURRENT_DATE-INTERVAL 13 DAY GROUP BY DATE(scheduled_at) ORDER BY day"));r.put("openIssues",jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE status<>'CLOSED'",Integer.class));return r;
    }

    private void markOverdue(){jdbc.update("UPDATE inspection_task SET status='OVERDUE' WHERE status IN ('PENDING','IN_PROGRESS') AND due_at<NOW(3)");}
    private String equipmentSnapshot(Map<String,Object> target){if(!"INSPECTION_EQUIPMENT".equals(String.valueOf(target.get("target_type"))))return "{}";List<Map<String,Object>> rows=jdbc.queryForList("SELECT e.equipment_code equipmentCode,e.equipment_name equipmentName,c.name categoryName,e.system_name systemName,e.installation_location installationLocation,e.floor_code floorCode,e.area_name areaName,e.brand,e.model,e.specification,e.serial_number serialNumber,e.responsible_department responsibleDepartment,e.responsible_person responsiblePerson,e.responsible_phone responsiblePhone,e.equipment_status equipmentStatus,e.importance_level importanceLevel,e.attributes_json attributesJson FROM inspection_equipment e JOIN inspection_equipment_category c ON c.id=e.category_id WHERE e.id=?",target.get("target_id"));return rows.isEmpty()?"{}":jsonText(rows.get(0));}
    private void sendReminders(){
        List<Map<String,Object>> due=jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status IN ('PENDING','IN_PROGRESS') AND k.due_at BETWEEN NOW(3) AND DATE_ADD(NOW(3),INTERVAL 2 HOUR)");
        for(Map<String,Object> x:due) notifyOnce(longValue(x.get("id")),"INSPECTION_TASK_DUE",String.valueOf(x.getOrDefault("webhookIds","[]")));
        List<Map<String,Object>> overdue=jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status='OVERDUE' AND k.due_at>=DATE_SUB(NOW(3),INTERVAL 1 DAY)");
        for(Map<String,Object> x:overdue) notifyOnce(longValue(x.get("id")),"INSPECTION_TASK_OVERDUE",String.valueOf(x.getOrDefault("webhookIds","[]")));
    }
    private void notifyOnce(Long taskId,String event,String configIds){try{jdbc.update("INSERT INTO inspection_reminder_log(task_id,event_type) VALUES(?,?)",taskId,event);notificationService.notifyInspectionTask(taskId,event,configIds);}catch(DuplicateKeyException ignored){}catch(Exception ignored){jdbc.update("DELETE FROM inspection_reminder_log WHERE task_id=? AND event_type=?",taskId,event);}}
    private boolean due(Map<String,Object> p,LocalDate d){String type=String.valueOf(p.get("frequency_type"));int v=intValue(p.get("frequency_value"),1);LocalDate start=toDate(p.get("start_date"));return switch(type){case "WEEKLY"->Math.floorMod((int)(d.toEpochDay()-start.toEpochDay()),7*v)==0;case "MONTHLY"->d.getDayOfMonth()==Math.min(start.getDayOfMonth(),d.lengthOfMonth())&&Math.floorMod((d.getYear()-start.getYear())*12+d.getMonthValue()-start.getMonthValue(),v)==0;case "INTERVAL_DAYS"->Math.floorMod((int)(d.toEpochDay()-start.toEpochDay()),v)==0;default->true;};}
    private String periodKey(Map<String,Object> p,LocalDate d){return String.valueOf(p.get("frequency_type"))+":"+d;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> l=jdbc.queryForList(sql,args);if(l.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"数据不存在");return new LinkedHashMap<>(l.get(0));}
    private void require(Map<?,?> m,String k,String msg){if(text(m,k).isBlank())bad(msg);} private void bad(String msg){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private String jsonText(Object o){if(o==null)return "[]";if(o instanceof String s)return s.isBlank()?"[]":s;try{return json.writeValueAsString(o);}catch(JsonProcessingException e){bad("JSON 数据格式错误");return "[]";}}
    private static String text(Map<?,?> m,String k){Object o=m.get(k);return o==null?"":String.valueOf(o).trim();} private static String defaultText(Map<?,?>m,String k,String d){String s=text(m,k);return s.isBlank()?d:s;}
    private static Long longValue(Object o){if(o==null||String.valueOf(o).isBlank())return null;return ((Number)(o instanceof Number?o:Long.valueOf(String.valueOf(o)))).longValue();} private static int intValue(Object o,int d){if(o==null||String.valueOf(o).isBlank())return d;return ((Number)(o instanceof Number?o:Integer.valueOf(String.valueOf(o)))).intValue();}
    private static int boolInt(Map<?,?>m,String k,int d){Object o=m.get(k);if(o==null)return d;if(o instanceof Boolean b)return b?1:0;return intValue(o,d);}
    private static LocalDate parseDate(Object o){if(o==null||String.valueOf(o).isBlank())return null;return LocalDate.parse(String.valueOf(o).substring(0,10));} private static LocalTime parseTime(Object o){if(o==null||String.valueOf(o).isBlank())return null;String s=String.valueOf(o);return LocalTime.parse(s.length()==5?s+":00":s.substring(0,8));}
    private static Date sqlDate(Object o){LocalDate d=parseDate(o);return d==null?null:Date.valueOf(d);} private static Timestamp sqlTimestamp(Object o){if(o==null||String.valueOf(o).isBlank())return null;return Timestamp.valueOf(String.valueOf(o).replace("T"," ").substring(0,19));} private static LocalDate toDate(Object o){if(o instanceof Date d)return d.toLocalDate();return parseDate(o);}
}
