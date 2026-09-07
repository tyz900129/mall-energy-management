package com.wanyuecity.energy.usage.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.usage.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalysisDimensionService {
 private final JdbcTemplate jdbc;

 public List<Map<String,Object>> dimensions(){
  List<Map<String,Object>> dimensions=jdbc.queryForList("SELECT * FROM analysis_dimension ORDER BY sort_order,id");
  List<Map<String,Object>> values=jdbc.queryForList("""
   SELECT v.*,COUNT(DISTINCT CASE WHEN a.effective_from<=CURRENT_DATE AND (a.effective_to IS NULL OR a.effective_to>=CURRENT_DATE) THEN a.platform_device_id END) device_count
   FROM analysis_dimension_value v LEFT JOIN device_dimension_assignment a ON a.dimension_value_id=v.id
   GROUP BY v.id ORDER BY v.dimension_id,v.sort_order,v.id""");
  for(Map<String,Object> dimension:dimensions){long id=((Number)dimension.get("id")).longValue();dimension.put("values",values.stream().filter(v->((Number)v.get("dimension_id")).longValue()==id).toList());}
  return dimensions;
 }

 @Transactional public Map<String,Object> createDimension(AnalysisDimensionRequest r){
  try{jdbc.update("INSERT INTO analysis_dimension(dimension_code,dimension_name,sort_order,status,remark) VALUES(?,?,?,?,?)",code(r.dimensionCode()),r.dimensionName().trim(),zero(r.sortOrder()),status(r.status()),r.remark());}
  catch(DuplicateKeyException e){throw BizException.badRequest("维度编码已存在");}
  return jdbc.queryForMap("SELECT * FROM analysis_dimension WHERE id=LAST_INSERT_ID()");
 }
 @Transactional public void updateDimension(Long id,AnalysisDimensionRequest r){
  if(jdbc.update("UPDATE analysis_dimension SET dimension_code=?,dimension_name=?,sort_order=?,status=?,remark=? WHERE id=?",code(r.dimensionCode()),r.dimensionName().trim(),zero(r.sortOrder()),status(r.status()),r.remark(),id)==0)throw BizException.badRequest("分析维度不存在");
 }
 @Transactional public void deleteDimension(Long id){
  if(count("SELECT COUNT(*) FROM device_dimension_assignment WHERE dimension_id=?",id)>0)throw BizException.badRequest("该维度已有设备归属历史，不能删除，可改为停用");
  jdbc.update("DELETE FROM analysis_dimension_value WHERE dimension_id=?",id);if(jdbc.update("DELETE FROM analysis_dimension WHERE id=?",id)==0)throw BizException.badRequest("分析维度不存在");
 }
 @Transactional public Map<String,Object> createValue(Long dimensionId,AnalysisDimensionValueRequest r){requireDimension(dimensionId);try{jdbc.update("INSERT INTO analysis_dimension_value(dimension_id,value_code,value_name,color,sort_order,status,remark) VALUES(?,?,?,?,?,?,?)",dimensionId,code(r.valueCode()),r.valueName().trim(),color(r.color()),zero(r.sortOrder()),status(r.status()),r.remark());}catch(DuplicateKeyException e){throw BizException.badRequest("该维度下分类编码已存在");}return jdbc.queryForMap("SELECT * FROM analysis_dimension_value WHERE id=LAST_INSERT_ID()");}
 @Transactional public void updateValue(Long id,AnalysisDimensionValueRequest r){if(jdbc.update("UPDATE analysis_dimension_value SET value_code=?,value_name=?,color=?,sort_order=?,status=?,remark=? WHERE id=?",code(r.valueCode()),r.valueName().trim(),color(r.color()),zero(r.sortOrder()),status(r.status()),r.remark(),id)==0)throw BizException.badRequest("维度分类不存在");}
 @Transactional public void deleteValue(Long id){if(count("SELECT COUNT(*) FROM device_dimension_assignment WHERE dimension_value_id=?",id)>0)throw BizException.badRequest("该分类已有设备归属历史，不能删除，可改为停用");if(jdbc.update("DELETE FROM analysis_dimension_value WHERE id=?",id)==0)throw BizException.badRequest("维度分类不存在");}

 public List<Map<String,Object>> devices(Long dimensionId,Long valueId,Boolean unassigned,String keyword,String energyType,LocalDate date){
  requireDimension(dimensionId);LocalDate effectiveDate=date==null?LocalDate.now():date;StringBuilder sql=new StringBuilder("""
   SELECT d.platform_device_id,d.device_name,d.comm_address,d.serial_number,d.energy_type,d.energy_type_name,
          a.dimension_value_id,v.value_name,v.color,a.effective_from,a.effective_to
   FROM platform_device_current d
   LEFT JOIN device_dimension_assignment a ON a.platform_device_id=d.platform_device_id AND a.dimension_id=?
     AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?)
   LEFT JOIN analysis_dimension_value v ON v.id=a.dimension_value_id WHERE 1=1""");
  List<Object> args=new ArrayList<>(List.of(dimensionId,effectiveDate,effectiveDate));
  if(valueId!=null){sql.append(" AND a.dimension_value_id=?");args.add(valueId);}if(Boolean.TRUE.equals(unassigned))sql.append(" AND a.id IS NULL");
  if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (d.device_name LIKE ? OR d.platform_device_id LIKE ? OR d.comm_address LIKE ? OR d.serial_number LIKE ?)");String k="%"+keyword.trim()+"%";Collections.addAll(args,k,k,k,k);}
  if(energyType!=null&&!energyType.isBlank()){sql.append(" AND UPPER(d.energy_type)=?");args.add(energyType.trim().toUpperCase(Locale.ROOT));}
  sql.append(" ORDER BY d.device_name,d.platform_device_id");return jdbc.queryForList(sql.toString(),args.toArray());
 }
 public List<Map<String,Object>> history(String deviceId,Long dimensionId){return jdbc.queryForList("""
  SELECT a.*,v.value_name,v.color FROM device_dimension_assignment a JOIN analysis_dimension_value v ON v.id=a.dimension_value_id
  WHERE a.platform_device_id=? AND a.dimension_id=? ORDER BY a.effective_from DESC""",deviceId,dimensionId);}

 @Transactional public int assign(DeviceDimensionAssignRequest r){requireDimension(r.dimensionId());Map<String,Object> value=requireValue(r.dimensionValueId());if(((Number)value.get("dimension_id")).longValue()!=r.dimensionId())throw BizException.badRequest("所选分类不属于当前维度");int changed=0;for(String deviceId:new LinkedHashSet<>(r.deviceIds()))changed+=assignOne(deviceId,r.dimensionId(),r.dimensionValueId(),r.effectiveFrom());return changed;}
 private int assignOne(String deviceId,Long dimensionId,Long valueId,LocalDate from){
  List<Map<String,Object>> exact=jdbc.queryForList("SELECT id,dimension_value_id FROM device_dimension_assignment WHERE platform_device_id=? AND dimension_id=? AND effective_from=?",deviceId,dimensionId,from);
  if(!exact.isEmpty()){if(((Number)exact.get(0).get("dimension_value_id")).longValue()==valueId)return 0;jdbc.update("UPDATE device_dimension_assignment SET dimension_value_id=? WHERE id=?",valueId,exact.get(0).get("id"));return 1;}
  List<Map<String,Object>> active=jdbc.queryForList("SELECT * FROM device_dimension_assignment WHERE platform_device_id=? AND dimension_id=? AND effective_from<? AND (effective_to IS NULL OR effective_to>=?) ORDER BY effective_from DESC LIMIT 1",deviceId,dimensionId,from,from);
  if(!active.isEmpty()&&((Number)active.get(0).get("dimension_value_id")).longValue()==valueId)return 0;
  LocalDate newTo=null;List<Map<String,Object>> next=jdbc.queryForList("SELECT effective_from FROM device_dimension_assignment WHERE platform_device_id=? AND dimension_id=? AND effective_from>? ORDER BY effective_from LIMIT 1",deviceId,dimensionId,from);
  if(!next.isEmpty())newTo=((java.sql.Date)next.get(0).get("effective_from")).toLocalDate().minusDays(1);
  if(!active.isEmpty()){Object oldTo=active.get(0).get("effective_to");if(oldTo!=null){LocalDate priorTo=((java.sql.Date)oldTo).toLocalDate();if(newTo==null||priorTo.isBefore(newTo))newTo=priorTo;}jdbc.update("UPDATE device_dimension_assignment SET effective_to=? WHERE id=?",from.minusDays(1),active.get(0).get("id"));}
  jdbc.update("INSERT INTO device_dimension_assignment(platform_device_id,dimension_id,dimension_value_id,effective_from,effective_to) VALUES(?,?,?,?,?)",deviceId,dimensionId,valueId,from,newTo);return 1;
 }
 private void requireDimension(Long id){if(count("SELECT COUNT(*) FROM analysis_dimension WHERE id=?",id)==0)throw BizException.badRequest("分析维度不存在");}private Map<String,Object> requireValue(Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM analysis_dimension_value WHERE id=?",id);if(rows.isEmpty())throw BizException.badRequest("维度分类不存在");return rows.get(0);}private long count(String sql,Object arg){Long n=jdbc.queryForObject(sql,Long.class,arg);return n==null?0:n;}private int zero(Integer n){return n==null?0:n;}private int status(Integer n){return n==null?1:n;}private String code(String s){return s.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");}private String color(String s){return s==null||s.isBlank()?"#409EFF":s.trim();}
}
