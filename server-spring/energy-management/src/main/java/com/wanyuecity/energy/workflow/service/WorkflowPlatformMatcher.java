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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j(topic="platform-sync") @Service @RequiredArgsConstructor
public class WorkflowPlatformMatcher {
 private final WorkflowMapper workflowMapper; private final WorkflowExpectedChangeMapper changeMapper;
 private final PlatformAccountCurrentMapper accountMapper; private final PlatformAccountDeviceCurrentMapper relationMapper;
 private final JdbcTemplate jdbc;
 private final NotificationService notificationService;
 private final PlatformTransactionManager transactionManager;

 public int matchAfterSync(Long batchId){
  List<Workflow> workflows=workflowMapper.selectList(new QueryWrapper<Workflow>().in("status",List.of("FIELD_VERIFIED","WAITING_PLATFORM_CHANGE","PLATFORM_VERIFYING","PARTIAL_MATCH","EXCEPTION")));
  TransactionTemplate transaction = new TransactionTemplate(transactionManager);
  transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  int completed=0;
  for(Workflow w:workflows){
   try{
    Boolean matched=transaction.execute(status->match(w,batchId));
    if(Boolean.TRUE.equals(matched))completed++;
   }catch(Exception e){log.error("workflow match failed: workflowId={}, batchId={}",w.getId(),batchId,e);}
  }
  return completed;
 }
 private boolean match(Workflow w,Long batchId){
  if(exists(w.getId(),batchId))return false;
  List<String> accounts=targetAccounts(w.getMerchantCode());
  List<WorkflowExpectedChange> changes=changeMapper.selectList(new QueryWrapper<WorkflowExpectedChange>().eq("workflow_id",w.getId()).orderByAsc("id"));
  String target=accounts.size()==1?accounts.get(0):null; List<Result> results=new ArrayList<>(); int matched=0,total=0;boolean hardError=false;
  for(WorkflowExpectedChange c:changes){
   List<String> actual=c.getPlatformDeviceId()==null?List.of():relationMapper.selectList(new QueryWrapper<PlatformAccountDeviceCurrent>().eq("platform_device_id",c.getPlatformDeviceId())).stream().map(PlatformAccountDeviceCurrent::getAccountNumber).distinct().sorted().toList();
   String status,reason=null;
   if("ACCOUNT_DISABLE".equals(c.getChangeType())){
    PlatformAccountCurrent a=accountMapper.selectOne(new QueryWrapper<PlatformAccountCurrent>().eq("account_number",c.getFromAccountNumber()).last("LIMIT 1"));
    if(a==null){status="MATCHED";reason="账户已停用或已从原平台移除";}
    else if(isDisabled(a.getPlatformStatus())){status="MATCHED";reason="账户已停用";}
    else {status="PENDING";reason="账户尚未停用";}
   }else if("DEVICE_UNBIND".equals(c.getChangeType())){
    total++;
    if(c.getFromAccountNumber()==null){status="MISMATCH";reason="缺少原计费账户快照";}
    else if(!actual.contains(c.getFromAccountNumber())){status="MATCHED";matched++;}
    else {status="PENDING";reason="设备尚未从原账户解除绑定";}
   }else{
    total++;
    if(accounts.isEmpty()){status="PENDING";reason="尚未同步到商户代码对应的新账户";}
    else if(accounts.size()>1){status="ACCOUNT_AMBIGUOUS";reason="商户代码对应多个启用计费账户";}
    else if(actual.size()==1&&target.equals(actual.get(0))){status="MATCHED";matched++;}
    else if(actual.isEmpty()){status="PENDING";reason="设备尚未绑定新账户";}
    else if(c.getFromAccountNumber()!=null&&actual.contains(c.getFromAccountNumber())){status="PENDING";reason=actual.contains(target)?"设备已绑定新账户，但尚未从原账户解除":"设备尚未从原账户解除并绑定新账户";}
    else if(c.getFromAccountNumber()==null){status="PENDING";reason="旧流程未保存原账户快照，设备尚未完成向新账户转移";}
    else {status="MISMATCH";reason="设备绑定的账户与目标账户不一致";}
   }
   if(Set.of("MISMATCH","ACCOUNT_AMBIGUOUS","ACCOUNT_NOT_FOUND").contains(status))hardError=true;
   results.add(new Result(c,actual,status,reason));
  }
  boolean beforeEffective=w.getRequestedBillingStartTime()!=null&&LocalDateTime.now().isBefore(w.getRequestedBillingStartTime());
  String overall=beforeEffective?"PENDING":matched==total&&total>0?"MATCHED":matched>0?"PARTIAL_MATCH":hardError?"MISMATCH":"PENDING";
  String summary=(beforeEffective?"未到预期生效时间；":"")+"匹配 "+matched+"/"+total+" 项设备变更";
  Long runId=insertRun(w.getId(),batchId,overall,matched,total,summary);
  LocalDateTime now=LocalDateTime.now();
  for(Result r:results){
   String expectedAccount="DEVICE_BIND".equals(r.change.getChangeType())?target:r.change.getFromAccountNumber();
   String actualAccounts=String.join(",",r.actual);
   jdbc.update("INSERT INTO workflow_match_detail(match_run_id,expected_change_id,platform_device_id,expected_account_number,actual_account_numbers,match_status,reason) VALUES(?,?,?,?,?,?,?)",runId,r.change.getId(),r.change.getPlatformDeviceId(),expectedAccount,actualAccounts,r.status,r.reason);
   jdbc.update("UPDATE workflow_expected_change SET to_account_number=?,actual_account_number=?,verify_status=?,verified_at=?,exception_reason=? WHERE id=?",target,actualAccounts,r.status,now,r.reason,r.change.getId());
  }
  w.setTargetAccountNumber(target);w.setWaitingPlatformAt(w.getWaitingPlatformAt()==null?now:w.getWaitingPlatformAt());w.setVerifiedAt(now);
  if("MATCHED".equals(overall)){w.setStatus("COMPLETED");w.setCompletedAt(now);}else if("PARTIAL_MATCH".equals(overall))w.setStatus("PARTIAL_MATCH");else if("MISMATCH".equals(overall))w.setStatus("EXCEPTION");else w.setStatus("WAITING_PLATFORM_CHANGE");workflowMapper.updateById(w);if("MATCHED".equals(overall))notificationService.notifyWorkflowCompleted(w.getId());return "MATCHED".equals(overall);
 }
 private List<String> targetAccounts(String merchantCode){return jdbc.queryForList("SELECT DISTINCT p.account_number FROM platform_account_current p LEFT JOIN platform_account_local_profile l ON l.account_number=p.account_number WHERE COALESCE(NULLIF(l.merchant_code,''),p.merchant_code)=? AND (p.platform_status IS NULL OR UPPER(p.platform_status) NOT IN ('0','DISABLED','INACTIVE')) ORDER BY p.account_number",String.class,merchantCode);}
 private boolean exists(Long workflowId,Long batchId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM workflow_match_run WHERE workflow_id=? AND sync_batch_id=?",Integer.class,workflowId,batchId);return n!=null&&n>0;}
 private boolean isDisabled(String status){return status!=null&&Set.of("0","DISABLED","INACTIVE").contains(status.toUpperCase());}
 private Long insertRun(Long wid,Long bid,String status,int matched,int total,String summary){var kh=new org.springframework.jdbc.support.GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO workflow_match_run(workflow_id,sync_batch_id,match_status,matched_count,total_count,summary) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,wid);ps.setLong(2,bid);ps.setString(3,status);ps.setInt(4,matched);ps.setInt(5,total);ps.setString(6,summary);return ps;},kh);return Objects.requireNonNull(kh.getKey()).longValue();}
 private record Result(WorkflowExpectedChange change,List<String> actual,String status,String reason){}
}
