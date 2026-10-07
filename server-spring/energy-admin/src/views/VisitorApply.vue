<template>
<main class="visitor-page">
  <section class="visitor-card">
    <header>
      <div class="brand">万悦城</div>
      <h1>访客门禁申请</h1>
      <p>请如实填写资料，审批通过后方可通行</p>
    </header>

    <el-result v-if="done" icon="success" title="申请提交成功" :sub-title="`预约编号：${appointmentNo}`">
      <template #extra><el-button type="primary" @click="reset">继续申请</el-button></template>
    </el-result>

    <el-form v-else label-position="top" class="form">
      <el-form-item label="姓名" required>
        <el-input v-model="form.visitorName" maxlength="40" autocomplete="name"/>
      </el-form-item>
      <el-form-item label="电话" required>
        <el-input v-model="form.visitorPhone" maxlength="11" inputmode="tel" autocomplete="tel"/>
      </el-form-item>
      <el-form-item label="身份证号" required>
        <el-input v-model="form.idNumber" maxlength="18" inputmode="text" @input="idChanged"/>
      </el-form-item>
      <el-form-item label="性别">
        <el-input :model-value="genderText" disabled/>
      </el-form-item>

      <el-form-item label="到访单位类型" required>
        <el-radio-group v-model="form.affiliationType" @change="unitChanged">
          <el-radio-button value="STALL">店铺</el-radio-button>
          <el-radio-button value="PROPERTY">物业部</el-radio-button>
          <el-radio-button value="COMMERCIAL">商管部</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="到访单位" required>
        <el-select v-if="form.affiliationType==='STALL'" v-model="form.stallId" filterable remote :remote-method="searchStalls" :loading="stallLoading" placeholder="输入铺位号或店铺名称">
          <el-option v-for="s in stalls" :key="s.id" :label="stallLabel(s)" :value="s.id"/>
        </el-select>
        <el-input v-else :model-value="form.affiliationType==='PROPERTY'?'物业部':'商管部'" disabled/>
      </el-form-item>

      <div class="dates">
        <el-form-item label="开始日期"><el-input v-model="form.validFrom" disabled/></el-form-item>
        <el-form-item label="结束日期"><el-input v-model="form.validTo" disabled/></el-form-item>
      </div>

      <photo-field label="身份证正面" :file="files.ID_FRONT" @select="setFile('ID_FRONT',$event)"/>
      <photo-field label="身份证反面" :file="files.ID_BACK" @select="setFile('ID_BACK',$event)"/>

      <el-form-item label="人脸照片" required>
        <label class="upload-box face-upload">
          <input type="file" accept="image/jpeg,image/png" capture="user" @change="onFaceSelected"/>
          <span v-if="!files.FACE">拍摄或选择人脸照片</span>
          <span v-else>{{ files.FACE.name }}</span>
        </label>
        <div v-if="faceChecking" class="face-status checking">
          <el-icon class="spin"><Loading/></el-icon>
          <span>正在检测人脸质量...</span>
        </div>
        <div v-else-if="faceQuality.passed" class="face-status passed">
          <el-icon><CircleCheckFilled/></el-icon>
          <span>{{ faceQuality.message }}</span>
        </div>
        <div v-else-if="faceQuality.message" class="face-status failed">
          <el-icon><WarningFilled/></el-icon>
          <span>{{ faceQuality.message }}</span>
        </div>
        <div v-else class="face-hint">
          <el-icon><InfoFilled/></el-icon>
          <span>请上传清晰、正面、无遮挡的近期免冠照片，系统将自动检测人脸质量</span>
        </div>
      </el-form-item>

      <input v-model="form.website" class="trap" tabindex="-1" autocomplete="off"/>

      <el-checkbox v-model="agreed">本人确认资料真实，并同意用于门禁预约及通行管理</el-checkbox>

      <el-button class="submit" type="primary" size="large" :loading="submitting" @click="submit">提交申请</el-button>
    </el-form>
  </section>
</main>
</template>

<script setup lang="ts">
import {computed,defineComponent,h,ref,reactive} from 'vue'
import {ElMessage,ElFormItem} from 'element-plus'
import {Loading,CircleCheckFilled,WarningFilled,InfoFilled} from '@element-plus/icons-vue'
import {accessPublicApi} from '@/api/accessPublic'

const dateText=(d:Date)=>{
  const p=(n:number)=>String(n).padStart(2,'0')
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const initial=()=>{
  const a=new Date(),b=new Date(a)
  b.setFullYear(b.getFullYear()+1)
  return{
    visitorName:'',visitorPhone:'',idNumber:'',gender:'UNKNOWN',
    affiliationType:'STALL',stallId:null as number|null,
    validFrom:dateText(a),validTo:dateText(b),website:''
  }
}

const form=ref<any>(initial())
const files=ref<any>({})
const stalls=ref<any[]>([])
const stallLoading=ref(false)
const submitting=ref(false)
const agreed=ref(false)
const done=ref(false)
const appointmentNo=ref('')
const submissionToken=ref('')
const faceChecking=ref(false)
const faceQuality=reactive({passed:false,message:''})

const genderText=computed(()=>
  form.value.gender==='MALE'?'男':form.value.gender==='FEMALE'?'女':'输入身份证号后自动识别'
)

function idChanged(){
  const v=String(form.value.idNumber||'').toUpperCase()
  form.value.idNumber=v
  if(/^\d{17}[0-9X]$/.test(v))
    form.value.gender=Number(v[16])%2?'MALE':'FEMALE'
}

function unitChanged(){form.value.stallId=null}

async function searchStalls(k:string){
  if(!k.trim()){stalls.value=[];return}
  stallLoading.value=true
  try{stalls.value=await accessPublicApi.stalls(k) as unknown as any[]}
  finally{stallLoading.value=false}
}

const stallLabel=(s:any)=>
  `${s.floorName||s.floorCode||''} ${s.stallCode} ${s.currentMerchantName||s.stallName||''}`.trim()

function setFile(type:string,file:File){
  files.value={...files.value,[type]:file}
}

async function onFaceSelected(e:Event){
  const file=(e.target as HTMLInputElement).files?.[0]
  if(!file)return
  files.value={...files.value,FACE:file}
  faceQuality.passed=false
  faceQuality.message=''
  faceChecking.value=true
  try{
    const result:any=await accessPublicApi.checkFaceQuality(file)
    faceQuality.passed=!!result.passed
    faceQuality.message=result.message||''
  }catch(err:any){
    faceQuality.passed=false
    faceQuality.message=err?.message||'人脸质量检测失败'
  }finally{
    faceChecking.value=false
  }
}

async function submit(){
  if(!form.value.visitorName||!/^1\d{10}$/.test(form.value.visitorPhone)||!/^\d{17}[0-9X]$/.test(form.value.idNumber)){
    ElMessage.warning('请填写正确的姓名、手机号和身份证号');return
  }
  if(form.value.affiliationType==='STALL'&&!form.value.stallId){
    ElMessage.warning('请选择到访店铺');return
  }
  for(const t of ['ID_FRONT','ID_BACK','FACE'])
    if(!files.value[t]){ElMessage.warning('请上传身份证正反面和人脸照片');return}
  if(!faceQuality.passed){
    ElMessage.warning('人脸照片质量不合格，无法通过门禁设备识别，请重新上传清晰的正面照片')
    return
  }
  if(!agreed.value){ElMessage.warning('请先确认资料使用说明');return}
  submitting.value=true
  try{
    if(!submissionToken.value){
      const r:any=await accessPublicApi.create(form.value)
      submissionToken.value=r.token
      appointmentNo.value=r.appointmentNo
    }
    for(const t of ['ID_FRONT','ID_BACK','FACE'])
      await accessPublicApi.upload(submissionToken.value,t,files.value[t])
    done.value=true
    submissionToken.value=''
  }finally{submitting.value=false}
}

function reset(){
  form.value=initial()
  files.value={}
  stalls.value=[]
  agreed.value=false
  done.value=false
  appointmentNo.value=''
  submissionToken.value=''
  faceQuality.passed=false
  faceQuality.message=''
}

const PhotoField=defineComponent({
  props:{label:String,file:Object,capture:Boolean},
  emits:['select'],
  setup(p,{emit}){
    return()=>h(ElFormItem,{label:p.label,required:true},()=>
      h('label',{class:'upload-box'},[
        h('input',{
          type:'file',accept:'image/jpeg,image/png',
          capture:p.capture?'user':undefined,
          onChange:(e:any)=>e.target.files?.[0]&&emit('select',e.target.files[0])
        }),
        h('span',p.file?(p.file as File).name:`拍摄或选择${p.label}`)
      ])
    )
  }
})
</script>

<style scoped>
.visitor-page{min-height:100vh;background:linear-gradient(145deg,#eef5ff,#f8fafc);padding:14px;box-sizing:border-box}
.visitor-card{max-width:620px;margin:auto;background:#fff;border-radius:18px;padding:22px;box-shadow:0 12px 35px #1e3a5f18}
header{text-align:center;margin-bottom:18px}
.brand{font-size:30px;font-weight:800;color:#c82026}
h1{font-size:24px;margin:8px 0}
header p{color:#6b7280;margin:0}
.form :deep(.el-select){width:100%}
.dates{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.upload-box{display:flex;width:100%;min-height:52px;border:1px dashed #93c5fd;border-radius:10px;align-items:center;justify-content:center;background:#f8fbff;color:#2563eb;padding:8px;box-sizing:border-box;text-align:center;cursor:pointer}
.upload-box input{display:none}
.face-upload{min-height:58px;font-weight:500}
.face-status{display:flex;align-items:center;gap:8px;margin-top:8px;padding:10px 12px;border-radius:8px;font-size:14px;line-height:1.5}
.face-status .el-icon{font-size:18px;flex-shrink:0}
.face-status.checking{background:#eff6ff;color:#2563eb}
.face-status.passed{background:#f0fdf4;color:#15803d}
.face-status.failed{background:#fef2f2;color:#dc2626}
.face-hint{display:flex;align-items:flex-start;gap:8px;margin-top:8px;padding:8px 12px;border-radius:8px;font-size:13px;color:#6b7280;background:#f9fafb;line-height:1.5}
.face-hint .el-icon{font-size:16px;flex-shrink:0;margin-top:1px}
.spin{animation:spin 1s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.submit{width:100%;margin-top:18px;height:48px}
.trap{position:absolute;left:-9999px;opacity:0}
@media(max-width:600px){
  .visitor-page{padding:0;background:#fff}
  .visitor-card{min-height:100vh;border-radius:0;padding:18px;box-shadow:none}
  .dates{grid-template-columns:1fr}
  .form :deep(.el-radio-group){display:flex;width:100%}
  .form :deep(.el-radio-button){flex:1}
  .form :deep(.el-radio-button__inner){width:100%;padding:11px 5px}
  .form :deep(.el-input__wrapper){min-height:46px}
  .upload-box{min-height:58px}
}
</style>
