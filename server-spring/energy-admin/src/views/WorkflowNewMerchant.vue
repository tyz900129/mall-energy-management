<template>
  <div class="page workflow-form">
    <div class="page-header"><div class="page-title">水电费变更申请</div><el-button @click="router.back()">返回</el-button></div>
    <el-alert title="统一入口用于新商户开户、已有商户增加铺位和商户撤柜。系统将先核对铺位、设备和启用计费账户关系。" type="info" show-icon :closable="false" />
    <el-card class="form-card"><el-form ref="formRef" :model="form" :rules="rules" :label-position="isMobile?'top':'right'" :label-width="isMobile?'auto':'120px'">
      <el-form-item label="变更类型" prop="workflowType">
        <el-select v-if="isMobile" v-model="form.workflowType" class="full-control" @change="changeType"><el-option v-for="item in workflowTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-radio-group v-else v-model="form.workflowType" @change="changeType"><el-radio-button value="NEW_MERCHANT">新商户开户</el-radio-button><el-radio-button value="EXISTING_MERCHANT_ADD_STALL">已有商户增加铺位</el-radio-button><el-radio-button value="MERCHANT_EXIT">已有商户撤柜</el-radio-button></el-radio-group>
      </el-form-item>
      <el-row :gutter="16"><el-col :xs="24" :sm="12"><el-form-item label="商户代码" prop="merchantCode"><el-input v-model="form.merchantCode" maxlength="6" inputmode="numeric" pattern="[0-9]*" placeholder="请输入连续6位数字" clearable @input="invalidate()" /></el-form-item></el-col>
        <el-col v-if="form.workflowType==='NEW_MERCHANT'" :xs="24" :sm="12"><el-form-item label="商户名称" prop="merchantName"><el-input v-model="form.merchantName" maxlength="100" placeholder="请输入商户或品牌名称" @input="invalidate(false)" /></el-form-item></el-col>
        <el-col v-if="form.workflowType==='NEW_MERCHANT'" :xs="24" :sm="24"><el-form-item label="生成账户名称"><el-input :model-value="proposedAccountName" disabled placeholder="填写商户代码和商户名称后自动生成" /><div class="account-name-hint">统一格式：3066-六位商户代码-商户名称</div></el-form-item></el-col>
        <el-col v-if="form.workflowType==='NEW_MERCHANT'" :xs="24" :sm="12"><el-form-item label="商户联系人" prop="contactName" required><el-input v-model="form.contactName" placeholder="必填" /></el-form-item></el-col>
        <el-col v-if="form.workflowType!=='MERCHANT_EXIT'" :xs="24" :sm="12"><el-form-item label="联系人电话" prop="contactPhone" required><el-input v-model="form.contactPhone" inputmode="tel" maxlength="32" placeholder="请输入本次联系手机号" @input="invalidate(false)" /></el-form-item></el-col>
        <el-col v-if="form.workflowType==='EXISTING_MERCHANT_ADD_STALL'" :xs="24" :sm="24"><el-alert title="请填写联系人电话。执行变更检查后系统会立即提示号码一致或不一致，并由申请人确认是否修改短信通知号码。" type="info" :closable="false" show-icon /></el-col>
        <el-col :xs="24" :sm="12"><el-form-item :label="form.workflowType==='MERCHANT_EXIT'?'解除绑定时间':'启用时间'" prop="effectiveTime"><input v-if="isMobile" v-model="mobileDateTime" class="native-date" type="datetime-local" step="60" @change="onMobileDateTimeChange" /><el-date-picker v-else v-model="form.effectiveTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:00" style="width:100%" /></el-form-item></el-col></el-row>
      <el-form-item v-if="form.workflowType==='MERCHANT_EXIT'" label="已绑定铺位" prop="stallId"><el-select v-model="form.stallId" filterable style="width:100%" placeholder="请先输入商户代码并查询" @change="invalidate(false)"><el-option v-for="s in merchantStalls" :key="s.stallId" :label="`${s.stallCode}${s.stallName?' - '+s.stallName:''}（${s.deviceIds.length}台设备）`" :value="s.stallId" /></el-select></el-form-item>
      <el-form-item v-else label="铺位编码" prop="stallId"><el-select v-model="form.stallId" filterable style="width:100%" @change="onStallChange"><el-option v-for="s in stalls" :key="s.id" :label="s.stallCode" :value="s.id" /></el-select></el-form-item>
      <el-form-item v-if="form.workflowType==='NEW_MERCHANT' && selectedStall && !selectedStallIsWarehouse" label="铺位业态" prop="stallBusinessType"><el-select v-model="form.stallBusinessType" class="full-control" placeholder="请选择该铺位入驻后的业态" @change="invalidate(false)"><el-option v-for="item in businessTypes" :key="item.id" :label="item.itemLabel" :value="item.itemValue" /></el-select><div class="account-name-hint">新商户开户流程完成后，自动同步到铺位管理。</div></el-form-item>
      <el-alert v-if="form.workflowType==='NEW_MERCHANT' && selectedStallIsWarehouse" title="该铺位业态为仓库，无需设置业态。" type="info" :closable="false" show-icon />
      <el-form-item v-if="check?.hasWaterMeter && form.workflowType!=='MERCHANT_EXIT'" label="新商户用水" required><el-radio-group v-model="form.usesWater" @change="invalidate(false)"><el-radio :value="true">用水，水表进入核对流程</el-radio><el-radio :value="false">不用水，水表不纳入本次变更</el-radio></el-radio-group></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form><div class="actions"><el-button type="primary" plain :loading="checking" @click="precheck">{{ form.workflowType==='MERCHANT_EXIT'&&!form.stallId?'查询商户铺位':'执行变更检查' }}</el-button><el-button type="primary" :disabled="!check?.allowed" :loading="saving" @click="create">创建流程草稿</el-button></div></el-card>
    <el-card v-if="check" class="result-card"><template #header><div class="check-title"><span>检查结果</span><el-tag :type="check.allowed?'success':'danger'">{{ check.allowed?'可以创建流程':'暂不能创建' }}</el-tag></div></template>
      <div v-if="form.workflowType==='EXISTING_MERCHANT_ADD_STALL'" class="bound"><b>原系统手机号：</b>{{check.originalContactPhone||'未登记'}}<span v-if="form.contactPhone"> · {{check.originalContactPhone?(check.phoneDifferent?'与填写号码不一致':'与填写号码一致'):'原系统未登记，无法直接比对'}}</span><span> · <b>处理选择：</b>{{form.updateSmsPhone===true?'申请更换短信通知号码':form.updateSmsPhone===false?'保持原号码不变':'待申请人确认'}}</span></div>
      <div v-if="check.merchantStalls.length" class="bound"><b>该商户当前关联铺位：</b><el-tag v-for="s in check.merchantStalls" :key="s.stallId" class="stall-tag">{{s.stallCode}}</el-tag></div>
      <el-alert v-for="notice in check.notices||[]" :key="notice" :title="notice" type="warning" :closable="false" show-icon class="reason" />
      <el-alert v-for="reason in check.reasons" :key="reason" :title="reason" :type="check.requiresWaterConfirmation?'warning':'error'" :closable="false" show-icon class="reason" />
      <el-table :data="check.devices" border empty-text="没有设备数据"><el-table-column prop="deviceName" label="设备名称" min-width="150"><template #default="{row}">{{row.deviceName||row.platformDeviceId}}</template></el-table-column><el-table-column prop="energyType" label="能源" width="75"><template #default="{row}">{{enumLabel(row.energyType)}}</template></el-table-column><el-table-column prop="commAddress" label="通讯地址" min-width="110" /><el-table-column prop="serialNumber" label="序列号" min-width="130" /><el-table-column prop="channelName" label="通道名称" min-width="120" /><el-table-column label="当前启用计费账户" min-width="180"><template #default="{row}">{{row.currentAccountNames?.length?row.currentAccountNames.join(', '):row.currentAccountNumbers.length?row.currentAccountNumbers.join(', '):'未绑定'}}</template></el-table-column><el-table-column label="本次处理" width="110"><template #default="{row}"><el-tag :type="row.conflict?'danger':row.included?'success':'info'">{{row.conflict?'冲突':row.included?'纳入核对':'不纳入'}}</el-tag></template></el-table-column></el-table>
    </el-card>
  </div>
</template>
<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'; import { useRouter } from 'vue-router'; import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'; import { stallApi } from '@/api/merchant'; import { workflowApi, type BillingChangePrecheck } from '@/api/workflow'; import { dictionaryApi } from '@/api/system'; import { enumLabel } from '@/utils/format'; import { buildStandardAccountName } from '@/utils/accountName'
const router=useRouter(),formRef=ref<FormInstance>(),stalls=ref<any[]>([]),merchantStalls=ref<any[]>([]),check=ref<BillingChangePrecheck|null>(null),checking=ref(false),saving=ref(false),requestKey=ref(''),isMobile=ref(window.innerWidth<=768),mobileDateTime=ref('')
const businessTypes=ref<any[]>([])
const workflowTypes=[{value:'NEW_MERCHANT',label:'新商户开户'},{value:'EXISTING_MERCHANT_ADD_STALL',label:'已有商户增加铺位'},{value:'MERCHANT_EXIT',label:'已有商户撤柜'}]
const form=reactive({workflowType:'NEW_MERCHANT',merchantCode:'',merchantName:'',contactName:'',contactPhone:'',stallId:null as number|null,stallBusinessType:'',effectiveTime:'',usesWater:undefined as boolean|undefined,remark:'',originalContactPhone:'',updateSmsPhone:undefined as boolean|undefined})
const selectedStall=computed(()=>stalls.value.find((s:any)=>s.id===form.stallId)),selectedStallIsWarehouse=computed(()=>String(selectedStall.value?.businessType||'').trim().toUpperCase()==='CK')
const proposedAccountName=computed(()=>/^\d{6}$/.test(form.merchantCode)&&form.merchantName.trim()?buildStandardAccountName(form.merchantCode,form.merchantName):'')
const rules={merchantCode:[{required:true,message:'请输入商户代码'},{pattern:/^\d{6}$/,message:'必须为6位数字'}],merchantName:[{validator:(_:any,v:string,cb:any)=>form.workflowType==='NEW_MERCHANT'&&!v?.trim()?cb(new Error('请输入商户名称')):cb()}],contactName:[{validator:(_:any,v:string,cb:any)=>form.workflowType==='NEW_MERCHANT'&&!v?.trim()?cb(new Error('请输入商户联系人')):cb()}],contactPhone:[{validator:(_:any,v:string,cb:any)=>form.workflowType!=='MERCHANT_EXIT'&&!v?.trim()?cb(new Error('请输入联系人电话')):cb()}],stallId:[{required:true,message:'请选择铺位'}],stallBusinessType:[{validator:(_:any,v:string,cb:any)=>form.workflowType==='NEW_MERCHANT'&&selectedStall.value&&!selectedStallIsWarehouse.value&&!v?.trim()?cb(new Error('请选择铺位业态')):cb()}],effectiveTime:[{required:true,message:'请选择日期'}]}
function invalidate(clearStalls=true){form.updateSmsPhone=undefined;check.value=null;if(!saving.value)requestKey.value='';if(clearStalls&&form.workflowType==='MERCHANT_EXIT'){merchantStalls.value=[];form.stallId=null}}
function changeType(){form.contactPhone='';form.originalContactPhone='';form.updateSmsPhone=undefined;check.value=null;requestKey.value='';merchantStalls.value=[];form.stallId=null;form.stallBusinessType='';form.usesWater=undefined}
function onStallChange(){const stall=selectedStall.value;form.stallBusinessType=String(stall?.businessType||'').toUpperCase()==='CK'?'':String(stall?.businessType||'');invalidate(false)}
function onMobileDateTimeChange(){form.effectiveTime=mobileDateTime.value?(mobileDateTime.value.length===16?`${mobileDateTime.value}:00`:mobileDateTime.value):'';check.value=null;if(!saving.value)requestKey.value=''}
function onResize(){isMobile.value=window.innerWidth<=768}
async function confirmSmsPhone(latest:any){
  form.originalContactPhone=latest.originalContactPhone||''
  const comparison=!form.originalContactPhone?'原系统未登记短信通知号码，无法直接比对':latest.phoneDifferent?'联系电话与原账户短信通知号码不一致':'联系电话与原账户短信通知号码一致'
  const comparisonColor=!form.originalContactPhone||latest.phoneDifferent?'#e6a23c':'#67c23a'
  const message=h('div',{style:'line-height:1.8'},[
    h('p',{style:`margin:0 0 8px;font-weight:600;color:${comparisonColor}`},`比对结果：${comparison}`),
    h('p',{style:'margin:0'},`原账户短信通知号码：${form.originalContactPhone||'未登记'}`),
    h('p',{style:'margin:0 0 8px'},`本次填写联系电话：${form.contactPhone}`),
    h('p',{style:'margin:0'},'请确认：是否申请将短信通知号码更换为本次填写的联系电话？')
  ])
  try{
    await ElMessageBox.confirm(message,'短信通知号码确认',{confirmButtonText:'申请更换',cancelButtonText:'保持不变',distinguishCancelAndClose:true,closeOnClickModal:false,type:latest.phoneDifferent?'warning':'success'})
    form.updateSmsPhone=true
    return true
  }catch(action){
    if(action==='cancel'){form.updateSmsPhone=false;return true}
    form.updateSmsPhone=undefined
    return false
  }
}
async function precheck(){if(!/^\d{6}$/.test(form.merchantCode)){ElMessage.warning('请先填写6位商户代码');return}if(form.workflowType!=='MERCHANT_EXIT'&&!form.stallId){ElMessage.warning('请选择铺位编码');return}if(form.workflowType==='NEW_MERCHANT'&&selectedStall.value&&!selectedStallIsWarehouse.value&&!form.stallBusinessType){ElMessage.warning('请选择铺位业态');return}checking.value=true;try{const r:any=await workflowApi.precheckBillingChange(form);check.value=r;merchantStalls.value=r.merchantStalls||[];if(r.hasWaterMeter&&form.usesWater===undefined)ElMessage.warning('检测到水表，请确认新商户是否用水');if(form.workflowType==='EXISTING_MERCHANT_ADD_STALL'&&r.merchantExists&&form.contactPhone){await confirmSmsPhone(r)}}finally{checking.value=false}}
function newRequestKey(){return globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`}
async function create(){
  if(saving.value)return
  saving.value=true
  try{
    await formRef.value?.validate()
    if(!check.value?.allowed){ElMessage.warning('请先完成检查并处理提示事项');return}
    if(form.workflowType==='EXISTING_MERCHANT_ADD_STALL'){
      const latest:any=await workflowApi.precheckBillingChange(form)
      check.value=latest
      if(!latest.allowed){ElMessage.warning('检查结果发生变化，请处理提示后重试');return}
      const sourceChanged=(latest.originalContactPhone||'')!==form.originalContactPhone
      form.originalContactPhone=latest.originalContactPhone||''
      if(sourceChanged)form.updateSmsPhone=undefined
      if(form.updateSmsPhone===undefined&&!await confirmSmsPhone(latest))return
    }
    if(!requestKey.value)requestKey.value=newRequestKey()
    const r:any=await workflowApi.createBillingChange({...form,requestKey:requestKey.value})
    ElMessage.success('水电费变更流程草稿已创建')
    router.push(`/workflow/${r.id}`)
  }finally{saving.value=false}
}
onMounted(async()=>{window.addEventListener('resize',onResize);const [r,d]:any=await Promise.all([stallApi.page({pageNum:1,pageSize:500}),dictionaryApi.list('BUSINESS_TYPE')]);stalls.value=(r.records||[]).filter((s:any)=>s.status!=='DECOMMISSIONED');businessTypes.value=d||[]})
onBeforeUnmount(()=>window.removeEventListener('resize',onResize))
</script>
<style scoped>.workflow-form{max-width:1180px;margin:auto}.form-card,.result-card{margin-top:12px}.actions{text-align:right}.check-title{display:flex;justify-content:space-between}.bound{margin-bottom:12px}.stall-tag{margin-left:8px}.reason{margin-bottom:8px}.full-control{width:100%}.account-name-hint{color:#909399;font-size:12px;line-height:20px;margin-top:4px}.native-date{display:block;width:100%;height:40px;padding:0 12px;border:1px solid #dcdfe6;border-radius:4px;background:#fff;color:#303133;font:inherit;-webkit-appearance:none;appearance:none}.native-date:focus{outline:none;border-color:#409eff}
@media(max-width:768px){.workflow-form{width:100%;padding:8px}.workflow-form :deep(.el-card__body){padding:12px!important}.workflow-form :deep(.el-form-item){display:block;margin-bottom:18px}.workflow-form :deep(.el-form-item__label){display:block;width:100%!important;height:auto;line-height:22px;padding:0 0 7px;text-align:left}.workflow-form :deep(.el-form-item__content){display:block;width:100%;margin-left:0!important}.workflow-form :deep(.el-input),.workflow-form :deep(.el-select),.workflow-form :deep(.el-date-editor){width:100%!important}.workflow-form :deep(.el-input__wrapper),.workflow-form :deep(.el-select__wrapper){min-height:44px}.workflow-form :deep(.el-radio-group){display:flex;flex-direction:column;align-items:flex-start;gap:8px}.actions{display:flex;flex-direction:column;gap:10px}.actions .el-button{width:100%;min-height:44px;margin-left:0}.result-card :deep(.el-table){display:block;overflow-x:auto}.bound{line-height:34px}.stall-tag{margin-left:0;margin-right:6px}.page-header{align-items:center}.page-header .el-button{min-height:40px}}
</style>
