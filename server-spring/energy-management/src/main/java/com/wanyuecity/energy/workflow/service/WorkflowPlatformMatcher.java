package com.wanyuecity.energy.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.workflow.entity.Workflow;
import com.wanyuecity.energy.workflow.entity.WorkflowExpectedChange;
import com.wanyuecity.energy.workflow.mapper.WorkflowExpectedChangeMapper;
import com.wanyuecity.energy.workflow.mapper.WorkflowMapper;
import com.wanyuecity.energy.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j(topic="platform-sync") @Service @RequiredArgsConstructor
public class WorkflowPlatformMatcher {
 private final WorkflowMapper workflowMapper; private final WorkflowExpectedChangeMapper changeMapper;
 private final PlatformAccountCurrentMapper accountMapper; private final PlatformAccountDeviceCurrentMapper relationMapper;
 private final JdbcTemplate jdbc;
 private final NotificationService notificationService;

 @Transactional public int matchAfterSync(Long batchId){
  List<Workflow> workflows=workflowMapper.selectList(new QueryWrapper<Workflow>().in("status",List.of("FIELD_VERIFIED","WAITING_PLATFORM_CHANGE","PLATFORM_VERIFYING","PARTIAL_MATCH","EXCEPTION")));
  int completed=0; for(Workflow w:workflows){try{if(match(w,batchId))completed++;}catch(Exception e){log.error("workflow match failed: workflowId={}, batchId={}",w.getId(),batchId,e);}}return completed;
 }
 private boolean match(Workflow w,Long batchId){
  if(exists(w.getId(),batchId))return false;
  List<PlatformAccountCurrent> accounts=accountMapper.selectList(new QueryWrapper<PlatformAccountCurrent>().eq("merchant_code",w.getMerchantCode()));
  List<WorkflowExpectedChange> changes=changeMapper.selectList(new QueryWrapper<WorkflowExpectedChange>().eq("workflow_id",w.getId()).orderByAsc("id"));
  String target=accounts.size()==1?accounts.get(0).getAccountNumber():null; List<Result> results=new ArrayList<>(); int matched=0;
  for(WorkflowExpectedChange c:changes){
   List<String> actual=c.getPlatformDeviceId()==null?List.of():relationMapper.selectList(new QueryWrapper<PlatformAccountDeviceCurrent>().eq("platform_device_id",c.getPlatformDeviceId())).stream().map(PlatformAccountDeviceCurrent::getAccountNumber).distinct().sorted().toList();
   String status,reason=null;
   if("ACCOUNT_DISABLE".equals(c.getChangeType())){
    PlatformAccountCurrent a=accountMapper.selectOne(new QueryWrapper<PlatformAccountCurrent>().eq("account_number",c.getFromAccountNumber()).last("LIMIT 1"));
    if(a==null){status="ACCOUNT_NOT_FOUND";reason="未找到待停用账户";}
    else if(isDisabled(a.getPlatformStatus())){status="MATCHED";matched++;}
    else {status="PENDING";reason="账户尚未停用";}
   }else if("DEVICE_UNBIND".equals(c.getChangeType())){
    if(c.getFromAccountNumber()==null){status="MISMATCH";reason="缺少原计费账户快照";}
    else if(!actual.contains(c.getFromAccountNumber())){status="MATCHED";matched++;}
    else {status="PENDING";reason="设备尚未从原账户解除绑定";}
   }else if(accounts.isEmpty()){status="ACCOUNT_NOT_FOUND";reason="未找到商户代码对应的原平台账户";}
   else if(accounts.size()>1){status="ACCOUNT_AMBIGUOUS";reason="商户代码对应多个原平台账户";}
   else if(actual.size()==1&&target.equals(actual.get(0))){status="MATCHED";matched++;}
   else if(actual.isEmpty()){status="PENDING";reason="设备尚未绑定目标账户";}
   else {status="MISMATCH";reason="设备当前账户与目标账户不一致";}
   results.add(new Result(c,actual,status,reason));
  }
  String overall=matched==changes.size()&&!changes.isEmpty()?"MATCHED":matched>0?"PARTIAL_MATCH":"MISMATCH";
  String summary="匹配 "+matched+"/"+changes.size()+" 项预期变更";
  Long runId=insertRun(w.getId(),batchId,overall,matched,changes.size(),summary);
  LocalDateTime now=LocalDateTime.now(); for(Result r:results){jdbc.update("INSERT INTO workflow_match_detail(match_run_id,expected_change_id,platform_device_id,expected_account_number,actual_account_numbers,match_status,reason) VALUES(?,?,?,?,?,?,?)",runId,r.change.getId(),r.change.getPlatformDeviceId(),target,String.join(",",r.actual),r.status,r.reason);r.change.setToAccountNumber(target);r.change.setActualAccountNumber(String.join(",",r.actual));r.change.setVerifyStatus(r.status);r.change.setVerifiedAt(now);r.change.setExceptionReason(r.reason);changeMapper.updateById(r.change);}
  w.setTargetAccountNumber(target);w.setWaitingPlatformAt(w.getWaitingPlatformAt()==null?now:w.getWaitingPlatformAt());w.setVerifiedAt(now);
  if("MATCHED".equals(overall)){w.setStatus("COMPLETED");w.setCompletedAt(now);}else w.setStatus("PARTIAL_MATCH".equals(overall)?"PARTIAL_MATCH":"EXCEPTION");workflowMapper.updateById(w);if("MATCHED".equals(overall))notificationService.notifyWorkflowCompleted(w.getId());return "MATCHED".equals(overall);
 }
 private boolean exists(Long workflowId,Long batchId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM workflow_match_run WHERE workflow_id=? AND sync_batch_id=?",Integer.class,workflowId,batchId);return n!=null&&n>0;}
 private boolean isDisabled(String status){return status!=null&&Set.of("0","DISABLED","INACTIVE").contains(status.toUpperCase());}
 private Long insertRun(Long wid,Long bid,String status,int matched,int total,String summary){var kh=new org.springframework.jdbc.support.GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO workflow_match_run(workflow_id,sync_batch_id,match_status,matched_count,total_count,summary) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,wid);ps.setLong(2,bid);ps.setString(3,status);ps.setInt(4,matched);ps.setInt(5,total);ps.setString(6,summary);return ps;},kh);return Objects.requireNonNull(kh.getKey()).longValue();}
 private record Result(WorkflowExpectedChange change,List<String> actual,String status,String reason){}
}
