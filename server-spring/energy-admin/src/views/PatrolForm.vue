<template>
  <div class="m-page">
    <div class="m-head">
      <el-page-header @back="goBack">
        <template #content><span class="head-title">{{point?.name || '点位巡检'}}</span></template>
      </el-page-header>
      <div class="head-sub">{{point?.area}} {{point?.floor}}　{{point?.locationText}}</div>
    </div>

    <div v-loading="loading" class="m-body">
      <div class="card">
        <div class="row" v-if="submitter?.id"><label>巡检人</label><span>{{submitter.displayName}} <em class="logged">已登录</em></span></div>
        <template v-else>
          <div class="login-tip">未验证登录，登录一次后本机 180 天内自动识别身份；也可以直接填写姓名提交。</div>
          <el-button type="primary" plain size="small" class="login-btn" @click="loginOnce">登录并自动记录填报人</el-button>
          <div class="field">
            <label>填报人 <i>*</i></label>
            <el-input v-model="form.submitterName" maxlength="20" clearable placeholder="请填写您的姓名，便于追踪问题" />
          </div>
        </template>
        <div class="row"><label>巡检时间</label><span>{{now}}</span></div>
      </div>

      <div class="card" v-if="point?.checkItems?.length">
        <div class="card-title">检查要点</div>
        <el-checkbox v-for="(item,idx) in point.checkItems" :key="idx" v-model="checks[idx]" :label="item.label" class="check-item" />
      </div>

      <div class="card">
        <div class="card-title">巡检结果</div>
        <div class="result-row">
          <button class="result-btn" :class="{active: form.result==='NORMAL'}" @click="form.result='NORMAL'">正常</button>
          <button class="result-btn danger" :class="{active: form.result==='ABNORMAL'}" @click="form.result='ABNORMAL'">异常</button>
        </div>
      </div>

      <template v-if="form.result==='ABNORMAL'">
        <div class="card">
          <div class="card-title">问题信息</div>
          <div class="field">
            <label>问题分类 <i>*</i></label>
            <el-select v-model="form.category" placeholder="请选择" style="width:100%">
              <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
          <div class="field">
            <label>问题描述</label>
            <el-input v-model="form.note" type="textarea" :rows="4" placeholder="选填，可描述具体问题与位置" />
          </div>
        </div>
      </template>

      <div class="card" v-if="form.result==='ABNORMAL' || specialArea">
        <div class="card-title">{{ specialArea && form.result==='NORMAL' ? '现场照片留档' : '现场照片' }}</div>
        <div class="field">
          <label>现场照片 <i>*</i></label>
          <div class="photos">
            <div v-for="(file,idx) in photos" :key="idx" class="photo">
              <img :src="file.preview" alt="现场照片" />
              <span class="remove" @click="removePhoto(idx)">×</span>
            </div>
            <label v-if="photos.length<6" class="photo add">
              <input type="file" accept="image/*" multiple @change="onFileChange" />
              <span>+ 拍照</span>
            </label>
          </div>
          <div class="hint" v-if="specialArea && form.result==='NORMAL'">特殊区域正常巡检也需拍照留档，提交后推送至巡场群；正常结果无需整改。</div>
          <div class="hint" v-else>最多 6 张，单张不超过 8MB</div>
        </div>
      </div>

      <div class="card" v-if="form.result==='ABNORMAL'">
        <div class="card-title">整改信息</div>
        <div class="field">
          <label>整改部门</label>
          <el-select v-model="form.departmentId" clearable placeholder="可不选，稍后由管理员分派" style="width:100%">
            <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" :disabled="!item.webhookConfigId" />
          </el-select>
          <div class="hint">选择后立即推送到该部门群；不选择则进入待分派列表。</div>
        </div>
      </div>

      <div class="submit-bar">
        <el-button type="primary" size="large" :loading="submitting" class="submit" @click="submit">{{ uploading ? uploadText : '提交巡检' }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const token = String(route.query.token||'')
const pointId = Number(route.query.pointId||0)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const uploadText = ref('')
const point = ref<any>(null)
const submitter = ref<any>(null)
const categories = ref<string[]>([])
const departments = ref<any[]>([])
const checks = ref<boolean[]>([])
const photos = ref<{file:File,preview:string}[]>([])
const specialArea = ref(false)
const now = new Date().toLocaleString('zh-CN',{hour12:false}).replace(/\//g,'-')
const form = reactive<any>({result:'NORMAL',category:'',note:'',departmentId:null,submitterName:localStorage.getItem('patrol.submitterName')||''})

async function load(){
  if(!token||!pointId) return ElMessage.error('缺少点位参数')
  loading.value=true
  try{
    // 打开页面先恢复登录态：JWT 有效直接识别，过期则用本机设备凭据静默换新
    await userStore.restoreSession()
    const data:any = await patrolApi.form(pointId, token)
    point.value = data.point
    specialArea.value = !!data.specialArea
    submitter.value = data.submitter?.id ? data.submitter : null
    categories.value = data.categories || []
    departments.value = data.departments || []
    checks.value = new Array(point.value?.checkItems?.length || 0).fill(true)
  }finally{ loading.value=false }
}
function loginOnce(){
  router.push({ path:'/login', query:{ redirect: route.fullPath } })
}
async function compress(file:File):Promise<File>{
  // 手机原图普遍 2-8MB，巡场现场看缺陷 960px JPEG 0.65 画质足够，压到 ≤150KB 弱网也能几秒内传完
  if(file.size <= 150*1024) return file
  const bitmap = await createImageBitmap(file)
  const max = 960
  const scale = Math.min(1, max/Math.max(bitmap.width,bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width*scale)
  canvas.height = Math.round(bitmap.height*scale)
  canvas.getContext('2d')!.drawImage(bitmap,0,0,canvas.width,canvas.height)
  const blob:Blob|null = await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',0.65))
  if(!blob) return file
  return new File([blob], file.name.replace(/\.\w+$/,'')+'.jpg', {type:'image/jpeg'})
}
async function onFileChange(event:Event){
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files||[])
  for(const file of files){
    if(photos.value.length>=6) break
    if(!file.type.startsWith('image/')){ ElMessage.warning('请选择图片文件'); continue }
    if(file.size > 8*1024*1024){ ElMessage.warning(`${file.name} 超过 8MB`); continue }
    try{
      const compressed = await compress(file)
      photos.value.push({file:compressed, preview:URL.createObjectURL(compressed)})
    }catch{ photos.value.push({file, preview:URL.createObjectURL(file)}) }
  }
  input.value = ''
}
function removePhoto(index:number){
  URL.revokeObjectURL(photos.value[index].preview)
  photos.value.splice(index,1)
}
async function submit(){
  if(!submitter.value?.id && !form.submitterName.trim()) return ElMessage.warning('请填写填报人姓名，或先登录后提交')
  if(form.result==='ABNORMAL'){
    if(!form.category) return ElMessage.warning('请选择问题分类')
    if(!photos.value.length) return ElMessage.warning('请至少上传 1 张现场照片')
  }else if(specialArea.value && !photos.value.length){
    return ElMessage.warning('特殊区域巡检请至少上传 1 张现场照片')
  }
  submitting.value=true
  try{
    const payload:any = {
      pointId,
      result: form.result,
      submitterName: submitter.value?.id ? '' : form.submitterName.trim(),
      note: form.result==='ABNORMAL' ? form.note.trim() : '',
      category: form.result==='ABNORMAL' ? form.category : '',
      departmentId: form.result==='ABNORMAL' ? form.departmentId : '',
      checkResults: (point.value?.checkItems||[]).map((item:any,idx:number)=>({label:item.label,checked:!!checks.value[idx]}))
    }
    if(payload.submitterName) localStorage.setItem('patrol.submitterName', payload.submitterName)
    // 照片一次性随表单提交，保证推送时能带上全部现场照片（特殊区域正常巡检也需留档）
    const needPhoto = form.result==='ABNORMAL' || specialArea.value
    const files = needPhoto ? photos.value.map(p=>p.file) : []
    const result:any = await patrolApi.submit(token, payload, files, (percent:number)=>{
      uploading.value = true
      uploadText.value = `照片上传 ${percent}%`
    })
    photos.value.forEach(p=>URL.revokeObjectURL(p.preview))
    photos.value = []
    if(result?.issue?.status==='UNASSIGNED') ElMessage.success(`已提交异常，问题编号 ${result.issue.issueNo}，等待分派`)
    else if(result?.issue) ElMessage.success(`已提交异常并推送${result.issue.departmentName||''}群`)
    else if(result?.specialArea) ElMessage.success('正常巡检已提交，照片已推送至巡场群')
    else ElMessage.success('巡检结果已提交')
    router.replace({path:'/patrol/entry',query:{token}})
  }finally{
    submitting.value=false
    uploading.value=false
    uploadText.value=''
  }
}
function goBack(){ router.replace({path:'/patrol/entry',query:{token}}) }
onMounted(load)
</script>

<style scoped>
.m-page{min-height:100vh;background:#f4f6f8;padding-bottom:20px}
.m-head{background:#fff;padding:12px 16px;border-bottom:1px solid #ebeef5;position:sticky;top:0;z-index:10}
.head-title{font-size:16px;font-weight:600;color:#303133}
.head-sub{font-size:12px;color:#909399;margin-top:6px}
.m-body{padding:12px}
.card{background:#fff;border-radius:10px;padding:14px;margin-bottom:12px}
.card-title{font-size:14px;font-weight:600;color:#303133;margin-bottom:10px}
.row{display:flex;justify-content:space-between;font-size:13px;padding:5px 0;color:#606266}
.row label{color:#909399}
.logged{font-style:normal;font-size:11px;color:#3f9c35;background:#eafaf1;border-radius:4px;padding:1px 5px;margin-left:4px}
.login-tip{font-size:12px;color:#909399;line-height:1.6;margin-bottom:10px;background:#f7f9fc;border-radius:8px;padding:10px}
.login-btn{width:100%;margin-bottom:14px}
.check-item{display:flex;margin-bottom:8px;font-size:13px}
.result-row{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.result-btn{padding:18px 0;border-radius:10px;border:2px solid #dcdfe6;background:#fafafa;font-size:17px;font-weight:600;color:#606266;cursor:pointer}
.result-btn.active{border-color:#67c23a;background:#eafaf1;color:#3f9c35}
.result-btn.danger.active{border-color:#f56c6c;background:#fdecec;color:#d93a3a}
.field{margin-bottom:14px}
.field>label{display:block;font-size:13px;color:#606266;margin-bottom:6px}
.field label i{color:#f56c6c;font-style:normal}
.photos{display:flex;flex-wrap:wrap;gap:8px}
.photo{position:relative;width:78px;height:78px;border-radius:8px;overflow:hidden;background:#f5f7fa}
.photo img{width:100%;height:100%;object-fit:cover}
.photo .remove{position:absolute;right:0;top:0;width:20px;height:20px;line-height:20px;text-align:center;background:rgba(0,0,0,.5);color:#fff;cursor:pointer}
.photo.add{display:flex;align-items:center;justify-content:center;border:1px dashed #c0c4cc;color:#909399;font-size:12px;cursor:pointer}
.photo.add input{display:none}
.hint{font-size:12px;color:#909399;margin-top:6px}
.submit-bar{padding:4px 2px 0}
.submit{width:100%;height:46px;font-size:16px}
</style>
