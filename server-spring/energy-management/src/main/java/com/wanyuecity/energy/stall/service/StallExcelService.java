package com.wanyuecity.energy.stall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.service.dto.StallImportResult;
import com.wanyuecity.energy.stall.service.dto.StallUpsertRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class StallExcelService {
 private static final String[] HEADERS={"铺位编码","当前商户（自动同步，请勿修改）","楼层编码","面积(㎡)","业态编码","状态"};
 private final StallMapper mapper; private final StallService stallService;
 public void export(String keyword,String status,String floorCode,String businessType,HttpServletResponse response)throws IOException{
  var q=new LambdaQueryWrapper<Stall>().orderByAsc(Stall::getStallCode);
  if(keyword!=null&&!keyword.isBlank()){String k=keyword.trim();q.and(w->w.like(Stall::getStallCode,k).or().like(Stall::getStallName,k));}
  if(status!=null&&!status.isBlank())q.eq(Stall::getStatus,status);if(floorCode!=null&&!floorCode.isBlank())q.eq(Stall::getFloorCode,floorCode);if(businessType!=null&&!businessType.isBlank())q.eq(Stall::getBusinessType,businessType);
  try(Workbook wb=new XSSFWorkbook()){Sheet sh=wb.createSheet("铺位");Row h=sh.createRow(0);CellStyle hs=wb.createCellStyle();Font f=wb.createFont();f.setBold(true);hs.setFont(f);for(int i=0;i<HEADERS.length;i++){Cell c=h.createCell(i);c.setCellValue(HEADERS[i]);c.setCellStyle(hs);sh.setColumnWidth(i,i==1?8000:4500);}
   int n=1;for(Stall s:mapper.selectList(q)){Row r=sh.createRow(n++);set(r,0,s.getStallCode());set(r,1,s.getStallName());set(r,2,s.getFloorCode());if(s.getArea()!=null)r.createCell(3).setCellValue(s.getArea().doubleValue());set(r,4,s.getBusinessType());set(r,5,statusLabel(s.getStatus()));}
   response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");String name=URLEncoder.encode("铺位数据.xlsx",StandardCharsets.UTF_8).replace("+","%20");response.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+name);wb.write(response.getOutputStream());}
 }
 public StallImportResult importFile(MultipartFile file){if(file==null||file.isEmpty())throw BizException.badRequest("请选择Excel文件");List<String> errors=new ArrayList<>();int ok=0;
  try(Workbook wb=WorkbookFactory.create(file.getInputStream())){Sheet sh=wb.getSheetAt(0);DataFormatter fmt=new DataFormatter();for(int i=1;i<=sh.getLastRowNum();i++){Row row=sh.getRow(i);if(row==null)continue;String code=text(row,0,fmt);if(code.isBlank())continue;try{Stall existing=mapper.selectOne(new LambdaQueryWrapper<Stall>().eq(Stall::getStallCode,code).last("LIMIT 1"));StallUpsertRequest r=new StallUpsertRequest();r.setStallCode(code);r.setStallName(existing==null?null:existing.getStallName());r.setFloorCode(text(row,2,fmt));String area=text(row,3,fmt);if(!area.isBlank())r.setArea(new BigDecimal(area));r.setBusinessType(text(row,4,fmt));r.setStatus(statusValue(text(row,5,fmt)));if(existing!=null){r.setAreaName(existing.getAreaName());r.setBusinessCategory(existing.getBusinessCategory());r.setRemark(existing.getRemark());r.setCurrentMerchantId(existing.getCurrentMerchantId());stallService.update(existing.getId(),r);}else stallService.create(r);ok++;}catch(Exception ex){errors.add("第"+(i+1)+"行（"+code+"）："+rootMessage(ex));}}
  }catch(Exception ex){throw BizException.badRequest("Excel读取失败："+rootMessage(ex));}return new StallImportResult(ok,errors.size(),errors.size()>100?errors.subList(0,100):errors);
 }
 private static void set(Row r,int i,String v){r.createCell(i).setCellValue(v==null?"":v);}private static String text(Row r,int i,DataFormatter f){Cell c=r.getCell(i);return c==null?"":f.formatCellValue(c).trim();}
 private static String statusValue(String s){return switch(s){case "","空置","AVAILABLE"->"AVAILABLE";case "装修中","RENOVATING"->"RENOVATING";case "撤场施工中","EXIT_CONSTRUCTION"->"EXIT_CONSTRUCTION";case "在用","OCCUPIED"->"OCCUPIED";case "维护","MAINTENANCE"->"MAINTENANCE";case "停用","DECOMMISSIONED"->"DECOMMISSIONED";default->s;};}
 private static String statusLabel(String s){return switch(s==null?"":s){case "AVAILABLE"->"空置";case "RENOVATING"->"装修中";case "EXIT_CONSTRUCTION"->"撤场施工中";case "OCCUPIED"->"在用";case "MAINTENANCE"->"维护";case "DECOMMISSIONED"->"停用";default->s;};}
 private static String rootMessage(Throwable e){Throwable x=e;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
}
