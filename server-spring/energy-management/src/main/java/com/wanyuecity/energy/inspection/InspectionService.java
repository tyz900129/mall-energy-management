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
    private final com.wanyuecity.energy.security.DataScopeService dataScope;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;
    // 补生成窗口：0 表示只生成当天、不补历史（当天仍在 from..to 区间内，照常生成）
    @Value("${energy.task.generate-backfill-days:0}") private int backfillDays;

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
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id,p.name,p.inspection_type_id inspectionTypeId,y.name inspectionTypeName,p.template_id templateId,t.name templateName,p.category,p.frequency_type frequencyType,p.frequency_value frequencyValue,p.schedule_config_json scheduleConfigJson,p.start_date startDate,p.end_date endDate,p.execute_time executeTime,p.overdue_minutes overdueMinutes,p.assignee_user_id assigneeUserId,p.assignee_name assigneeName,p.owner_type ownerType,p.owner_id ownerId,p.owner_dept_id ownerDeptId,p.target_mode targetMode,p.claim_mode claimMode,p.form_mode formMode,p.require_photo requirePhoto,p.advance_minutes advanceMinutes,p.urge_minutes urgeMinutes,p.webhook_config_ids webhookConfigIds,p.enabled,p.last_generated_at lastGeneratedAt,p.updated_at updatedAt,(SELECT COUNT(*) FROM inspection_plan_target x WHERE x.plan_id=p.id AND x.enabled=1) targetCount FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id JOIN inspection_template t ON t.id=p.template_id ORDER BY p.enabled DESC,p.id DESC");
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
        String ownerType=defaultText(body,"ownerType","USER");
        if(!Set.of("USER","ROLE","DEPT").contains(ownerType)) bad("归属类型无效");
        Long ownerId=longValue(body.get("ownerId"));
        if("USER".equals(ownerType)&&ownerId==null) ownerId=longValue(body.get("assigneeUserId"));
        if(!"USER".equals(ownerType)&&ownerId==null) bad("请选择责任岗位或部门");
        String targetMode=defaultText(body,"targetMode","UNIT");
        if("PER_CHILD".equals(targetMode)&&!"DEPT".equals(ownerType)) bad("按子节点下发仅支持部门任务");
        String claimMode=text(body,"claimMode");
        if(claimMode.isBlank()) claimMode="USER".equals(ownerType)?"NONE":"CLAIM";
        String formMode=defaultText(body,"formMode","CHECKLIST");
        if(!Set.of("CHECKLIST","NONE","INSPECTION").contains(formMode)) bad("回执模式无效");
        Long ownerDeptId=longValue(body.get("ownerDeptId"));
        if(ownerDeptId==null&&ownerId!=null){
            if("DEPT".equals(ownerType)) ownerDeptId=ownerId;
            else if("ROLE".equals(ownerType)){List<Long> l=jdbc.queryForList("SELECT dept_id FROM sys_role WHERE id=?",Long.class,ownerId);ownerDeptId=l.isEmpty()?null:l.get(0);}
            else if("USER".equals(ownerType)){List<Long> l=jdbc.queryForList("SELECT department_id FROM sys_user WHERE id=?",Long.class,ownerId);ownerDeptId=l.isEmpty()?null:l.get(0);}
        }
        boolean fanout="DEPT".equals(ownerType)&&"PER_CHILD".equals(targetMode);
        Object[] args={text(body,"name"),typeId,templateId,defaultText(body,"category","OTHER"),defaultText(body,"frequencyType","DAILY"),intValue(body.get("frequencyValue"),1),jsonText(body.get("scheduleConfig")),Date.valueOf(start),sqlDate(body.get("endDate")),Time.valueOf(execute),intValue(body.get("overdueMinutes"),1440),longValue(body.get("assigneeUserId")),text(body,"assigneeName"),jsonText(body.get("webhookConfigIds")),ownerType,ownerId,ownerDeptId,targetMode,claimMode,formMode,boolInt(body,"requirePhoto",0),intValue(body.get("advanceMinutes"),0),intValue(body.get("urgeMinutes"),30),boolInt(body,"enabled",1)};
        if(id==null){jdbc.update("INSERT INTO inspection_plan(name,inspection_type_id,template_id,category,frequency_type,frequency_value,schedule_config_json,start_date,end_date,execute_time,overdue_minutes,assignee_user_id,assignee_name,webhook_config_ids,owner_type,owner_id,owner_dept_id,target_mode,claim_mode,form_mode,require_photo,advance_minutes,urge_minutes,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",args);id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
        else {Object[] update=Arrays.copyOf(args,args.length+1);update[args.length]=id;jdbc.update("UPDATE inspection_plan SET name=?,inspection_type_id=?,template_id=?,category=?,frequency_type=?,frequency_value=?,schedule_config_json=?,start_date=?,end_date=?,execute_time=?,overdue_minutes=?,assignee_user_id=?,assignee_name=?,webhook_config_ids=?,owner_type=?,owner_id=?,owner_dept_id=?,target_mode=?,claim_mode=?,form_mode=?,require_photo=?,advance_minutes=?,urge_minutes=?,enabled=? WHERE id=?",update);jdbc.update("DELETE FROM inspection_plan_target WHERE plan_id=?",id);}
        if(!fanout){
            Object targets=body.get("targets");
            if(targets instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> m){
                jdbc.update("INSERT INTO inspection_plan_target(plan_id,target_type,target_id,target_code,target_name,location_text,enabled) VALUES(?,?,?,?,?,?,1)",id,defaultText(m,"targetType","PLATFORM_DEVICE"),text(m,"targetId"),text(m,"targetCode"),text(m,"targetName"),text(m,"locationText"));
            }
            if(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target WHERE plan_id=?",Integer.class,id)==0) bad("至少选择一个巡检对象");
            Integer invalid=jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan_target x JOIN inspection_equipment e ON e.id=CAST(x.target_id AS UNSIGNED) LEFT JOIN inspection_category_template ct ON ct.category_id=e.category_id AND ct.template_id=? AND ct.enabled=1 WHERE x.plan_id=? AND x.target_type='INSPECTION_EQUIPMENT' AND ct.id IS NULL",Integer.class,templateId,id);
            if(invalid!=null&&invalid>0)bad("所选设备分类尚未关联该巡检表单，请先在设备分类中配置关联表单");
        }
        generatePlan(id,LocalDate.now());
        Long savedId = id;
        return plans().stream().filter(p->Objects.equals(longValue(p.get("id")),savedId)).findFirst().orElseThrow();
    }

    @Scheduled(cron="0 */5 * * * *")
    public void scheduledGenerate(){ generateAll(); markOverdue(); sendReminders(); }

    public int generateAll(){
        LocalDate today=LocalDate.now(); int total=0;
        for(Map<String,Object> p:jdbc.queryForList("SELECT id,start_date,end_date FROM inspection_plan WHERE enabled=1 AND start_date<=CURRENT_DATE AND (end_date IS NULL OR end_date>=CURRENT_DATE)")){
            Long planId=longValue(p.get("id"));
            LocalDate from=today.minusDays(Math.max(backfillDays,0));
            LocalDate start=toDate(p.get("start_date")); if(start.isAfter(from)) from=start;
            LocalDate to=today; if(p.get("end_date")!=null){LocalDate e=toDate(p.get("end_date")); if(e.isBefore(to)) to=e;}
            for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)) total+=generatePlan(planId,d);
        }
        return total;
    }

    public Map<String,Object> calendarTasks(LocalDate from, LocalDate to, Long deptId, String category, String status, String keyword, boolean includeExternal, String username){
        LocalDate begin=from==null?LocalDate.now().withDayOfMonth(1):from;
        LocalDate end=to==null?begin.plusDays(30):to;
        if(end.isAfter(begin.plusDays(30))) end=begin.plusDays(30);
        if(end.isBefore(begin)) end=begin;
        com.wanyuecity.energy.security.DataScopeService.Scope scope=dataScope.resolve(username);
        StringBuilder where=new StringBuilder(" WHERE k.scheduled_at>=? AND k.scheduled_at<? AND k.status<>'CANCELLED'");
        List<Object> args=new ArrayList<>();args.add(Timestamp.valueOf(begin.atStartOfDay()));args.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        if(deptId!=null){where.append(" AND (k.owner_dept_id=? OR k.owner_ancestors LIKE ?)");args.add(deptId);args.add("%/"+deptId+"/%");}
        if(category!=null&&!category.isBlank()){where.append(" AND p.category=?");args.add(category.trim());}
        if(status!=null&&!status.isBlank()){where.append(" AND k.status=?");args.add(status.trim());}
        if(keyword!=null&&!keyword.isBlank()){where.append(" AND (k.task_no LIKE ? OR k.target_name LIKE ? OR p.name LIKE ? OR k.owner_name LIKE ?)");String kw="%"+keyword.trim()+"%";args.add(kw);args.add(kw);args.add(kw);args.add(kw);}
        where.append(taskScopeSql(scope,"k",args));
        List<Map<String,Object>> instances=jdbc.queryForList("SELECT k.id,k.task_no taskNo,k.target_name targetName,k.target_code targetCode,k.owner_type ownerType,k.owner_id ownerId,k.owner_name ownerName,k.owner_dept_id ownerDeptId,k.owner_dept_name ownerDeptName,k.assignee_name assigneeName,k.claimed_by claimedBy,k.status,k.scheduled_at scheduledAt,k.due_at dueAt,k.submitted_at submittedAt,k.source_type sourceType,p.name planName,p.category,p.form_mode formMode FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id"+where+" ORDER BY k.scheduled_at,k.id",args.toArray());
        List<Map<String,Object>> projections=new ArrayList<>();
        if(status==null||status.isBlank()||"PENDING".equals(status)){
            List<Map<String,Object>> plans=jdbc.queryForList("SELECT p.*,d.name owner_dept_name,COALESCE(CASE p.owner_type WHEN 'USER' THEN u.display_name WHEN 'ROLE' THEN r.name ELSE d.name END,p.assignee_name) owner_display_name FROM inspection_plan p LEFT JOIN sys_department d ON d.id=p.owner_dept_id LEFT JOIN sys_user u ON u.id=p.owner_id AND p.owner_type='USER' LEFT JOIN sys_role r ON r.id=p.owner_id AND p.owner_type='ROLE' WHERE p.enabled=1 AND p.start_date<=? AND (p.end_date IS NULL OR p.end_date>=?)",Date.valueOf(end),Date.valueOf(begin));
            Set<String> generated=new HashSet<>();
            for(Map<String,Object> x:jdbc.queryForList("SELECT plan_id,period_key FROM inspection_task WHERE scheduled_at>=? AND scheduled_at<?",Timestamp.valueOf(begin.atStartOfDay()),Timestamp.valueOf(end.plusDays(1).atStartOfDay()))) generated.add(String.valueOf(x.get("plan_id"))+":"+x.get("period_key"));
            for(Map<String,Object> p:plans){
                if(deptId!=null){Long od=longValue(p.get("owner_dept_id"));if(od==null||!od.equals(deptId))continue;}
                // 预计任务同样受数据范围约束
                if(!scope.covers(text(p,"owner_type"),longValue(p.get("owner_id")),longValue(p.get("owner_dept_id"))))continue;
                if(category!=null&&!category.isBlank()&&!category.trim().equals(String.valueOf(p.get("category"))))continue;
                if(keyword!=null&&!keyword.isBlank()){String kw=keyword.trim();if(!text(p,"name").contains(kw)&&!text(p,"owner_display_name").contains(kw))continue;}
                Long planId=longValue(p.get("id"));LocalDate start=toDate(p.get("start_date"));
                for(LocalDate d=(start.isAfter(begin)?start:begin);!d.isAfter(end);d=d.plusDays(1)){
                    if(!due(p,d))continue;String period=periodKey(p,d);if(generated.contains(planId+":"+period))continue;
                    LocalTime execute=((Time)p.get("execute_time")).toLocalTime();
                    Map<String,Object> item=new LinkedHashMap<>();item.put("planId",planId);item.put("planName",text(p,"name"));item.put("category",text(p,"category"));item.put("projected",true);item.put("scheduledAt",LocalDateTime.of(d,execute));item.put("ownerType",text(p,"owner_type"));item.put("ownerName",text(p,"owner_display_name"));item.put("ownerDeptName",text(p,"owner_dept_name"));item.put("formMode",text(p,"form_mode"));item.put("status","PENDING");
                    projections.add(item);
                }
            }
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("instances",instances);result.put("projections",projections);result.put("externals",includeExternal&&(category==null||category.isBlank())?externalCalendarTasks(begin,end,deptId,status,keyword,scope):List.of());result.put("from",begin);result.put("to",end);return result;
    }

    /** 只读聚合：把营运巡场任务映射为日历条目，处理入口仍回到原模块。 */
    public List<Map<String,Object>> externalCalendarTasks(LocalDate begin, LocalDate end, Long deptId, String status, String keyword, com.wanyuecity.energy.security.DataScopeService.Scope scope){
        StringBuilder sql=new StringBuilder("SELECT t.id,t.task_date taskDate,t.status,t.plan_start_at planStartAt,t.plan_end_at planEndAt,t.assignee_name assigneeName,t.submitter_name submitterName,t.late_minutes lateMinutes,t.pass_count passCount,t.fail_count failCount,t.is_handover handover,d.name deptName,s.name slotName FROM ops_patrol_task t JOIN ops_patrol_dept d ON d.id=t.dept_id JOIN ops_patrol_slot s ON s.id=t.slot_id WHERE t.task_date>=? AND t.task_date<=?");
        List<Object> args=new ArrayList<>();args.add(Date.valueOf(begin));args.add(Date.valueOf(end));
        if(deptId!=null){sql.append(" AND d.id IN (SELECT ops_dept_id FROM sys_department WHERE ops_dept_id IS NOT NULL AND (id=? OR ancestors LIKE ?))");args.add(deptId);args.add("%/"+deptId+"/%");}
        if(scope!=null&&!scope.all()&&!scope.deptIds().isEmpty()){
            sql.append(" AND d.id IN (SELECT ops_dept_id FROM sys_department WHERE ops_dept_id IS NOT NULL AND id IN (")
                    .append(String.join(",", Collections.nCopies(scope.deptIds().size(),"?"))).append("))");
            args.addAll(scope.deptIds());
        }
        sql.append(" ORDER BY t.plan_start_at,t.id");
        List<Map<String,Object>> out=new ArrayList<>();
        String kw=keyword==null||keyword.isBlank()?null:keyword.trim();
        for(Map<String,Object> row:jdbc.queryForList(sql.toString(),args.toArray())){
            String mapped=mapOpsPatrolStatus(text(row,"status"));
            if(status!=null&&!status.isBlank()&&!status.trim().equals(mapped)) continue;
            String deptName=text(row,"deptName"),slotName=text(row,"slotName"),assignee=text(row,"assigneeName"),submitter=text(row,"submitterName");
            String title=deptName+" · "+slotName;
            if(kw!=null&&!title.contains(kw)&&!assignee.contains(kw)&&!submitter.contains(kw)) continue;
            Long id=longValue(row.get("id"));
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("source","OPS_PATROL");
            item.put("id",id);
            item.put("title",title);
            item.put("deptName",deptName);
            item.put("ownerName",assignee.isEmpty()?submitter:assignee);
            item.put("status",mapped);
            item.put("rawStatus",text(row,"status"));
            item.put("scheduledAt",row.get("planStartAt"));
            item.put("dueAt",row.get("planEndAt"));
            item.put("lateMinutes",intValue(row.get("lateMinutes"),0));
            item.put("passCount",intValue(row.get("passCount"),0));
            item.put("failCount",intValue(row.get("failCount"),0));
            item.put("handover",intValue(row.get("handover"),0)==1);
            item.put("readonly",true);
            item.put("link","/ops-patrol/tasks?date="+row.get("taskDate")+"&taskId="+id);
            out.add(item);
        }
        return out;
    }

    private String mapOpsPatrolStatus(String status){
        return switch(status){
            case "DONE" -> "COMPLETED";
            case "EXEMPT" -> "CLOSED";
            default -> status;
        };
    }

    public int generate(Long planId, LocalDate day){
        return planId==null?generateAll():generatePlan(planId,day==null?LocalDate.now():day);
    }

    public List<Map<String,Object>> generationLogs(Long planId, LocalDate from, LocalDate to){
        LocalDate end=to==null?LocalDate.now():to; LocalDate begin=from==null?end.minusDays(6):from;
        return jdbc.queryForList("SELECT g.id,g.plan_id planId,p.name planName,g.occur_date occurDate,g.period_key periodKey,g.task_count taskCount,g.result,g.message,g.created_at createdAt FROM calendar_generation_log g LEFT JOIN inspection_plan p ON p.id=g.plan_id WHERE (? IS NULL OR g.plan_id=?) AND g.occur_date BETWEEN ? AND ? ORDER BY g.id DESC LIMIT 200",planId,planId,Date.valueOf(begin),Date.valueOf(end));
    }

    /**
     * 任务数据范围 SQL：管理员不限；领导看本部门及子部门、本人任务；
     * 普通人员看本人、本岗位与被指派的部门任务。
     */
    private String taskScopeSql(com.wanyuecity.energy.security.DataScopeService.Scope s,String alias,List<Object> args){
        if(s.all()) return "";
        StringBuilder w=new StringBuilder(" AND (").append(alias).append(".assignee_user_id=?").append(" OR ").append(alias).append(".claimed_by=?");
        args.add(s.userId());args.add(s.username());
        if(!s.roleIds().isEmpty()){
            w.append(" OR (").append(alias).append(".owner_type='ROLE' AND ").append(alias).append(".owner_id IN (")
                    .append(String.join(",",Collections.nCopies(s.roleIds().size(),"?"))).append("))");
            args.addAll(s.roleIds());
        }
        if(!s.deptIds().isEmpty()){
            w.append(" OR ").append(alias).append(".owner_dept_id IN (")
                    .append(String.join(",",Collections.nCopies(s.deptIds().size(),"?"))).append(")");
            args.addAll(s.deptIds());
        }
        return w.append(")").toString();
    }

    /** 日历计划列表：只返回 CALENDAR 类型，并计算下次生成时间。 */
    public List<Map<String,Object>> calendarPlans(String keyword,String status,String category,String username){
        StringBuilder sql=new StringBuilder("SELECT p.id,p.name,p.category,p.frequency_type frequencyType,p.frequency_value frequencyValue,p.start_date startDate,p.end_date endDate,p.execute_time executeTime,p.overdue_minutes overdueMinutes,p.owner_type ownerType,p.owner_id ownerId,p.owner_dept_id ownerDeptId,p.target_mode targetMode,p.claim_mode claimMode,p.form_mode formMode,p.require_photo requirePhoto,p.advance_minutes advanceMinutes,p.urge_minutes urgeMinutes,p.webhook_config_ids webhookConfigIds,p.enabled,p.last_generated_at lastGeneratedAt,p.updated_at updatedAt,COALESCE(CASE p.owner_type WHEN 'USER' THEN u.display_name WHEN 'ROLE' THEN r.name ELSE d.name END,p.assignee_name) ownerName,COALESCE(CASE p.owner_type WHEN 'DEPT' THEN d.name WHEN 'USER' THEN ud.name ELSE rd.name END,'-') ownerDeptName FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id LEFT JOIN sys_user u ON u.id=p.owner_id AND p.owner_type='USER' LEFT JOIN sys_role r ON r.id=p.owner_id AND p.owner_type='ROLE' LEFT JOIN sys_department d ON d.id=p.owner_id AND p.owner_type='DEPT' LEFT JOIN sys_department ud ON ud.id=u.department_id LEFT JOIN sys_department rd ON rd.id=r.dept_id WHERE y.code='CALENDAR'");
        List<Object> args=new ArrayList<>();
        if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (p.name LIKE ? OR COALESCE(CASE p.owner_type WHEN 'USER' THEN u.display_name WHEN 'ROLE' THEN r.name ELSE d.name END,'') LIKE ?)");String kw="%"+keyword.trim()+"%";args.add(kw);args.add(kw);}
        if(status!=null&&!status.isBlank()){sql.append(" AND p.enabled=?");args.add(("1".equals(status.trim())||"ENABLED".equalsIgnoreCase(status.trim()))?1:0);}
        if(category!=null&&!category.isBlank()){sql.append(" AND p.category=?");args.add(category.trim());}
        sql.append(" ORDER BY p.enabled DESC,p.id DESC LIMIT 500");
        com.wanyuecity.energy.security.DataScopeService.Scope scope=dataScope.resolve(username);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Map<String,Object> row:jdbc.queryForList(sql.toString(),args.toArray())){
            if(!scope.covers(text(row,"ownerType"),longValue(row.get("ownerId")),longValue(row.get("ownerDeptId")))) continue;
            Map<String,Object> item=new LinkedHashMap<>(row);
            item.put("nextRunAt",nextRunAt(item));
            rows.add(item);
        }
        return rows;
    }

    public Map<String,Object> calendarPlan(Long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.id,p.name,p.category,p.frequency_type frequencyType,p.frequency_value frequencyValue,p.start_date startDate,p.end_date endDate,p.execute_time executeTime,p.overdue_minutes overdueMinutes,p.owner_type ownerType,p.owner_id ownerId,p.owner_dept_id ownerDeptId,p.target_mode targetMode,p.claim_mode claimMode,p.form_mode formMode,p.require_photo requirePhoto,p.advance_minutes advanceMinutes,p.urge_minutes urgeMinutes,p.webhook_config_ids webhookConfigIds,p.enabled,p.last_generated_at lastGeneratedAt,p.updated_at updatedAt FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id WHERE p.id=? AND y.code='CALENDAR'",id);
        if(rows.isEmpty()) bad("日历计划不存在");
        Map<String,Object> item=new LinkedHashMap<>(rows.get(0));
        item.put("nextRunAt",nextRunAt(item));
        item.put("logs",generationLogs(id,LocalDate.now().minusDays(6),LocalDate.now()));
        return item;
    }

    /** 计划详情：按当前用户数据范围校验后可读。 */
    public Map<String,Object> calendarPlan(Long id,String username){
        assertPlanVisible(id,username);
        return calendarPlan(id);
    }

    private void assertPlanVisible(Long id,String username){
        com.wanyuecity.energy.security.DataScopeService.Scope scope=dataScope.resolve(username);
        if(scope.all()) return;
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT owner_type ownerType,owner_id ownerId,owner_dept_id ownerDeptId FROM inspection_plan WHERE id=?",id);
        if(rows.isEmpty()) bad("日历计划不存在");
        Map<String,Object> row=rows.get(0);
        if(!scope.covers(text(row,"ownerType"),longValue(row.get("ownerId")),longValue(row.get("ownerDeptId"))))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权访问该计划");
    }

    @Transactional
    public Map<String,Object> saveCalendarPlan(Long id,Map<String,Object> body,String username){
        String name=text(body,"name");
        if(name.isBlank()) bad("计划名称不能为空");
        if(name.length()>100) bad("计划名称不能超过100个字符");
        LocalDate start=parseDate(body.get("startDate")); if(start==null) bad("请选择开始日期");
        LocalDate endDate=parseDate(body.get("endDate")); if(endDate!=null&&endDate.isBefore(start)) bad("结束日期不能早于开始日期");
        LocalTime execute=parseTime(body.get("executeTime")); if(execute==null) bad("请选择执行时间");
        String category=defaultText(body,"category","OTHER");
        if(!Set.of("DAILY","SAFETY","CLEANING","OPERATION","MEETING","OTHER").contains(category)) bad("任务分类无效");
        String frequency=defaultText(body,"frequencyType","DAILY");
        if(!Set.of("DAILY","WEEKLY","MONTHLY","INTERVAL_DAYS").contains(frequency)) bad("执行频率无效");
        int frequencyValue=Math.max(1,intValue(body.get("frequencyValue"),1));
        String ownerType=defaultText(body,"ownerType","DEPT");
        if(!Set.of("USER","ROLE","DEPT").contains(ownerType)) bad("归属类型无效");
        Long ownerId=longValue(body.get("ownerId")); if(ownerId==null) bad("请选择责任对象");
        String targetMode="DEPT".equals(ownerType)?defaultText(body,"targetMode","UNIT"):"UNIT";
        if(!Set.of("UNIT","PER_CHILD").contains(targetMode)) bad("下发方式无效");
        String claimMode=text(body,"claimMode");
        if(claimMode.isBlank()) claimMode="USER".equals(ownerType)?"NONE":"CLAIM";
        if(!Set.of("NONE","CLAIM","AUTO").contains(claimMode)) bad("认领方式无效");
        String formMode=defaultText(body,"formMode","NONE");
        if(!Set.of("CHECKLIST","NONE","INSPECTION").contains(formMode)) bad("回执模式无效");
        Long ownerDeptId=longValue(body.get("ownerDeptId"));
        if(ownerDeptId==null){
            if("DEPT".equals(ownerType)) ownerDeptId=ownerId;
            else if("ROLE".equals(ownerType)){List<Long> l=jdbc.queryForList("SELECT dept_id FROM sys_role WHERE id=?",Long.class,ownerId);ownerDeptId=l.isEmpty()?null:l.get(0);}
            else {List<Long> l=jdbc.queryForList("SELECT department_id FROM sys_user WHERE id=?",Long.class,ownerId);ownerDeptId=l.isEmpty()?null:l.get(0);}
        }
        // 提前校验责任对象，避免生成阶段才发现归属无效
        resolveOwner(ownerType,ownerId,text(body,"assigneeName"),claimMode);
        // 领导只能给本部门及子部门创建任务；管理员不限
        com.wanyuecity.energy.security.DataScopeService.Scope scope=dataScope.resolve(username);
        if(!scope.all()&&!scope.covers(ownerType,ownerId,ownerDeptId))
            bad("只能为本部门及子部门的人员或岗位创建任务");
        if(id!=null){
            List<Map<String,Object>> old=jdbc.queryForList("SELECT owner_type ownerType,owner_id ownerId,owner_dept_id ownerDeptId FROM inspection_plan WHERE id=?",id);
            if(!old.isEmpty()){
                Map<String,Object> o=old.get(0);
                if(!scope.covers(text(o,"ownerType"),longValue(o.get("ownerId")),longValue(o.get("ownerDeptId"))))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权修改该计划");
            }
        }
        Long typeId=calendarTypeId();
        Long templateId=calendarTemplateId(typeId);
        int enabled=boolInt(body,"enabled",1);
        String webhook;
        if(body.containsKey("webhookConfigIds")) webhook=jsonText(body.get("webhookConfigIds"));
        else if(id!=null) webhook=String.valueOf(one("SELECT webhook_config_ids v FROM inspection_plan WHERE id=?",id).getOrDefault("v","[]"));
        else webhook="[]";
        Object[] args={name,typeId,templateId,category,frequency,frequencyValue,"{}",Date.valueOf(start),sqlDate(body.get("endDate")),Time.valueOf(execute),intValue(body.get("overdueMinutes"),1440),longValue(body.get("assigneeUserId")),text(body,"assigneeName"),webhook,ownerType,ownerId,ownerDeptId,targetMode,claimMode,formMode,boolInt(body,"requirePhoto",0),intValue(body.get("advanceMinutes"),0),intValue(body.get("urgeMinutes"),30),enabled};
        if(id==null){
            jdbc.update("INSERT INTO inspection_plan(name,inspection_type_id,template_id,category,frequency_type,frequency_value,schedule_config_json,start_date,end_date,execute_time,overdue_minutes,assignee_user_id,assignee_name,webhook_config_ids,owner_type,owner_id,owner_dept_id,target_mode,claim_mode,form_mode,require_photo,advance_minutes,urge_minutes,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",args);
            id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        }else{
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id WHERE p.id=? AND y.code='CALENDAR'",Integer.class,id);
            if(exists==null||exists==0) bad("日历计划不存在");
            Object[] update=Arrays.copyOf(args,args.length+1);update[args.length]=id;
            jdbc.update("UPDATE inspection_plan SET name=?,inspection_type_id=?,template_id=?,category=?,frequency_type=?,frequency_value=?,schedule_config_json=?,start_date=?,end_date=?,execute_time=?,overdue_minutes=?,assignee_user_id=?,assignee_name=?,webhook_config_ids=?,owner_type=?,owner_id=?,owner_dept_id=?,target_mode=?,claim_mode=?,form_mode=?,require_photo=?,advance_minutes=?,urge_minutes=?,enabled=? WHERE id=?",update);
            jdbc.update("DELETE FROM inspection_plan_target WHERE plan_id=?",id);
        }
        if(!("DEPT".equals(ownerType)&&"PER_CHILD".equals(targetMode))) {
            // 日历计划按计划整体下发，不使用设备对象；保留一条占位目标供生成器复用
            jdbc.update("INSERT INTO inspection_plan_target(plan_id,target_type,target_id,target_code,target_name,location_text,enabled) VALUES(?,?,?,?,?,?,1)",id,"CALENDAR",String.valueOf(ownerId),"",name,"");
        }
        if(enabled==1) generatePlan(id,LocalDate.now());
        return calendarPlan(id);
    }

    @Transactional
    public Map<String,Object> toggleCalendarPlan(Long id,boolean enabled,String username){
        assertPlanVisible(id,username);
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id WHERE p.id=? AND y.code='CALENDAR'",Integer.class,id);
        if(exists==null||exists==0) bad("日历计划不存在");
        jdbc.update("UPDATE inspection_plan SET enabled=?,updated_at=NOW(3) WHERE id=?",enabled?1:0,id);
        if(enabled) generatePlan(id,LocalDate.now());
        return calendarPlan(id);
    }

    private Long calendarTypeId(){
        List<Long> ids=jdbc.queryForList("SELECT id FROM inspection_type WHERE code='CALENDAR' ORDER BY id LIMIT 1",Long.class);
        if(ids.isEmpty()) bad("日历任务类型未初始化，请先执行数据库迁移");
        return ids.get(0);
    }

    private Long calendarTemplateId(Long typeId){
        List<Long> ids=jdbc.queryForList("SELECT id FROM inspection_template WHERE inspection_type_id=? AND enabled=1 ORDER BY id LIMIT 1",Long.class,typeId);
        if(!ids.isEmpty()) return ids.get(0);
        jdbc.update("INSERT INTO inspection_template(inspection_type_id,name,version_no,fields_json,enabled) VALUES(?,?,1,'[]',1)",typeId,"日历任务默认回执");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }

    /** 计算下一次计划生成时间，最多向后看 400 天。 */
    private LocalDateTime nextRunAt(Map<String,Object> row){
        LocalDate start=toDate(row.get("startDate")); if(start==null) return null;
        LocalDate end=toDate(row.get("endDate"));
        Object time=row.get("executeTime");
        LocalTime execute=time instanceof Time t?t.toLocalTime():parseTime(time);
        if(execute==null) return null;
        Map<String,Object> probe=new LinkedHashMap<>();
        probe.put("frequency_type",row.get("frequencyType"));
        probe.put("frequency_value",row.get("frequencyValue"));
        probe.put("start_date",row.get("startDate"));
        LocalDate today=LocalDate.now(); LocalDate d=start.isAfter(today)?start:today; LocalDateTime now=LocalDateTime.now();
        for(int i=0;i<400&&(end==null||!d.isAfter(end));i++,d=d.plusDays(1)){
            if(!due(probe,d)) continue;
            LocalDateTime at=LocalDateTime.of(d,execute);
            if(!at.isBefore(now)) return at;
        }
        return null;
    }

    /** 任务操作日志：写入失败不阻断主流程。 */
    public void logTask(Long taskId,String action,String actorType,String actorName,String detail,String ip){
        try{
            jdbc.update("INSERT INTO inspection_task_log(task_id,action,actor_type,actor_name,detail,ip) VALUES(?,?,?,?,?,?)",taskId,trunc(action,32),trunc(actorType,16),trunc(actorName,64),trunc(detail,500),trunc(ip,64));
        }catch(Exception ignored){}
    }

    public List<Map<String,Object>> taskLogs(Long taskId){
        return jdbc.queryForList("SELECT id,task_id taskId,action,actor_type actorType,actor_name actorName,detail,ip,created_at createdAt FROM inspection_task_log WHERE task_id=? ORDER BY id DESC LIMIT 100",taskId);
    }

    @Transactional
    public int generatePlan(Long planId, LocalDate day){
        Map<String,Object> p=one("SELECT p.*,y.code typeCode FROM inspection_plan p JOIN inspection_type y ON y.id=p.inspection_type_id WHERE p.id=?",planId);
        if(intValue(p.get("enabled"),0)!=1||day.isBefore(toDate(p.get("start_date")))||(p.get("end_date")!=null&&day.isAfter(toDate(p.get("end_date"))))) return 0;
        if(!due(p,day)) return 0;
        boolean calendar="CALENDAR".equals(String.valueOf(p.get("typeCode")));
        LocalTime execute=((Time)p.get("execute_time")).toLocalTime(); LocalDateTime scheduled=LocalDateTime.of(day,execute); String period=periodKey(p,day);
        LocalDateTime dueAt=scheduled.plusMinutes(intValue(p.get("overdue_minutes"),1440));
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM inspection_task WHERE plan_id=? AND period_key=?",Integer.class,planId,period);
        if(exists!=null&&exists>0){ logGeneration(planId,day,period,0,"SKIPPED","任务已存在，跳过生成"); return 0; }
        String ownerType=defaultText(p,"owner_type","USER"); Long ownerId=longValue(p.get("owner_id"));
        String targetMode=defaultText(p,"target_mode","UNIT"); String claimMode=defaultText(p,"claim_mode","NONE");
        int count=0;
        try{
            if("DEPT".equals(ownerType)&&"PER_CHILD".equals(targetMode)){
                List<Map<String,Object>> children=jdbc.queryForList("SELECT id,code,name FROM sys_department WHERE parent_id=? AND deleted_at IS NULL AND status='ACTIVE' ORDER BY sort_order,id",ownerId);
                if(children.isEmpty()) children=jdbc.queryForList("SELECT id,code,name FROM sys_department WHERE id=? AND deleted_at IS NULL",ownerId);
                for(Map<String,Object> c:children) count+=insertTask(p,period,scheduled,dueAt,"DEPT",String.valueOf(c.get("id")),String.valueOf(c.get("code")),String.valueOf(c.get("name")),null,deptOwner(longValue(c.get("id")),claimMode),calendar);
            } else {
                Owner o=resolveOwner(ownerType,ownerId,text(p,"assignee_name"),claimMode);
                for(Map<String,Object> t:jdbc.queryForList("SELECT * FROM inspection_plan_target WHERE plan_id=? AND enabled=1",planId))
                    count+=insertTask(p,period,scheduled,dueAt,String.valueOf(t.get("target_type")),String.valueOf(t.get("target_id")),text(t,"target_code"),text(t,"target_name"),text(t,"location_text"),o,calendar);
            }
        }catch(RuntimeException e){
            logGeneration(planId,day,period,0,"ERROR",limit(e.getMessage()));
            throw e;
        }
        jdbc.update("UPDATE inspection_plan SET last_generated_at=NOW(3) WHERE id=?",planId);
        logGeneration(planId,day,period,count,count>0?"SUCCESS":"SKIPPED",count>0?null:"无有效下发对象");
        return count;
    }

    private void logGeneration(Long planId,LocalDate day,String period,int count,String result,String message){
        jdbc.update("INSERT INTO calendar_generation_log(plan_id,occur_date,period_key,task_count,result,message) VALUES(?,?,?,?,?,?) AS new ON DUPLICATE KEY UPDATE task_count=new.task_count,result=new.result,message=new.message,updated_at=NOW(3)",planId,Date.valueOf(day),period,count,result,limit(message));
    }

    private static String limit(String s){if(s==null)return null;return s.length()>500?s.substring(0,500):s;}
    private static String trunc(String s,int max){if(s==null)return null;return s.length()>max?s.substring(0,max):s;}

    private int insertTask(Map<String,Object> p,String period,LocalDateTime scheduled,LocalDateTime dueAt,String targetType,String targetId,String targetCode,String targetName,String locationText,Owner o,boolean calendar){
        try{
            String no="XJ"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+String.format("%04d",Math.abs(UUID.randomUUID().hashCode())%10000);
            jdbc.update("INSERT INTO inspection_task(task_no,plan_id,inspection_type_id,template_id,target_type,target_id,target_code,target_name,location_text,equipment_snapshot_json,period_key,scheduled_at,due_at,assignee_user_id,assignee_name,access_token,access_token_expire_at,status,source_type,owner_type,owner_id,owner_name,owner_dept_id,owner_dept_name,owner_ancestors,claimed_by,claimed_at,assignee_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?,?,?,?,?)",
                    no,longValue(p.get("id")),
                    p.get("inspection_type_id"),p.get("template_id"),targetType,targetId,targetCode,targetName,locationText,equipmentSnapshot(targetType,targetId),period,Timestamp.valueOf(scheduled),Timestamp.valueOf(dueAt),
                    o.assigneeId(),o.assigneeName(),UUID.randomUUID().toString().replace("-",""),Timestamp.valueOf(dueAt.plusDays(7)),
                    calendar?"CALENDAR":"PLAN",o.type(),o.id(),o.name(),o.deptId(),o.deptName(),o.ancestors(),o.claimedBy(),o.claimedBy()==null?null:Timestamp.valueOf(LocalDateTime.now()),o.assigneeStatus());
            return 1;
        }catch(DuplicateKeyException ignored){return 0;}
    }

    public List<Map<String,Object>> tasks(String status,String keyword,String username){
        StringBuilder sql=new StringBuilder("SELECT k.id,k.task_no taskNo,k.plan_id planId,p.name planName,y.name inspectionTypeName,k.target_type targetType,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.status,k.source_type sourceType,k.owner_type ownerType,k.owner_id ownerId,k.owner_name ownerName,k.owner_dept_id ownerDeptId,k.owner_dept_name ownerDeptName,k.claimed_by claimedBy,(SELECT COALESCE(NULLIF(u2.display_name,''),u2.username) FROM sys_user u2 WHERE u2.username=k.claimed_by LIMIT 1) claimedByName,k.claimed_at claimedAt,k.assignee_status assigneeStatus,k.started_at startedAt,k.submitted_at submittedAt,k.result_summary resultSummary,k.completed_by completedBy,p.claim_mode planClaimMode FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id WHERE 1=1");
        List<Object> args=new ArrayList<>(); if(status!=null&&!status.isBlank()){sql.append(" AND k.status=?");args.add(status);} if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (k.task_no LIKE ? OR k.target_name LIKE ? OR k.target_code LIKE ?)");String x="%"+keyword+"%";args.add(x);args.add(x);args.add(x);}
        sql.append(taskScopeSql(dataScope.resolve(username),"k",args));
        sql.append(" ORDER BY k.scheduled_at DESC,k.id DESC LIMIT 500");return jdbc.queryForList(sql.toString(),args.toArray());
    }

    public Map<String,Object> task(Long id){
        Map<String,Object> r=one("SELECT k.*,k.task_no taskNo,k.plan_id planId,k.target_type targetType,k.target_id targetId,k.target_code targetCode,k.target_name targetName,k.location_text locationText,k.equipment_snapshot_json equipmentSnapshotJson,k.scheduled_at scheduledAt,k.due_at dueAt,k.assignee_user_id assigneeUserId,k.assignee_name assigneeName,k.access_token accessToken,k.status,k.source_type sourceType,k.owner_type ownerType,k.owner_id ownerId,k.owner_name ownerName,k.owner_dept_id ownerDeptId,k.owner_dept_name ownerDeptName,k.owner_ancestors ownerAncestors,k.claimed_by claimedBy,(SELECT COALESCE(NULLIF(u2.display_name,''),u2.username) FROM sys_user u2 WHERE u2.username=k.claimed_by LIMIT 1) claimedByName,k.claimed_at claimedAt,k.assignee_status assigneeStatus,k.started_at startedAt,k.submitted_at submittedAt,k.result_json resultJson,k.result_summary resultSummary,k.completed_by completedBy,p.name planName,p.category,p.form_mode formMode,p.require_photo requirePhoto,p.advance_minutes advanceMinutes,p.urge_minutes urgeMinutes,p.claim_mode planClaimMode,y.name inspectionTypeName,t.name templateName,t.fields_json fieldsJson,(SELECT eq.qr_token FROM inspection_equipment eq WHERE eq.id=k.target_id AND k.target_type='INSPECTION_EQUIPMENT') equipmentQrToken FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id JOIN inspection_type y ON y.id=k.inspection_type_id JOIN inspection_template t ON t.id=k.template_id WHERE k.id=?",id);
        r.put("issues",issues(id,null));r.put("logs",taskLogs(id));return r;
    }
    public Map<String,Object> taskByToken(String token){Map<String,Object> row=one("SELECT id FROM inspection_task WHERE access_token=?",token);return task(longValue(row.get("id")));}
    public byte[] qr(Long id){Map<String,Object> t=task(id);try{String token=String.valueOf(t.get("accessToken"));String source=String.valueOf(t.get("sourceType"));String path="CALENDAR".equals(source)||"MANUAL".equals(source)?"/calendar/receipt/"+token:"/inspection/tasks?taskToken="+token;String url=publicBaseUrl.replaceAll("/$","")+path;BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,420,420);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}catch(Exception e){bad("巡检二维码生成失败");return new byte[0];}}
    public byte[] qrByToken(String token){Map<String,Object> row=one("SELECT id FROM inspection_task WHERE access_token=?",token);return qr(longValue(row.get("id")));}

    @Transactional
    public Map<String,Object> submit(Long id,Map<String,Object> body,String username){
        Map<String,Object> task=task(id);String status=String.valueOf(task.get("status"));if(Set.of("COMPLETED","CLOSED","CANCELLED").contains(status)) bad("该任务已结束，不能重复提交");
        String claimedBy=task.get("claimedBy")==null?null:String.valueOf(task.get("claimedBy"));
        if(claimedBy!=null&&!claimedBy.equals(username)) bad("该任务已由 "+claimedBy+" 认领，不能重复回执");
        if(intValue(task.get("requirePhoto"),0)==1){Integer photos=jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE business_id=? AND business_type IN ('CALENDAR_TASK','INSPECTION_TASK')",Integer.class,id);if(photos==null||photos==0) bad("该任务要求至少上传1张现场照片");}
        Map<String,Object> u=userRow(username); Long uid=u==null?null:longValue(u.get("id")); String display=u==null?username:String.valueOf(u.getOrDefault("name",username));
        int lateMinutes=0;Object dueObj=task.get("dueAt");if(dueObj instanceof java.sql.Timestamp ts) lateMinutes=(int)Math.max(0,java.time.Duration.between(ts.toLocalDateTime(),LocalDateTime.now()).toMinutes());
        String result=jsonText(body.get("result"));Object issues=body.get("issues");int issueCount=issues instanceof List<?> l?l.size():0;
        jdbc.update("UPDATE inspection_task SET status=?,started_at=COALESCE(started_at,NOW(3)),submitted_at=NOW(3),result_json=?,result_summary=?,completed_by=?,claimed_by=COALESCE(claimed_by,?),claimed_at=COALESCE(claimed_at,NOW(3)),assignee_user_id=COALESCE(assignee_user_id,?),assignee_name=COALESCE(NULLIF(assignee_name,''),?),assignee_status='ASSIGNED',submitter_name=?,submit_source='WEB',late_minutes=? WHERE id=?",issueCount>0?"PENDING_RECTIFICATION":"COMPLETED",result,text(body,"summary"),username,username,uid,display,display,lateMinutes,id);
        jdbc.update("DELETE FROM inspection_issue WHERE task_id=? AND status='PENDING'",id);
        if(issues instanceof List<?> list) for(Object item:list) if(item instanceof Map<?,?> m) jdbc.update("INSERT INTO inspection_issue(task_id,field_key,field_label,level,description,rectification_requirement,responsible_user_id,responsible_name,due_at,status) VALUES(?,?,?,?,?,?,?,?,?,'PENDING')",id,text(m,"fieldKey"),text(m,"fieldLabel"),defaultText(m,"level","WARNING"),text(m,"description"),text(m,"rectificationRequirement"),longValue(m.get("responsibleUserId")),text(m,"responsibleName"),sqlTimestamp(m.get("dueAt")));
        logTask(id,"SUBMIT","USER",display,issueCount>0?"提交回执并登记 "+issueCount+" 个问题":"提交回执",null);
        return task(id);
    }

    @Transactional
    public Map<String,Object> claim(Long id,String username){
        Map<String,Object> t=one("SELECT k.id,k.status,k.owner_type ownerType,k.owner_id ownerId,k.claimed_by claimedBy,k.owner_ancestors ownerAncestors,p.claim_mode claimMode FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.id=?",id);
        if(Set.of("COMPLETED","CLOSED","CANCELLED").contains(String.valueOf(t.get("status")))) bad("任务已结束，无需认领");
        String ownerType=String.valueOf(t.get("ownerType"));
        if(!Set.of("DEPT","ROLE").contains(ownerType)) bad("该任务不支持认领");
        if(!"CLAIM".equals(String.valueOf(t.get("claimMode")))) bad("该任务未开启认领");
        if(t.get("claimedBy")!=null){ if(username.equals(String.valueOf(t.get("claimedBy")))) return task(id); bad("该任务已被 "+t.get("claimedBy")+" 认领"); }
        Map<String,Object> u=userRow(username); if(u==null) bad("当前用户不存在");
        Long uid=longValue(u.get("id"));
        if("ROLE".equals(ownerType)){
            Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_id=?",Integer.class,uid,longValue(t.get("ownerId")));
            if(n==null||n==0) bad("当前用户不属于该岗位，不能认领");
        }else{
            String path=deptFullPath(longValue(u.get("department_id")));
            String ownerPath=t.get("ownerAncestors")==null?null:String.valueOf(t.get("ownerAncestors"));
            if(ownerPath==null||!path.startsWith(ownerPath)) bad("当前用户不属于该部门，不能认领");
        }
        String display=String.valueOf(u.getOrDefault("name",username));
        int rows=jdbc.update("UPDATE inspection_task SET claimed_by=?,claimed_at=NOW(3),assignee_user_id=?,assignee_name=?,assignee_status='ASSIGNED' WHERE id=? AND claimed_by IS NULL",username,uid,display,id);
        if(rows==0){
            Map<String,Object> again=one("SELECT claimed_by claimedBy FROM inspection_task WHERE id=?",id);
            if(username.equals(String.valueOf(again.get("claimedBy")))) return task(id);
            bad("任务已被他人认领");
        }
        logTask(id,"CLAIM","USER",display,"认领任务",null);
        return task(id);
    }

    @Transactional
    public Map<String,Object> reassign(Long id,Map<String,Object> body,String actor){
        Map<String,Object> t=one("SELECT id,status FROM inspection_task WHERE id=?",id);
        if(Set.of("COMPLETED","CLOSED","CANCELLED").contains(String.valueOf(t.get("status")))) bad("已完成或已取消的任务不允许重新归属");
        String type=defaultText(body,"ownerType","DEPT"); Long oid=longValue(body.get("ownerId"));
        if(!Set.of("USER","ROLE","DEPT").contains(type)) bad("归属类型无效");
        if(oid==null) bad("请选择新的责任对象");
        Owner o=resolveOwner(type,oid,null,"CLAIM");
        jdbc.update("UPDATE inspection_task SET owner_type=?,owner_id=?,owner_name=?,owner_dept_id=?,owner_dept_name=?,owner_ancestors=?,claimed_by=NULL,claimed_at=NULL,assignee_user_id=?,assignee_name=?,assignee_status=? WHERE id=?",o.type(),o.id(),o.name(),o.deptId(),o.deptName(),o.ancestors(),o.assigneeId(),o.assigneeName(),o.assigneeStatus(),id);
        Map<String,Object> actorRow=userRow(actor);
        String actorName=actorRow==null?actor:String.valueOf(actorRow.getOrDefault("name",actor));
        logTask(id,"REASSIGN","USER",actorName,"责任归属调整为 "+("USER".equals(o.type())?"个人":("ROLE".equals(o.type())?"岗位":"部门"))+"："+o.name(),null);
        return task(id);
    }

    public Map<String,Object> ownerOptions(){
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("departments",jdbc.queryForList("SELECT id,code,name,parent_id parentId,ancestors,depth,dept_level deptLevel,status FROM sys_department WHERE deleted_at IS NULL ORDER BY sort_order,id"));
        r.put("roles",jdbc.queryForList("SELECT id,code,name,dept_id deptId FROM sys_role ORDER BY dept_id IS NULL DESC,dept_id,id"));
        r.put("users",jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id deptId FROM sys_user WHERE status=1 ORDER BY department_id,id"));
        return r;
    }

    /** 日历计划责任对象选项：按当前用户数据范围收敛，领导只能选本部门及子部门。 */
    public Map<String,Object> ownerOptions(String username){
        com.wanyuecity.energy.security.DataScopeService.Scope s=dataScope.resolve(username);
        if(s.all()) return ownerOptions();
        Map<String,Object> r=new LinkedHashMap<>();
        if(s.deptIds().isEmpty()){
            r.put("departments",List.of());r.put("roles",List.of());r.put("users",List.of());
            return r;
        }
        String in=String.join(",",Collections.nCopies(s.deptIds().size(),"?"));
        Object[] ids=s.deptIds().toArray();
        r.put("departments",jdbc.queryForList("SELECT id,code,name,parent_id parentId,ancestors,depth,dept_level deptLevel,status FROM sys_department WHERE deleted_at IS NULL AND id IN ("+in+") ORDER BY sort_order,id",ids));
        r.put("roles",jdbc.queryForList("SELECT id,code,name,dept_id deptId FROM sys_role WHERE dept_id IN ("+in+") ORDER BY dept_id,id",ids));
        r.put("users",jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id deptId FROM sys_user WHERE status=1 AND department_id IN ("+in+") ORDER BY department_id,id",ids));
        return r;
    }

    public List<Map<String,Object>> issues(Long taskId,String status){String sql="SELECT i.id,i.task_id taskId,k.task_no taskNo,k.target_name targetName,i.field_key fieldKey,i.field_label fieldLabel,i.level,i.description,i.rectification_requirement rectificationRequirement,i.responsible_user_id responsibleUserId,i.responsible_name responsibleName,i.due_at dueAt,i.status,i.rectification_note rectificationNote,i.rectified_by rectifiedBy,i.rectified_at rectifiedAt,i.reviewed_by reviewedBy,i.reviewed_at reviewedAt,i.created_at createdAt FROM inspection_issue i JOIN inspection_task k ON k.id=i.task_id WHERE (? IS NULL OR i.task_id=?) AND (? IS NULL OR i.status=?) ORDER BY i.id DESC";return jdbc.queryForList(sql,taskId,taskId,status,status);}

    @Transactional public Map<String,Object> rectify(Long id,String note,String user){if(note==null||note.isBlank())bad("请填写整改说明");jdbc.update("UPDATE inspection_issue SET status='RECTIFIED',rectification_note=?,rectified_by=?,rectified_at=NOW(3) WHERE id=?",note,user,id);return one("SELECT * FROM inspection_issue WHERE id=?",id);}
    @Transactional public Map<String,Object> review(Long id,boolean passed,String user){Map<String,Object> issue=one("SELECT * FROM inspection_issue WHERE id=?",id);jdbc.update("UPDATE inspection_issue SET status=?,reviewed_by=?,reviewed_at=NOW(3) WHERE id=?",passed?"CLOSED":"PENDING",user,id);Long taskId=longValue(issue.get("task_id"));if(passed&&jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE task_id=? AND status<>'CLOSED'",Integer.class,taskId)==0)jdbc.update("UPDATE inspection_task SET status='CLOSED' WHERE id=?",taskId);else if(!passed)jdbc.update("UPDATE inspection_task SET status='PENDING_RECTIFICATION' WHERE id=?",taskId);return one("SELECT * FROM inspection_issue WHERE id=?",id);}

    public Map<String,Object> dashboard(){
        Map<String,Object> r=new LinkedHashMap<>();r.put("summary",one("SELECT COUNT(*) total,SUM(status='PENDING') pending,SUM(status='IN_PROGRESS') inProgress,SUM(status IN ('COMPLETED','CLOSED')) completed,SUM(status='OVERDUE') overdue,SUM(status='PENDING_RECTIFICATION') rectification FROM inspection_task WHERE scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01')"));r.put("typeStats",jdbc.queryForList("SELECT y.name typeName,COUNT(*) total,SUM(k.status IN ('COMPLETED','CLOSED')) completed,SUM(k.status='OVERDUE') overdue FROM inspection_task k JOIN inspection_type y ON y.id=k.inspection_type_id WHERE k.scheduled_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') GROUP BY y.id,y.name ORDER BY total DESC"));r.put("recent",jdbc.queryForList("SELECT DATE(scheduled_at) day,COUNT(*) total,SUM(status IN ('COMPLETED','CLOSED')) completed FROM inspection_task WHERE scheduled_at>=CURRENT_DATE-INTERVAL 13 DAY GROUP BY DATE(scheduled_at) ORDER BY day"));r.put("openIssues",jdbc.queryForObject("SELECT COUNT(*) FROM inspection_issue WHERE status<>'CLOSED'",Integer.class));return r;
    }

    private void markOverdue(){jdbc.update("UPDATE inspection_task SET status='OVERDUE' WHERE status IN ('PENDING','IN_PROGRESS') AND due_at<NOW(3)");}
    private String equipmentSnapshot(String targetType,String targetId){if(!"INSPECTION_EQUIPMENT".equals(targetType))return "{}";List<Map<String,Object>> rows=jdbc.queryForList("SELECT e.equipment_code equipmentCode,e.equipment_name equipmentName,c.name categoryName,e.system_name systemName,e.installation_location installationLocation,e.floor_code floorCode,e.area_name areaName,e.brand,e.model,e.specification,e.serial_number serialNumber,e.responsible_department responsibleDepartment,e.responsible_person responsiblePerson,e.responsible_phone responsiblePhone,e.equipment_status equipmentStatus,e.importance_level importanceLevel,e.attributes_json attributesJson FROM inspection_equipment e JOIN inspection_equipment_category c ON c.id=e.category_id WHERE e.id=?",targetId);return rows.isEmpty()?"{}":jsonText(rows.get(0));}

    private record Owner(String type,Long id,String name,Long deptId,String deptName,String ancestors,Long assigneeId,String assigneeName,String assigneeStatus,String claimedBy){}

    private Owner resolveOwner(String type,Long id,String fallbackName,String claimMode){
        if("ROLE".equals(type)){
            Map<String,Object> role=one("SELECT id,name,dept_id deptId FROM sys_role WHERE id=?",id);
            if(role==null) bad("岗位不存在: "+id);
            Map<String,Object> dept=deptSnapshot(longValue(role.get("deptId")));
            List<Map<String,Object>> holders=jdbc.queryForList("SELECT u.id,u.username,COALESCE(NULLIF(u.display_name,''),u.username) name FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id WHERE ur.role_id=? AND u.status=1 ORDER BY u.id",id);
            return new Owner("ROLE",id,String.valueOf(role.get("name")),longValue(role.get("deptId")),dept==null?null:String.valueOf(dept.get("name")),dept==null?null:String.valueOf(dept.get("path")),
                    holders.size()==1?longValue(holders.get(0).get("id")):null,
                    holders.size()==1?String.valueOf(holders.get(0).get("name")):null,
                    holders.size()==1?"ASSIGNED":"NO_ASSIGNEE",
                    holders.size()==1&&"AUTO".equals(claimMode)?String.valueOf(holders.get(0).get("username")):null);
        }
        if("DEPT".equals(type)) return deptOwner(id,claimMode);
        if(id!=null){
            Map<String,Object> u=one("SELECT u.id,COALESCE(NULLIF(u.display_name,''),u.username) name,u.department_id deptId FROM sys_user u WHERE u.id=?",id);
            if(u==null) bad("用户不存在: "+id);
            Map<String,Object> dept=deptSnapshot(longValue(u.get("deptId")));
            return new Owner("USER",id,String.valueOf(u.get("name")),longValue(u.get("deptId")),dept==null?null:String.valueOf(dept.get("name")),dept==null?null:String.valueOf(dept.get("path")),id,String.valueOf(u.get("name")),"ASSIGNED",null);
        }
        String name=fallbackName==null?"":fallbackName.trim();
        return new Owner("USER",null,name,null,null,null,null,name.isBlank()?null:name,name.isBlank()?"NO_ASSIGNEE":"ASSIGNED",null);
    }

    private Owner deptOwner(Long id,String claimMode){
        Map<String,Object> dept=deptSnapshot(id); if(dept==null) bad("部门不存在: "+id);
        return new Owner("DEPT",id,String.valueOf(dept.get("name")),id,String.valueOf(dept.get("name")),String.valueOf(dept.get("path")),null,null,"CLAIM".equals(claimMode)?"UNCLAIMED":"NO_ASSIGNEE",null);
    }

    private Map<String,Object> deptSnapshot(Long id){
        if(id==null) return null;
        List<Map<String,Object>> l=jdbc.queryForList("SELECT id,name,ancestors FROM sys_department WHERE id=? AND deleted_at IS NULL",id);
        if(l.isEmpty()) return null;
        Map<String,Object> d=new LinkedHashMap<>(l.get(0));
        d.put("path",(d.get("ancestors")==null?"/":String.valueOf(d.get("ancestors")))+id+"/");
        return d;
    }

    private Map<String,Object> userRow(String username){
        List<Map<String,Object>> l=jdbc.queryForList("SELECT id,username,COALESCE(NULLIF(display_name,''),username) name,department_id FROM sys_user WHERE username=? LIMIT 1",username);
        return l.isEmpty()?null:new LinkedHashMap<>(l.get(0));
    }

    private String deptFullPath(Long deptId){
        Map<String,Object> d=deptSnapshot(deptId);
        return d==null?"/":String.valueOf(d.get("path"));
    }
    private void sendReminders(){
        java.util.Map<String,List<Long>> groups=new java.util.LinkedHashMap<>();
        List<Map<String,Object>> due=jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status IN ('PENDING','IN_PROGRESS') AND k.due_at BETWEEN DATE_ADD(NOW(3),INTERVAL p.advance_minutes MINUTE) AND DATE_ADD(DATE_ADD(NOW(3),INTERVAL p.advance_minutes MINUTE),INTERVAL 5 MINUTE)");
        for(Map<String,Object> x:due) collectReminder(groups,longValue(x.get("id")),"INSPECTION_TASK_DUE",String.valueOf(x.getOrDefault("webhookIds","[]")));
        List<Map<String,Object>> urge=jdbc.queryForList("SELECT k.id,p.webhook_config_ids webhookIds,p.urge_minutes urgeMinutes,TIMESTAMPDIFF(MINUTE,k.due_at,NOW(3)) lateMinutes FROM inspection_task k JOIN inspection_plan p ON p.id=k.plan_id WHERE k.status IN ('PENDING','IN_PROGRESS','OVERDUE') AND p.urge_minutes>0 AND k.due_at<NOW(3) AND TIMESTAMPDIFF(MINUTE,k.due_at,NOW(3))>=p.urge_minutes");
        for(Map<String,Object> x:urge){int urgeMinutes=intValue(x.get("urgeMinutes"),30);int late=intValue(x.get("lateMinutes"),0);String event="INSPECTION_TASK_URGE_"+(late/urgeMinutes);collectReminder(groups,longValue(x.get("id")),event,String.valueOf(x.getOrDefault("webhookIds","[]")));}
        for(java.util.Map.Entry<String,List<Long>> e:groups.entrySet()){int sep=e.getKey().indexOf('\u0000');notificationService.notifyInspectionTaskBatch(e.getValue(),e.getKey().substring(0,sep),e.getKey().substring(sep+1));}
    }
    private void collectReminder(java.util.Map<String,List<Long>> groups,Long taskId,String event,String configIds){if(taskId==null)return;try{jdbc.update("INSERT INTO inspection_reminder_log(task_id,event_type) VALUES(?,?)",taskId,event);}catch(DuplicateKeyException ignored){return;}catch(Exception ignored){return;}groups.computeIfAbsent(event+"\u0000"+configIds,k->new java.util.ArrayList<>()).add(taskId);}
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
