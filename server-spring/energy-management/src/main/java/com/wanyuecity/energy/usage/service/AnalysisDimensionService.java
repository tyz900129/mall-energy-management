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
   SELECT v.*,COUNT(DISTINCT a.platform_device_id) device_count
   FROM analysis_dimension_value v LEFT JOIN (
     SELECT current_assignment.* FROM device_dimension_assignment current_assignment
     JOIN (SELECT platform_device_id,dimension_id,MAX(id) id FROM device_dimension_assignment GROUP BY platform_device_id,dimension_id) latest
       ON latest.id=current_assignment.id
   ) a ON a.dimension_value_id=v.id
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
  if(count("SELECT COUNT(*) FROM device_dimension_assignment WHERE dimension_id=?",id)>0)throw BizException.badRequest("该维度已有设备归属，不能删除，可改为停用");
  jdbc.update("DELETE FROM analysis_dimension_value WHERE dimension_id=?",id);if(jdbc.update("DELETE FROM analysis_dimension WHERE id=?",id)==0)throw BizException.badRequest("分析维度不存在");
 }
 @Transactional public Map<String,Object> createValue(Long dimensionId,AnalysisDimensionValueRequest r){requireDimension(dimensionId);try{jdbc.update("INSERT INTO analysis_dimension_value(dimension_id,value_code,value_name,color,sort_order,status,remark) VALUES(?,?,?,?,?,?,?)",dimensionId,code(r.valueCode()),r.valueName().trim(),color(r.color()),zero(r.sortOrder()),status(r.status()),r.remark());}catch(DuplicateKeyException e){throw BizException.badRequest("该维度下分类编码已存在");}return jdbc.queryForMap("SELECT * FROM analysis_dimension_value WHERE id=LAST_INSERT_ID()");}
 @Transactional public void updateValue(Long id,AnalysisDimensionValueRequest r){if(jdbc.update("UPDATE analysis_dimension_value SET value_code=?,value_name=?,color=?,sort_order=?,status=?,remark=? WHERE id=?",code(r.valueCode()),r.valueName().trim(),color(r.color()),zero(r.sortOrder()),status(r.status()),r.remark(),id)==0)throw BizException.badRequest("维度分类不存在");}
 @Transactional public void deleteValue(Long id){if(count("SELECT COUNT(*) FROM device_dimension_assignment WHERE dimension_value_id=?",id)>0)throw BizException.badRequest("该分类已有设备归属，不能删除，可改为停用");if(jdbc.update("DELETE FROM analysis_dimension_value WHERE id=?",id)==0)throw BizException.badRequest("维度分类不存在");}

 public List<Map<String,Object>> devices(Long dimensionId,Long valueId,Boolean unassigned,String keyword,String energyType){
  requireDimension(dimensionId);StringBuilder sql=new StringBuilder("""
   SELECT d.platform_device_id,d.device_name,d.comm_address,d.serial_number,d.energy_type,d.energy_type_name,
          a.dimension_value_id,v.value_name,v.color
   FROM platform_device_current d
   LEFT JOIN device_dimension_assignment a ON a.id=(
     SELECT MAX(latest.id) FROM device_dimension_assignment latest
     WHERE latest.platform_device_id=d.platform_device_id AND latest.dimension_id=?
   )
   LEFT JOIN analysis_dimension_value v ON v.id=a.dimension_value_id WHERE 1=1""");
  List<Object> args=new ArrayList<>(List.of(dimensionId));
  if(valueId!=null){sql.append(" AND a.dimension_value_id=?");args.add(valueId);}if(Boolean.TRUE.equals(unassigned))sql.append(" AND a.id IS NULL");
  if(keyword!=null&&!keyword.isBlank()){sql.append(" AND (d.device_name LIKE ? OR d.platform_device_id LIKE ? OR d.comm_address LIKE ? OR d.serial_number LIKE ?)");String k="%"+keyword.trim()+"%";Collections.addAll(args,k,k,k,k);}
  if(energyType!=null&&!energyType.isBlank()){sql.append(" AND UPPER(d.energy_type)=?");args.add(energyType.trim().toUpperCase(Locale.ROOT));}
  sql.append(" ORDER BY d.device_name,d.platform_device_id");return jdbc.queryForList(sql.toString(),args.toArray());
 }
 @Transactional public int assign(DeviceDimensionAssignRequest r){
  requireDimension(r.dimensionId());
  if(r.dimensionValueId()!=null){Map<String,Object> value=requireValue(r.dimensionValueId());if(((Number)value.get("dimension_id")).longValue()!=r.dimensionId())throw BizException.badRequest("所选分类不属于当前维度");}
  int changed=0;for(String deviceId:new LinkedHashSet<>(r.deviceIds()))changed+=assignOne(deviceId,r.dimensionId(),r.dimensionValueId());return changed;
 }
 private int assignOne(String deviceId,Long dimensionId,Long valueId){
  List<Map<String,Object>> current=jdbc.queryForList("SELECT dimension_value_id FROM device_dimension_assignment WHERE platform_device_id=? AND dimension_id=? ORDER BY id DESC LIMIT 1",deviceId,dimensionId);
  boolean unchanged=valueId==null?current.isEmpty():!current.isEmpty()&&((Number)current.get(0).get("dimension_value_id")).longValue()==valueId;
  jdbc.update("DELETE FROM device_dimension_assignment WHERE platform_device_id=? AND dimension_id=?",deviceId,dimensionId);
  if(valueId!=null)jdbc.update("INSERT INTO device_dimension_assignment(platform_device_id,dimension_id,dimension_value_id,effective_from,effective_to) VALUES(?,?,?,'1970-01-01',NULL)",deviceId,dimensionId,valueId);
  return unchanged?0:1;
 }
 private void requireDimension(Long id){if(count("SELECT COUNT(*) FROM analysis_dimension WHERE id=?",id)==0)throw BizException.badRequest("分析维度不存在");}private Map<String,Object> requireValue(Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM analysis_dimension_value WHERE id=?",id);if(rows.isEmpty())throw BizException.badRequest("维度分类不存在");return rows.get(0);}private long count(String sql,Object arg){Long n=jdbc.queryForObject(sql,Long.class,arg);return n==null?0:n;}private int zero(Integer n){return n==null?0:n;}private int status(Integer n){return n==null?1:n;}private String code(String s){return s.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]","_");}private String color(String s){return s==null||s.isBlank()?"#409EFF":s.trim();}
}
