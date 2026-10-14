package com.wanyuecity.energy.operation;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;

@Service
public class OperationMerchantResponsibilityService {
    private final JdbcTemplate jdbc;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;
    public OperationMerchantResponsibilityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> page(String keyword, Long managerUserId, int pageNum, int pageSize) {
        int pn=Math.max(1,pageNum), ps=Math.min(100,Math.max(10,pageSize)), offset=(pn-1)*ps;
        String k="%"+Objects.toString(keyword,"").trim()+"%";
        String filter=(managerUserId==null?"":" AND r.responsible_user_id="+managerUserId);
        String where=" WHERE s.status<>'DISABLED' AND (s.stall_code LIKE ? OR s.stall_name LIKE ? OR m.merchant_code LIKE ? OR m.merchant_name LIKE ?)"+filter;
        String from=" FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id LEFT JOIN operation_merchant_responsibility r ON r.stall_id=s.id AND r.status='ACTIVE' LEFT JOIN sys_user u ON u.id=r.responsible_user_id LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=s.business_type";
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,k,k,k,k);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT s.id stallId,s.stall_code stallCode,s.stall_name stallName,s.floor_name floorName,s.business_type businessType,COALESCE(d.item_label,s.business_type) businessTypeLabel,s.status stallStatus,s.operation_qr_token qrToken,m.merchant_code merchantCode,m.merchant_name merchantName,r.id responsibilityId,r.responsible_user_id managerUserId,u.username managerUsername,u.display_name managerName,COALESCE(NULLIF(r.contact_phone,''),u.phone) managerPhone,r.effective_from effectiveFrom,r.remark"+from+where+" ORDER BY s.floor_code,s.stall_code LIMIT ? OFFSET ?",k,k,k,k,ps,offset);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("records",rows);result.put("total",total==null?0:total);result.put("current",pn);result.put("size",ps);return result;
    }

    public List<Map<String,Object>> managers(String keyword) {
        String k="%"+Objects.toString(keyword,"").trim()+"%";
        return jdbc.queryForList("SELECT DISTINCT u.id,u.username,u.display_name displayName,u.phone FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.code='APPLICANT' AND (u.username LIKE ? OR u.display_name LIKE ? OR u.phone LIKE ?) ORDER BY u.display_name,u.username LIMIT 100",k,k,k);
    }

    public List<Map<String,Object>> history(Long stallId) {
        return jdbc.queryForList("SELECT h.id,h.transfer_type transferType,h.contact_phone contactPhone,h.effective_from effectiveFrom,h.effective_to effectiveTo,h.remark,h.operated_by operatedBy,h.created_at createdAt,fu.display_name fromManagerName,tu.display_name toManagerName FROM operation_merchant_responsibility_history h LEFT JOIN sys_user fu ON fu.id=h.from_user_id LEFT JOIN sys_user tu ON tu.id=h.to_user_id WHERE h.stall_id=? ORDER BY h.id DESC",stallId);
    }

    public Map<String,Object> publicStall(String token){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT s.id stallId,s.stall_code stallCode,s.floor_name floorName,s.business_type businessType,COALESCE(d.item_label,s.business_type) businessTypeLabel,s.status stallStatus,COALESCE(NULLIF(m.merchant_name,''),NULLIF(s.stall_name,''),s.stall_code) merchantName FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=s.business_type WHERE s.operation_qr_token=? AND s.status<>'DISABLED'",token);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"二维码无效或铺位已停用");return rows.get(0);
    }

    public Map<String,Object> detailByToken(String token){
        Map<String,Object> base=publicStall(token);Long stallId=number(base.get("stallId"));
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.responsible_user_id managerUserId,u.display_name managerName,COALESCE(NULLIF(r.contact_phone,''),u.phone) managerPhone,r.effective_from effectiveFrom FROM operation_merchant_responsibility r JOIN sys_user u ON u.id=r.responsible_user_id WHERE r.stall_id=? AND r.status='ACTIVE'",stallId);
        if(!rows.isEmpty())base.putAll(rows.get(0));return base;
    }

    public byte[] qr(Long stallId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT operation_qr_token token FROM stall WHERE id=? AND status<>'DISABLED'",stallId);if(rows.isEmpty())throw bad("铺位不存在或已停用");
        String token=Objects.toString(rows.get(0).get("token"),"");if(token.isBlank()){token=UUID.randomUUID().toString().replace("-","");jdbc.update("UPDATE stall SET operation_qr_token=? WHERE id=?",token,stallId);}
        try{String url=publicBaseUrl.replaceAll("/+$","")+"/operation/stall/"+token;BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,520,520);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return out.toByteArray();}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"铺位二维码生成失败");}
    }

    @Transactional
    public Map<String,Object> assign(Map<String,Object> body,String operator) {
        Long stallId=number(body.get("stallId")), userId=number(body.get("managerUserId"));
        if(stallId==null||userId==null) throw bad("请选择铺位和责任商管");
        ensureExists("SELECT COUNT(*) FROM stall WHERE id=? AND status<>'DISABLED'",stallId,"铺位不存在或已停用");
        ensureExists("SELECT COUNT(*) FROM sys_user WHERE id=? AND status=1",userId,"责任商管不存在或已停用");
        List<Map<String,Object>> old=jdbc.queryForList("SELECT id,responsible_user_id FROM operation_merchant_responsibility WHERE stall_id=?",stallId);
        Long oldUser=old.isEmpty()?null:number(old.get(0).get("responsible_user_id"));
        String phone=text(body.get("contactPhone")),remark=text(body.get("remark")),type=text(body.get("transferType"));
        if(type.isBlank())type=oldUser==null?"ASSIGN":"HANDOVER";
        Timestamp now=Timestamp.valueOf(LocalDateTime.now());
        if(old.isEmpty()) jdbc.update("INSERT INTO operation_merchant_responsibility(stall_id,responsible_user_id,contact_phone,effective_from,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",stallId,userId,phone,now,remark,operator,operator);
        else jdbc.update("UPDATE operation_merchant_responsibility SET responsible_user_id=?,contact_phone=?,effective_from=?,remark=?,status='ACTIVE',updated_by=? WHERE stall_id=?",userId,phone,now,remark,operator,stallId);
        Long id=jdbc.queryForObject("SELECT id FROM operation_merchant_responsibility WHERE stall_id=?",Long.class,stallId);
        jdbc.update("INSERT INTO operation_merchant_responsibility_history(responsibility_id,stall_id,from_user_id,to_user_id,transfer_type,contact_phone,effective_from,remark,operated_by) VALUES(?,?,?,?,?,?,?,?,?)",id,stallId,oldUser,userId,type,phone,now,remark,operator);
        return jdbc.queryForMap("SELECT r.id,r.stall_id stallId,r.responsible_user_id managerUserId,u.display_name managerName,COALESCE(NULLIF(r.contact_phone,''),u.phone) managerPhone,r.effective_from effectiveFrom FROM operation_merchant_responsibility r JOIN sys_user u ON u.id=r.responsible_user_id WHERE r.id=?",id);
    }

    private void ensureExists(String sql,Long id,String message){Integer n=jdbc.queryForObject(sql,Integer.class,id);if(n==null||n==0)throw bad(message);}
    private Long number(Object v){if(v==null||v.toString().isBlank())return null;try{return Long.valueOf(v.toString());}catch(Exception e){return null;}}
    private String text(Object v){return Objects.toString(v,"").trim();}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
