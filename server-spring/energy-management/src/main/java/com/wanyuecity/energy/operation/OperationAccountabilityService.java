package com.wanyuecity.energy.operation;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OperationAccountabilityService {
    private final JdbcTemplate jdbc;
    public OperationAccountabilityService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<Map<String,Object>> list(String status,String keyword){
        String s=Objects.toString(status,"").trim(),k="%"+Objects.toString(keyword,"").trim()+"%";
        return jdbc.queryForList("SELECT o.id,o.order_no orderNo,o.period_date periodDate,o.stall_code_snapshot stallCode,o.merchant_name_snapshot merchantName,o.issue_description issueDescription,o.responsible_name responsibleName,o.responsible_phone responsiblePhone,o.deadline_at deadlineAt,o.status,o.created_by createdBy,o.handled_by handledBy,o.resolved_at resolvedAt,o.closed_at closedAt,o.created_at createdAt FROM operation_accountability_order o WHERE (?='' OR o.status=?) AND (?='%%' OR o.order_no LIKE ? OR o.stall_code_snapshot LIKE ? OR COALESCE(o.merchant_name_snapshot,'') LIKE ? OR COALESCE(o.responsible_name,'') LIKE ?) ORDER BY FIELD(o.status,'PENDING','PROCESSING','RESOLVED','CLOSED'),o.created_at DESC LIMIT 1000",s,s,k,k,k,k,k);
    }

    public Map<String,Object> detail(Long id){
        return one("SELECT o.*,o.order_no orderNo,o.property_inspection_id propertyInspectionId,o.stall_code_snapshot stallCode,o.merchant_name_snapshot merchantName,o.issue_description issueDescription,o.responsible_name responsibleName,o.responsible_phone responsiblePhone,o.corrective_action correctiveAction,o.deadline_at deadlineAt,o.handling_result handlingResult,o.created_by createdBy,o.handled_by handledBy,o.resolved_at resolvedAt,o.closed_at closedAt,o.created_at createdAt,i.inspector_name inspectorName,i.inspected_at inspectedAt,i.template_name_snapshot templateName FROM operation_accountability_order o JOIN operation_property_inspection i ON i.id=o.property_inspection_id WHERE o.id=?",id);
    }

    @Transactional public Map<String,Object> create(Long inspectionId,Map<String,Object> body,String username){
        Map<String,Object> i=one("SELECT i.id,i.stall_id stallId,i.period_date periodDate,i.status,i.exception_description issueDescription,s.stall_code stallCode,i.merchant_name_snapshot merchantName FROM operation_property_inspection i JOIN stall s ON s.id=i.stall_id WHERE i.id=?",inspectionId);
        if(!"ABNORMAL".equals(i.get("status")))throw bad("只有异常检查记录可以生成追责单");
        List<Map<String,Object>> old=jdbc.queryForList("SELECT id FROM operation_accountability_order WHERE property_inspection_id=?",inspectionId);
        if(!old.isEmpty())return detail(((Number)old.get(0).get("id")).longValue());
        String no="ZR"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+"-"+inspectionId;
        jdbc.update("INSERT INTO operation_accountability_order(order_no,property_inspection_id,stall_id,period_date,stall_code_snapshot,merchant_name_snapshot,issue_description,responsible_name,responsible_phone,corrective_action,deadline_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",no,inspectionId,i.get("stallId"),i.get("periodDate"),i.get("stallCode"),i.get("merchantName"),value(body,"issueDescription",i.get("issueDescription")),value(body,"responsibleName",null),value(body,"responsiblePhone",null),value(body,"correctiveAction",null),dateTime(body.get("deadlineAt")),username);
        Long id=jdbc.queryForObject("SELECT id FROM operation_accountability_order WHERE property_inspection_id=?",Long.class,inspectionId);
        return detail(id);
    }

    @Transactional public Map<String,Object> update(Long id,Map<String,Object> body,String username){
        Map<String,Object> old=detail(id);String status=value(body,"status",old.get("status"));
        if(!Set.of("PENDING","PROCESSING","RESOLVED","CLOSED").contains(status))throw bad("不支持的追责状态");
        Object resolved="RESOLVED".equals(status)||"CLOSED".equals(status)?Timestamp.valueOf(LocalDateTime.now()):old.get("resolved_at");
        Object closed="CLOSED".equals(status)?Timestamp.valueOf(LocalDateTime.now()):null;
        jdbc.update("UPDATE operation_accountability_order SET responsible_name=?,responsible_phone=?,corrective_action=?,deadline_at=?,status=?,handling_result=?,handled_by=?,resolved_at=?,closed_at=? WHERE id=?",value(body,"responsibleName",old.get("responsible_name")),value(body,"responsiblePhone",old.get("responsible_phone")),value(body,"correctiveAction",old.get("corrective_action")),body.containsKey("deadlineAt")?dateTime(body.get("deadlineAt")):old.get("deadline_at"),status,value(body,"handlingResult",old.get("handling_result")),username,resolved,closed,id);
        return detail(id);
    }

    private Timestamp dateTime(Object v){String s=Objects.toString(v,"").trim();if(s.isBlank())return null;return Timestamp.valueOf(s.replace('T',' '));}
    private String value(Map<String,Object>b,String key,Object fallback){return Objects.toString(b.getOrDefault(key,fallback),"").trim();}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>>r=jdbc.queryForList(sql,args);if(r.isEmpty())throw bad("追责数据不存在");return r.get(0);}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
