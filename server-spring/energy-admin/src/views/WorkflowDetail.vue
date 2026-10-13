<template>
  <div class="page" v-loading="loading">
    <div class="page-header"><div class="page-title">流程详情</div><div><el-button v-if="canOperate&&row.status==='DRAFT'" type="primary" :loading="acting" @click="submit">提交申请</el-button><el-button v-if="canOperate&&cancelable" type="danger" plain :loading="acting" @click="cancel">取消流程</el-button><el-button v-if="isSuperAdmin" type="warning" plain :loading="acting" @click="resetWorkflow">复位流程</el-button><el-button v-if="isSuperAdmin" type="danger" :loading="acting" @click="deleteWorkflow">永久删除</el-button><el-button @click="router.back()">返回</el-button></div></div>
    <el-alert v-if="row.status==='DRAFT'" title="草稿尚未进入现场核验；提交时系统会重新检查铺位设备与计费账户。" type="warning" show-icon :closable="false" />
    <el-card style="margin-top:12px"><template #header>基本信息</template>
      <el-descriptions :column="3" border><el-descriptions-item label="流程编号">{{row.workflowNo}}</el-descriptions-item><el-descriptions-item label="业务类型">{{typeLabel(row.workflowType)}}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="statusType(row.status)">{{statusLabel(row.status)}}</el-tag></el-descriptions-item>
        <el-descriptions-item label="商户">{{row.merchantCode}} - {{merchantDisplayName}}</el-descriptions-item><el-descriptions-item label="铺位">{{row.stallCode}} - {{row.stallName}}</el-descriptions-item><el-descriptions-item :label="row.workflowType==='MERCHANT_EXIT'?'计划解绑时间':'计划启用时间'">{{formatDateTime(row.requestedBillingStartTime)}}</el-descriptions-item>
        <el-descriptions-item v-if="!isExit" label="标准账户名称" :span="3">{{standardAccountName||'商户代码或名称不完整'}}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{row.contactName}}</el-descriptions-item><el-descriptions-item label="联系电话">{{row.contactPhone}}</el-descriptions-item><el-descriptions-item v-if="row.originalContactPhone!=null" label="原系统手机号">{{row.originalContactPhone||'未登记'}}</el-descriptions-item><el-descriptions-item v-if="row.updateSmsPhone!=null" label="短信通知号码">{{row.updateSmsPhone?'申请修改为 '+row.contactPhone:'保留原系统号码'}}</el-descriptions-item><el-descriptions-item label="申请人">{{row.applicantName}}</el-descriptions-item><el-descriptions-item label="备注" :span="3">{{row.remark||'-'}}</el-descriptions-item></el-descriptions>
    </el-card>
    <el-card class="platform-info-card" style="margin-top:12px"><template #header><div class="oa-head"><span>OA 流程与完结信息</span><el-tag v-if="canEditPlatformInfo" type="warning">管理员维护</el-tag></div></template>
      <el-form label-width="110px" :label-position="isMobile?'top':'right'">
        <el-form-item label="OA流程编号"><div class="platform-info-input"><el-input v-model="row.oaProcessNo" :disabled="!canEditPlatformInfo" maxlength="100" placeholder="尚未填写" clearable/><el-button v-if="canEditPlatformInfo" type="primary" :loading="savingPlatformInfo==='oa'" @click="saveOaProcessNo">保存编号</el-button></div></el-form-item>
        <el-form-item label="完结备注"><div class="platform-info-input"><el-input v-model="row.completionRemark" :disabled="!canEditPlatformInfo" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="用于填写流程完结后的账户登录信息，可稍后单独补充"/><el-button v-if="canEditPlatformInfo" type="primary" :loading="savingPlatformInfo==='remark'" @click="saveCompletionRemark">保存备注</el-button></div></el-form-item>
      </el-form>
      <el-alert v-if="!row.oaProcessNo&&!row.completionRemark&&!canEditPlatformInfo" title="管理员尚未填写 OA 流程编号和完结备注" type="info" :closable="false" show-icon/>
    </el-card>
    <el-card style="margin-top:12px"><template #header>设备快照</template><el-table :data="row.devices||[]" border>
      <el-table-column prop="deviceNameSnapshot" label="设备名称" min-width="160" /><el-table-column prop="energyTypeSnapshot" label="能源" width="90"><template #default="{row:r}">{{enumLabel(r.energyTypeSnapshot)}}</template></el-table-column><el-table-column prop="platformDeviceId" label="平台设备ID" min-width="160" /><el-table-column prop="commAddressSnapshot" label="通讯地址" min-width="140" /><el-table-column prop="serialNumberSnapshot" label="序列号" min-width="140" /><el-table-column prop="oldAccountNumber" label="原账户" min-width="140"><template #default="{row:r}">{{r.oldAccountNumber||'未绑定'}}</template></el-table-column><el-table-column prop="operationType" label="预期操作" width="100"><template #default="{row:r}">{{enumLabel(r.operationType)}}</template></el-table-column>
    </el-table></el-card>
    <el-alert v-if="row.expectedAccountAction==='UNBIND_AND_DISABLE'" title="该铺位全部设备解绑后，商户账户将没有任何绑定设备；平台处理时需要同时停用该账户。" type="warning" show-icon :closable="false" style="margin-top:12px" />
    <el-card v-if="canOa&&verification?.status==='COMPLETED'" class="oa-card" style="margin-top:12px">
      <template #header><div class="oa-head"><span>OA 提交资料</span><el-tag type="success">现场核验已完成</el-tag></div></template>
      <div class="oa-comment"><div class="oa-comment-head"><b>申请批注</b><el-button link type="primary" @click="copyOaComment">复制批注</el-button></div><div>{{oaComment}}</div></div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="OA业务类型">{{isExit?'已有商户撤柜':'商户入驻/增加铺位'}}</el-descriptions-item>
        <el-descriptions-item label="铺位">{{row.stallCode}} - {{row.stallName}}</el-descriptions-item>
        <el-descriptions-item v-if="!isExit" label="供应商编码">{{row.merchantCode||'-'}}</el-descriptions-item>
        <el-descriptions-item v-if="!isExit" label="供应商联系方式">{{row.contactName||'-'}} / {{row.contactPhone||'-'}}</el-descriptions-item>
        <el-descriptions-item v-if="!isExit" label="楼层">{{row.floorName||row.floorCode||'未维护'}}</el-descriptions-item>
        <el-descriptions-item :label="isExit?'停用日期':'启用日期'">{{formatDateTime(row.requestedBillingStartTime)}}</el-descriptions-item>
      </el-descriptions>
      <el-alert title="实际读数按“表盘读数 × 倍率”计算；单相电表和水表按 1 倍计算。" type="info" :closable="false" show-icon style="margin-top:14px" />
      <el-table :data="verification.devices||[]" border style="margin-top:14px">
        <el-table-column prop="deviceNameSnapshot" label="设备名称" min-width="150" />
        <el-table-column label="类型" width="80"><template #default="{row:d}">{{d.energyType==='WATER'?'水表':'电表'}}</template></el-table-column>
        <el-table-column label="序列号/表号（自动识别）" min-width="190"><template #default="{row:d}">{{oaDeviceNumber(d)}}</template></el-table-column>
        <el-table-column label="倍率" width="90"><template #default="{row:d}">{{oaMultiplier(d)}}</template></el-table-column>
        <el-table-column :label="isExit?'表盘停用读数':'表盘启用读数'" width="150"><template #default="{row:d}">{{d.onsiteReading??'未填写'}} {{d.readingUnit||''}}</template></el-table-column>
        <el-table-column :label="isExit?'实际停用读数':'实际启用读数'" width="150"><template #default="{row:d}">{{oaActualReading(d)}} {{d.onsiteReading==null?'':(d.readingUnit||'')}}</template></el-table-column>
        <el-table-column label="核查照片" min-width="240"><template #default="{row:d}"><div class="oa-photos"><span v-if="!devicePhotos(d.id).length">未上传</span><span v-for="(p,index) in devicePhotos(d.id)" :key="p.id" class="oa-photo"><button type="button" class="oa-photo-preview" @click="openPhotoPreview(d,index)"><el-image :src="p.url" fit="cover" loading="lazy" /><span>预览</span></button><button type="button" class="oa-photo-download" :disabled="downloadingPhotoId===p.id" @click="downloadPhoto(d,p,index)">{{downloadingPhotoId===p.id?'下载中…':'下载'}}</button></span></div></template></el-table-column>
      </el-table>
    </el-card>
    <el-card style="margin-top:12px"><template #header>预期变化（与平台实际状态独立保存）</template><el-table :data="row.expectedChanges||[]" border>
      <el-table-column prop="changeType" label="变化类型" width="130"><template #default="{row:r}">{{enumLabel(r.changeType)}}</template></el-table-column><el-table-column prop="platformDeviceId" label="设备ID" min-width="160"><template #default="{row:r}">{{r.platformDeviceId||'不涉及设备'}}</template></el-table-column><el-table-column prop="fromAccountNumber" label="原账户" min-width="130"><template #default="{row:r}">{{r.fromAccountNumber||'未绑定'}}</template></el-table-column><el-table-column prop="toAccountNumber" label="目标账户" min-width="150"><template #default="{row:r}">{{r.changeType==='ACCOUNT_DISABLE'?'无需目标账户':(r.toAccountNumber||'平台创建后按商户代码识别')}}</template></el-table-column><el-table-column prop="verifyStatus" label="核对状态" width="110"><template #default="{row:r}">{{enumLabel(r.verifyStatus)}}</template></el-table-column><el-table-column prop="actualAccountNumber" label="平台实际账户" min-width="150"><template #default="{row:r}">{{r.changeType==='ACCOUNT_DISABLE'?(r.verifyStatus==='MATCHED'?'账户已停用':'账户仍启用'):(r.actualAccountNumber||'尚未匹配')}}</template></el-table-column>
    </el-table></el-card>
    <el-card v-if="evidence.runs?.length||resolvable" style="margin-top:12px"><template #header><div class="match-head"><span>平台匹配证据与处置</span><div v-if="canResolve&&resolvable"><el-button type="warning" plain @click="retryMatch">等待下次同步</el-button><el-button type="danger" plain @click="closeException">填写结论并关闭</el-button></div></div></template>
      <el-alert v-if="resolvable" title="旧匹配证据不会被覆盖；重新等待后，下次同步会生成新的匹配记录。" type="warning" :closable="false" show-icon />
      <el-collapse style="margin-top:12px"><el-collapse-item v-for="run in evidence.runs||[]" :key="run.id" :title="`同步批次 #${run.sync_batch_id}　${matchLabel(run.match_status)}　${run.summary}`"><el-table :data="run.details" border size="small"><el-table-column prop="platform_device_id" label="设备ID" min-width="150"/><el-table-column prop="expected_account_number" label="目标账户" min-width="140"/><el-table-column prop="actual_account_numbers" label="实际账户" min-width="140"><template #default="{row:r}">{{r.actual_account_numbers||'未绑定'}}</template></el-table-column><el-table-column label="结果" width="130"><template #default="{row:r}">{{matchLabel(r.match_status)}}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="200"/></el-table></el-collapse-item></el-collapse>
      <el-divider v-if="evidence.resolutions?.length">人工处置历史</el-divider><div v-for="(r,i) in evidence.resolutions||[]" :key="i" class="resolution"><b>{{r.operator_name}}</b>：{{r.action==='RETRY'?'重新等待同步':'关闭异常'}}（{{statusLabel(r.from_status)}} → {{statusLabel(r.to_status)}}）　{{r.conclusion||''}}　{{formatDateTime(r.created_at)}}</div>
    </el-card>
    <el-dialog v-model="photoPreviewVisible" class="workflow-photo-dialog" :fullscreen="isMobile" width="min(1100px, 94vw)" append-to-body destroy-on-close @closed="resetPhotoPreview">
      <template #header><div class="photo-dialog-title"><b>{{previewDeviceName}}</b><span v-if="previewPhotos.length">{{previewPhotoIndex+1}} / {{previewPhotos.length}}</span></div></template>
      <div class="photo-preview-stage">
        <el-button v-if="previewPhotos.length>1" class="photo-nav photo-nav-prev" circle aria-label="上一张" @click="previousPhoto">‹</el-button>
        <img v-if="currentPreviewPhoto" :src="currentPreviewPhoto.url" :alt="currentPreviewPhoto.fileName||'核查照片'" class="photo-preview-image" :style="previewImageStyle" />
        <el-button v-if="previewPhotos.length>1" class="photo-nav photo-nav-next" circle aria-label="下一张" @click="nextPhoto">›</el-button>
      </div>
      <div class="photo-preview-toolbar">
        <el-button @click="changePreviewScale(-0.25)">缩小</el-button><span>{{Math.round(photoPreviewScale*100)}}%</span><el-button @click="changePreviewScale(0.25)">放大</el-button>
        <el-button @click="rotatePreview(-90)">左转</el-button><el-button @click="rotatePreview(90)">右转</el-button><el-button @click="resetPhotoTransform">复位</el-button>
      </div>
      <div v-if="previewPhotos.length>1" class="photo-preview-thumbnails"><button v-for="(p,index) in previewPhotos" :key="p.id" type="button" :class="['photo-preview-thumb',{active:index===previewPhotoIndex}]" @click="selectPreviewPhoto(index)"><img :src="p.url" :alt="`第${index+1}张核查照片`" loading="lazy" /></button></div>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'; import { useRoute,useRouter } from 'vue-router'; import { ElMessage,ElMessageBox } from 'element-plus'; import { workflowApi } from '@/api/workflow'
import { enumLabel, formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { verificationApi } from '@/api/verification'
import { buildStandardAccountName, extractMerchantName } from '@/utils/accountName'
const route=useRoute(),router=useRouter(),row=ref<any>({}),evidence=ref<any>({runs:[],resolutions:[]}),loading=ref(false),acting=ref(false),savingPlatformInfo=ref<'oa'|'remark'|''>(''),isMobile=ref(window.innerWidth<=768); const labels:Record<string,string>={DRAFT:'草稿',FIELD_CHECK_PENDING:'待现场核验',FIELD_CHECKING:'核验中',FIELD_VERIFIED:'现场核验通过',FIELD_EXCEPTION:'现场异常',WAITING_OA_SUBMISSION:'待OA提交',WAITING_PLATFORM_CHANGE:'等待平台处理',PLATFORM_VERIFYING:'平台核对中',VERIFIED:'平台验证通过',PARTIAL_MATCH:'部分匹配',EXCEPTION:'异常',RESOLVED_EXCEPTION:'异常已关闭',CANCELLED:'已取消',COMPLETED:'已完成'}
const userStore=useUserStore();const canOperate=computed(()=>userStore.user?.roles?.some(r=>['SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT'].includes(r)));const canViewEvidence=computed(()=>userStore.user?.roles?.some(r=>['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'].includes(r)));const canResolve=computed(()=>userStore.user?.roles?.some(r=>['SUPER_ADMIN','SYSTEM_ADMIN'].includes(r)))
const isSuperAdmin=computed(()=>userStore.user?.roles?.includes('SUPER_ADMIN'))
const canEditPlatformInfo=computed(()=>canResolve.value&&['WAITING_OA_SUBMISSION','WAITING_PLATFORM_CHANGE','PLATFORM_VERIFYING','PARTIAL_MATCH','EXCEPTION','VERIFIED','COMPLETED','RESOLVED_EXCEPTION'].includes(row.value.status))
const canOa=canResolve,isExit=computed(()=>row.value.workflowType==='MERCHANT_EXIT'),verification=ref<any>(null),oaPhotos=ref<Record<number,any[]>>({})
const photoPreviewVisible=ref(false),previewPhotos=ref<any[]>([]),previewPhotoIndex=ref(0),previewDeviceName=ref('核查照片'),previewDevice=ref<any>(null),photoPreviewScale=ref(1),photoPreviewRotation=ref(0),downloadingPhotoId=ref<number|string|null>(null)
const currentPreviewPhoto=computed(()=>previewPhotos.value[previewPhotoIndex.value]||null)
const previewImageStyle=computed(()=>({transform:`scale(${photoPreviewScale.value}) rotate(${photoPreviewRotation.value}deg)`}))
const cancelable=computed(()=>['DRAFT','FIELD_CHECK_PENDING','FIELD_CHECKING','WAITING_OA_SUBMISSION','WAITING_PLATFORM_CHANGE'].includes(row.value.status)); function statusLabel(s:string){return labels[s]||s} function statusType(s:string){return ['COMPLETED','VERIFIED'].includes(s)?'success':['EXCEPTION','FIELD_EXCEPTION'].includes(s)?'danger':['DRAFT','CANCELLED'].includes(s)?'info':'warning'}
function typeLabel(t:string){return ({NEW_MERCHANT:'新商户开户',EXISTING_MERCHANT_ADD_STALL:'已有商户增加铺位',MERCHANT_EXIT:'已有商户撤柜'} as Record<string,string>)[t]||t}
const resolvable=computed(()=>['PARTIAL_MATCH','EXCEPTION'].includes(row.value.status));function matchLabel(s:string){return ({MATCHED:'完全匹配',PARTIAL_MATCH:'部分匹配',MISMATCH:'不一致',PENDING:'尚未变更',ACCOUNT_NOT_FOUND:'未找到目标账户',ACCOUNT_AMBIGUOUS:'目标账户不唯一'} as any)[s]||s}
async function load(){loading.value=true;try{row.value=await workflowApi.get(Number(route.params.id));evidence.value=canViewEvidence.value?await workflowApi.matchEvidence(row.value.id):{runs:[],resolutions:[]};if(canOa.value){verification.value=await verificationApi.byWorkflow(row.value.id).catch(()=>null);if(verification.value?.status==='COMPLETED'&&verification.value.accessToken){for(const d of verification.value.devices||[]){const photos:any=await verificationApi.attachments(verification.value.accessToken,d.id).catch(()=>[]);oaPhotos.value[d.id]=(photos as any[]).map((p:any)=>({...p,url:`/api/v1/verification/tasks/${verification.value.accessToken}/attachments/${p.id}`}))}}}}finally{loading.value=false}} async function submit(){await ElMessageBox.confirm('提交后将进入现场核验，确认继续？','提交申请');acting.value=true;try{row.value=await workflowApi.submit(row.value.id);ElMessage.success('已提交，等待现场核验')}finally{acting.value=false}} async function cancel(){await ElMessageBox.confirm('确认取消该流程？','取消流程',{type:'warning'});acting.value=true;try{row.value=await workflowApi.cancel(row.value.id);ElMessage.success('流程已取消')}finally{acting.value=false}}
function devicePhotos(id:number){return oaPhotos.value[id]||[]}
function safeFilePart(value:unknown){return String(value||'').replace(/[\\/:*?"<>|]/g,'-').replace(/\s+/g,' ').trim()||'未命名商户'}
function photoDeviceType(device:any){if(device?.energyType==='WATER')return '水表';if(device?.energyType==='ELECTRIC')return device?.meterPhase==='THREE_PHASE'?'三相电表':'电表';return '设备'}
function photoDownloadName(device:any,photo:any,index:number){const original=String(photo?.fileName||''),match=/\.(jpe?g|png|webp|heic)$/i.exec(original),extension=match?.[0].toLowerCase()||'.jpg';return `${safeFilePart(merchantDisplayName.value)}-${photoDeviceType(device)}-${index+1}${extension}`}
async function downloadPhoto(device:any,photo:any,index:number){downloadingPhotoId.value=photo.id;try{const response=await fetch(photo.url,{credentials:'same-origin'});if(!response.ok)throw new Error(`HTTP ${response.status}`);const blob=await response.blob(),url=URL.createObjectURL(blob),anchor=document.createElement('a');anchor.href=url;anchor.download=photoDownloadName(device,photo,index);document.body.appendChild(anchor);anchor.click();anchor.remove();setTimeout(()=>URL.revokeObjectURL(url),1000)}catch{ElMessage.error('照片下载失败，请稍后重试')}finally{downloadingPhotoId.value=null}}
function resetPhotoTransform(){photoPreviewScale.value=1;photoPreviewRotation.value=0}
function openPhotoPreview(device:any,index:number){previewDevice.value=device;previewPhotos.value=devicePhotos(device.id);previewPhotoIndex.value=index;previewDeviceName.value=device.deviceNameSnapshot||device.platformDeviceId||'核查照片';resetPhotoTransform();photoPreviewVisible.value=true}
function selectPreviewPhoto(index:number){previewPhotoIndex.value=index;resetPhotoTransform()}
function previousPhoto(){selectPreviewPhoto((previewPhotoIndex.value-1+previewPhotos.value.length)%previewPhotos.value.length)}
function nextPhoto(){selectPreviewPhoto((previewPhotoIndex.value+1)%previewPhotos.value.length)}
function changePreviewScale(delta:number){photoPreviewScale.value=Math.min(3,Math.max(.5,Number((photoPreviewScale.value+delta).toFixed(2))))}
function rotatePreview(delta:number){photoPreviewRotation.value=(photoPreviewRotation.value+delta)%360}
function resetPhotoPreview(){previewDevice.value=null;previewPhotos.value=[];previewPhotoIndex.value=0;resetPhotoTransform()}
function oaDeviceNumber(d:any){
  if(d.energyType==='WATER')return d.expectedSerialNumber||d.onsiteSerialNumber||d.onsiteMeterNumber||d.expectedCommAddress||'未记录'
  return d.expectedCommAddress||d.onsiteMeterNumber||d.expectedSerialNumber||d.onsiteSerialNumber||'未记录'
}
function oaMultiplier(d:any){
  if(d.energyType!=='ELECTRIC')return '-'
  if(d.onsiteMultiplier!==null&&d.onsiteMultiplier!==undefined)return d.onsiteMultiplier
  return d.meterPhase==='THREE_PHASE'?'未填写':'1'
}
function oaActualReading(d:any){
  if(d.onsiteReading===null||d.onsiteReading===undefined)return '未填写'
  const reading=Number(d.onsiteReading)
  const multiplier=d.energyType==='ELECTRIC'&&d.meterPhase==='THREE_PHASE'?Number(d.onsiteMultiplier):1
  if(!Number.isFinite(reading))return '读数无效'
  if(!Number.isFinite(multiplier)||multiplier<=0)return '倍率未填写'
  return Number((reading*multiplier).toFixed(7)).toString()
}
function deviceReference(d:any){
  if(d.energyType==='WATER'){
    if(d.expectedSerialNumber)return {type:'水表',label:'序列号',value:d.expectedSerialNumber}
    if(d.onsiteSerialNumber)return {type:'水表',label:'序列号',value:d.onsiteSerialNumber}
    if(d.onsiteMeterNumber)return {type:'水表',label:'表号',value:d.onsiteMeterNumber}
    if(d.expectedCommAddress)return {type:'水表',label:'通讯地址',value:d.expectedCommAddress}
    return {type:'水表',label:'编号',value:'未记录'}
  }
  if(d.energyType==='ELECTRIC'){
    if(d.expectedCommAddress)return {type:'电表',label:'通讯地址',value:d.expectedCommAddress}
    if(d.onsiteMeterNumber)return {type:'电表',label:'表号',value:d.onsiteMeterNumber}
    if(d.expectedSerialNumber)return {type:'电表',label:'序列号',value:d.expectedSerialNumber}
    if(d.onsiteSerialNumber)return {type:'电表',label:'序列号',value:d.onsiteSerialNumber}
    return {type:'电表',label:'编号',value:'未记录'}
  }
  return {type:'设备',label:'编号',value:d.expectedSerialNumber||d.expectedCommAddress||d.platformDeviceId||'未记录'}
}
function deviceDescription(d:any){const reference=deviceReference(d);return `${reference.type}（${reference.label}${reference.value}）`}
function exitMeterLabel(){const devices=verification.value?.devices?.length?verification.value.devices:(row.value.devices||[]).map((d:any)=>({energyType:d.energyTypeSnapshot})),types=new Set(devices.map((d:any)=>d.energyType));return types.has('WATER')&&types.has('ELECTRIC')?'水电表':types.has('WATER')?'水表':types.has('ELECTRIC')?'电表':'设备'}
const merchantDisplayName=computed(()=>extractMerchantName(row.value.merchantName||row.value.billingAccountName,row.value.merchantCode)||row.value.merchantName||row.value.merchantCode||'')
const standardAccountName=computed(()=>row.value.workflowType==='NEW_MERCHANT'
  ? buildStandardAccountName(row.value.merchantCode,row.value.merchantName)
  : (row.value.billingAccountName||buildStandardAccountName(row.value.merchantCode,row.value.merchantName)))
// 撤柜场景严格以铺位管理中的业态编码判断，避免铺位或商户名称中含“仓库”造成误判。
function isWarehouse(){return String(row.value.stallBusinessType||'').trim().toUpperCase()==='CK'}
const baseOaComment=computed(()=>{const location=`${row.value.floorName||row.value.floorCode||''}${row.value.stallCode||''}`,oldAccount=row.value.devices?.find((d:any)=>d.oldAccountNumber)?.oldAccountNumber;if(isExit.value){const warehouse=isWarehouse(),name=merchantDisplayName.value.replace(warehouse?/仓库$/:'',''),account=standardAccountName.value||row.value.billingAccountName||oldAccount||'原计费',disableAccount=row.value.expectedAccountAction==='UNBIND_AND_DISABLE'?'停用该账户。':'';return warehouse?`${row.value.stallCode||''} 商户${name}仓库撤柜，${exitMeterLabel()}需从${account}账户中移除。${disableAccount}`:`${location}商户${name}撤柜，${exitMeterLabel()}需从${account}账户中移除。${disableAccount}`}if(row.value.workflowType==='NEW_MERCHANT')return `商户${standardAccountName.value} ${location}，该铺位商户入驻，需要开设账户。`;const devices=(verification.value?.devices||[]).map(deviceDescription).join('、')||'核验设备',account=standardAccountName.value||row.value.billingAccountName||row.value.targetAccountNumber||'原有计费';return `商户${account} ${location}新增铺位入驻，需将${devices}添加至${account}账户中。`})
const oaComment=computed(()=>baseOaComment.value+(row.value.workflowType==='EXISTING_MERCHANT_ADD_STALL'&&row.value.updateSmsPhone===true&&row.value.contactPhone?` 需修改短信通知号码为${row.value.contactPhone}。`:''))
async function copyOaComment(){await navigator.clipboard.writeText(oaComment.value);ElMessage.success('申请批注已复制')}
async function saveOaProcessNo(){savingPlatformInfo.value='oa';try{row.value=await workflowApi.updateOaProcessNo(row.value.id,row.value.oaProcessNo||'');ElMessage.success('OA流程编号已保存')}finally{savingPlatformInfo.value=''}}
async function saveCompletionRemark(){savingPlatformInfo.value='remark';try{row.value=await workflowApi.updateCompletionRemark(row.value.id,row.value.completionRemark||'');ElMessage.success('完结备注已保存')}finally{savingPlatformInfo.value=''}}
async function resetWorkflow(){await ElMessageBox.confirm('复位后将永久清除现场核验、照片、平台匹配和推送记录，并把流程恢复为草稿。确认继续？','复位流程',{type:'warning',confirmButtonText:'确认复位'});acting.value=true;try{row.value=await workflowApi.reset(row.value.id);verification.value=null;oaPhotos.value={};evidence.value={runs:[],resolutions:[]};ElMessage.success('流程已复位，可重新提交')}finally{acting.value=false}}
async function deleteWorkflow(){const p=await ElMessageBox.prompt(`永久删除将清除流程 ${row.value.workflowNo} 及其核验、照片和全部关联记录，无法恢复。请输入流程编号确认：`,'永久删除流程',{type:'error',confirmButtonText:'永久删除',inputValidator:v=>v===row.value.workflowNo||'请输入正确的流程编号'});acting.value=true;try{await workflowApi.permanentDelete(row.value.id);ElMessage.success('流程及全部关联记录已永久删除');router.replace('/workflow')}finally{acting.value=false}}
async function retryMatch(){await ElMessageBox.confirm('确认等待下一次同步重新匹配？','重新匹配');await workflowApi.resolveMatch(row.value.id,{action:'RETRY'});ElMessage.success('已恢复等待状态');await load()}async function closeException(){const p=await ElMessageBox.prompt('请输入调查结果和关闭依据','关闭异常',{inputType:'textarea',inputValidator:v=>!!v?.trim()||'必须填写处理结论'});await workflowApi.resolveMatch(row.value.id,{action:'CLOSE_EXCEPTION',conclusion:p.value});ElMessage.success('异常已关闭');await load()}onMounted(load)
</script>
<style scoped>.match-head{display:flex;align-items:center;justify-content:space-between}.resolution{padding:8px 0;border-bottom:1px solid #ebeef5;color:#606266}.oa-head,.oa-comment-head{display:flex;align-items:center;justify-content:space-between}.oa-comment{padding:12px 14px;margin-bottom:14px;background:#f0f9eb;border:1px solid #c2e7b0;border-radius:6px;line-height:1.8}.oa-photos{display:flex;gap:10px;flex-wrap:wrap}.oa-photo{display:flex;flex-direction:column;align-items:stretch;border:1px solid #dcdfe6;border-radius:6px;background:#fff;overflow:hidden}.oa-photo:hover{border-color:#409eff}.oa-photo-preview,.oa-photo-download{padding:0;border:0;background:#fff;color:#409eff;cursor:pointer}.oa-photo-preview{display:flex;flex-direction:column;align-items:center;gap:4px}.oa-photo-preview .el-image{width:92px;height:68px}.oa-photo-preview span{padding-bottom:3px;font-size:12px}.oa-photo-download{min-height:30px;border-top:1px solid #ebeef5;font-size:13px}.oa-photo-download:hover{background:#ecf5ff}.oa-photo-download:disabled{color:#a8abb2;cursor:wait}.platform-info-input{display:flex;width:100%;gap:10px;align-items:flex-start}.platform-info-input .el-button{flex:none}.photo-dialog-title{display:flex;align-items:center;gap:16px;padding-right:28px}.photo-dialog-title span{font-size:13px;color:#909399}.photo-preview-stage{position:relative;display:flex;align-items:center;justify-content:center;height:min(66vh,720px);overflow:auto;background:#111;border-radius:8px}.photo-preview-image{display:block;max-width:100%;max-height:100%;object-fit:contain;transition:transform .18s ease}.photo-nav{position:absolute;z-index:2;top:50%;transform:translateY(-50%);width:44px;height:44px;border:0;background:rgba(0,0,0,.52);color:#fff;font-size:30px}.photo-nav-prev{left:12px}.photo-nav-next{right:12px}.photo-preview-toolbar{display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap;padding:14px 0}.photo-preview-toolbar span{min-width:48px;text-align:center;color:#606266}.photo-preview-thumbnails{display:flex;gap:8px;justify-content:center;overflow-x:auto;padding:2px 2px 8px}.photo-preview-thumb{flex:none;width:72px;height:54px;padding:2px;border:2px solid transparent;border-radius:5px;background:#fff;cursor:pointer}.photo-preview-thumb.active{border-color:#409eff}.photo-preview-thumb img{width:100%;height:100%;object-fit:cover;border-radius:2px}@media(max-width:768px){.oa-card :deep(.el-descriptions__body){overflow-x:auto}.oa-photo-preview .el-image{width:86px;height:64px}.platform-info-input{flex-direction:column}.platform-info-input .el-button{width:100%}.photo-dialog-title{gap:10px}.photo-preview-stage{height:calc(100dvh - 190px);min-height:280px;border-radius:0;margin:0 -16px}.photo-nav{width:40px;height:40px}.photo-nav-prev{left:6px}.photo-nav-next{right:6px}.photo-preview-toolbar{position:relative;z-index:3;gap:6px;padding:10px 0}.photo-preview-toolbar .el-button{margin-left:0;padding:7px 10px}.photo-preview-thumbnails{justify-content:flex-start}.photo-preview-thumb{width:64px;height:48px}}</style>

<style>.workflow-photo-dialog .el-dialog__body{padding-top:8px}.workflow-photo-dialog .el-dialog__header{margin-right:0}@media(max-width:768px){.workflow-photo-dialog.el-dialog{display:flex;flex-direction:column;height:100dvh;overflow:hidden}.workflow-photo-dialog .el-dialog__header{flex:none;padding:14px 16px 10px}.workflow-photo-dialog .el-dialog__body{display:flex;flex:1;min-height:0;flex-direction:column;padding:0 16px 10px;overflow:hidden}}</style>
