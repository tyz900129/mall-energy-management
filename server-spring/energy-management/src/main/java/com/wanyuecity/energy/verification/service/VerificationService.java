package com.wanyuecity.energy.verification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.service.MerchantNameParser;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.verification.entity.*;
import com.wanyuecity.energy.verification.mapper.*;
import com.wanyuecity.energy.verification.service.dto.*;
import com.wanyuecity.energy.workflow.entity.*;
import com.wanyuecity.energy.workflow.mapper.*;
import com.wanyuecity.energy.workflow.service.WorkflowStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class VerificationService {
 private final FieldVerificationTaskMapper taskMapper; private final FieldVerificationDeviceMapper deviceMapper;
 private final AttachmentMapper attachmentMapper;
 private final WorkflowMapper workflowMapper; private final WorkflowDeviceMapper workflowDeviceMapper; private final StallMapper stallMapper; private final WorkflowStateMachine stateMachine;
 private static final SecureRandom RANDOM=new SecureRandom();

 @Transactional public FieldVerificationTask createForWorkflow(Long workflowId){
  FieldVerificationTask existing=taskMapper.selectOne(new QueryWrapper<FieldVerificationTask>().eq("workflow_id",workflowId).last("LIMIT 1")); if(existing!=null)return existing;
  Workflow workflow=requireWorkflow(workflowId); List<WorkflowDevice> devices=workflowDeviceMapper.selectList(new QueryWrapper<WorkflowDevice>().eq("workflow_id",workflowId));
  if(devices.isEmpty())throw BizException.badRequest("workflow has no device snapshot");
  FieldVerificationTask task=new FieldVerificationTask(); task.setTaskNo("FV"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
  task.setWorkflowId(workflowId);task.setAccessToken(randomToken());task.setStatus("PENDING");taskMapper.insert(task);
  for(WorkflowDevice wd:devices){FieldVerificationDevice d=new FieldVerificationDevice();d.setVerificationTaskId(task.getId());d.setWorkflowDeviceId(wd.getId());d.setPlatformDeviceId(wd.getPlatformDeviceId());d.setDeviceNameSnapshot(wd.getDeviceNameSnapshot());d.setEnergyType(wd.getEnergyTypeSnapshot());d.setProductNameSnapshot(wd.getProductNameSnapshot());d.setMeterPhase(wd.getMeterPhaseSnapshot());d.setExpectedCommAddress(wd.getCommAddressSnapshot());d.setExpectedSerialNumber(wd.getSerialNumberSnapshot());d.setReadingUnit("WATER".equals(wd.getEnergyTypeSnapshot())?"m³":"kWh");deviceMapper.insert(d);} return task;
 }

 @Transactional public VerificationTaskView open(String token){FieldVerificationTask task=requireToken(token);if("PENDING".equals(task.getStatus())){task.setStatus("OPENED");task.setOpenedAt(LocalDateTime.now());taskMapper.updateById(task);}return view(task,false);}
 @Transactional public VerificationTaskView start(String token){FieldVerificationTask task=requireToken(token);if("COMPLETED".equals(task.getStatus())||"FAILED".equals(task.getStatus()))throw BizException.badRequest("verification task already finished");
  Workflow workflow=requireWorkflow(task.getWorkflowId());if("FIELD_CHECK_PENDING".equals(workflow.getStatus())){stateMachine.requireTransition(workflow.getStatus(),"FIELD_CHECKING");workflow.setStatus("FIELD_CHECKING");workflowMapper.updateById(workflow);}
  if(!"IN_PROGRESS".equals(task.getStatus())){task.setStatus("IN_PROGRESS");if(task.getOpenedAt()==null)task.setOpenedAt(LocalDateTime.now());task.setStartedAt(LocalDateTime.now());taskMapper.updateById(task);}return view(task,false);}
 @Transactional public VerificationTaskView updateDevice(String token,Long deviceId,VerificationDeviceUpdateRequest req){FieldVerificationTask task=requireToken(token);if(!"IN_PROGRESS".equals(task.getStatus()))throw BizException.badRequest("verification task must be started first");
  FieldVerificationDevice d=deviceMapper.selectById(deviceId);if(d==null||!task.getId().equals(d.getVerificationTaskId()))throw BizException.notFound("verification device not found");
  if("THREE_PHASE".equals(d.getMeterPhase())&&(req.getOnsiteMultiplier()==null||req.getOnsiteMultiplier().signum()<=0))throw BizException.badRequest("三相电表必须填写大于0的倍率");
  d.setOnsiteMeterNumber(null);d.setOnsiteSerialNumber(null);d.setOnsiteReading(req.getOnsiteReading());d.setOnsiteMultiplier("THREE_PHASE".equals(d.getMeterPhase())?req.getOnsiteMultiplier():null);d.setDeviceMatched(true);d.setSerialMatched(true);d.setAddressMatched(true);d.setVerificationResult("MATCHED");d.setVerifiedAt(LocalDateTime.now());d.setRemark(trim(req.getRemark()));deviceMapper.updateById(d);return view(task,false);}
 @Transactional public VerificationTaskView complete(String token,String remark){FieldVerificationTask task=requireToken(token);if(!"IN_PROGRESS".equals(task.getStatus()))throw BizException.badRequest("verification task must be in progress");List<FieldVerificationDevice> devices=devices(task.getId());
  if(devices.isEmpty()||devices.stream().anyMatch(d->d.getVerificationResult()==null||d.getOnsiteReading()==null))throw BizException.badRequest("所有设备都必须填写当前读数");
  if(devices.stream().anyMatch(d->"THREE_PHASE".equals(d.getMeterPhase())&&(d.getOnsiteMultiplier()==null||d.getOnsiteMultiplier().signum()<=0)))throw BizException.badRequest("所有三相电表都必须填写大于0的倍率");
  for(FieldVerificationDevice d:devices){long face=photoCount(d,"METER_FACE");long serial=photoCount(d,"SERIAL_NUMBER");if(face<1)throw BizException.badRequest("设备“"+d.getDeviceNameSnapshot()+"”需要上传清晰的表盘读数照片");if("ELECTRIC".equals(d.getEnergyType())&&!"THREE_PHASE".equals(d.getMeterPhase())&&serial<1)throw BizException.badRequest("单相电表“"+d.getDeviceNameSnapshot()+"”还需要上传电表号照片");}
  boolean mismatch=false;
  task.setStatus(mismatch?"FAILED":"COMPLETED");task.setVerificationResult(mismatch?"MISMATCH":"MATCHED");task.setCompletedAt(LocalDateTime.now());task.setRemark(trim(remark));taskMapper.updateById(task);
  Workflow workflow=requireWorkflow(task.getWorkflowId());String next=mismatch?"FIELD_EXCEPTION":"FIELD_VERIFIED";stateMachine.requireTransition(workflow.getStatus(),next);workflow.setStatus(next);workflow.setFieldVerifiedAt(LocalDateTime.now());if(!mismatch){stateMachine.requireTransition("FIELD_VERIFIED","WAITING_PLATFORM_CHANGE");workflow.setStatus("WAITING_PLATFORM_CHANGE");workflow.setWaitingPlatformAt(LocalDateTime.now());}workflowMapper.updateById(workflow);return view(task,false);}
 public VerificationTaskView getByWorkflow(Long workflowId){FieldVerificationTask task=taskMapper.selectOne(new QueryWrapper<FieldVerificationTask>().eq("workflow_id",workflowId).last("LIMIT 1"));return task==null?null:view(task,true);}
 private FieldVerificationTask requireToken(String token){if(token==null||token.length()<32)throw BizException.notFound("verification task not found");FieldVerificationTask task=taskMapper.selectOne(new QueryWrapper<FieldVerificationTask>().eq("access_token",token).last("LIMIT 1"));if(task==null)throw BizException.notFound("verification task not found");return task;}
 private Workflow requireWorkflow(Long id){Workflow w=workflowMapper.selectById(id);if(w==null)throw BizException.notFound("workflow not found");return w;}
 private List<FieldVerificationDevice> devices(Long taskId){return deviceMapper.selectList(new QueryWrapper<FieldVerificationDevice>().eq("verification_task_id",taskId).orderByAsc("id"));}
 private long photoCount(FieldVerificationDevice d,String type){return attachmentMapper.selectCount(new QueryWrapper<Attachment>().eq("business_type","VERIFICATION_DEVICE").eq("business_id",d.getId()).eq("photo_type",type));}
 private VerificationTaskView view(FieldVerificationTask t,boolean includeToken){Workflow w=requireWorkflow(t.getWorkflowId());Stall s=stallMapper.selectById(w.getStallId());VerificationTaskView v=new VerificationTaskView();v.setId(t.getId());v.setTaskNo(t.getTaskNo());v.setWorkflowId(w.getId());v.setWorkflowNo(w.getWorkflowNo());v.setWorkflowType(w.getWorkflowType());v.setMerchantCode(w.getMerchantCode());String merchantName=MerchantNameParser.extract(w.getMerchantName(),w.getMerchantCode());v.setMerchantName(merchantName==null?w.getMerchantName():merchantName);if(s!=null){v.setStallCode(s.getStallCode());v.setStallName(s.getStallName());}v.setStatus(t.getStatus());if(includeToken)v.setAccessToken(t.getAccessToken());v.setOpenedAt(t.getOpenedAt());v.setStartedAt(t.getStartedAt());v.setCompletedAt(t.getCompletedAt());v.setVerificationResult(t.getVerificationResult());v.setRemark(t.getRemark());v.setDevices(devices(t.getId()));return v;}
 private String randomToken(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);} private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
}
