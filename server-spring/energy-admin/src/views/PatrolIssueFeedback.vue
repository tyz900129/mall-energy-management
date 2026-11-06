<template>
  <div class="m-page">
    <div class="m-head">
      <div class="m-title">问题整改反馈</div>
      <div class="m-sub">{{issue?.issueNo}}　{{issue?.pointName}}</div>
    </div>
    <div v-loading="loading" class="m-body">
      <template v-if="issue">
        <div class="card">
          <div class="row"><label>区域</label><span>{{issue.area}} {{issue.floor||''}}</span></div>
          <div class="row"><label>整改部门</label><span>{{issue.departmentName||'待分派'}}</span></div>
          <div class="row"><label>问题分类</label><span>{{issue.category||'-'}}</span></div>
          <div class="row"><label>紧急程度</label><span :class="{danger:issue.urgency==='URGENT'}">{{issue.urgency==='URGENT'?'紧急':'一般'}}</span></div>
          <div class="row"><label>上报人</label><span>{{issue.submitterName}} · {{short(issue.submittedAt)}}</span></div>
          <div class="row"><label>整改时限</label><span :class="{danger:overdue}">{{short(issue.dueAt)}}</span></div>
          <div class="row"><label>当前状态</label><span>{{statusLabel(issue.status)}}</span></div>
        </div>

        <div class="card">
          <div class="card-title">问题描述</div>
          <p class="desc">{{issue.description}}</p>
          <div class="photos" v-if="attachments.length">
            <el-image v-for="a in attachments" :key="a.id" :src="a.url" :preview-src-list="attachments.map((x:any)=>x.url)" fit="cover" class="photo" />
          </div>
        </div>

        <div class="card" v-if="issue.rectificationNote">
          <div class="card-title">已提交的整改说明</div>
          <p class="desc">{{issue.rectificationNote}}</p>
          <div class="row"><label>提交时间</label><span>{{short(issue.rectifiedAt)}}</span></div>
        </div>

        <div class="card" v-if="canFeedback">
          <div class="card-title">提交整改情况</div>
          <div class="field">
            <label>整改措施 <i>*</i></label>
            <el-input v-model="note" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写处理过程与结果" />
          </div>
          <div class="field">
            <label>整改后照片</label>
            <div class="photos">
              <div v-for="(item,idx) in photos" :key="idx" class="photo">
                <img :src="item.preview" alt="整改照片" />
                <span class="remove" @click="removePhoto(idx)">×</span>
              </div>
              <label v-if="photos.length<6" class="photo add">
                <input type="file" accept="image/*" multiple @change="onFileChange" />
                <span>+ 拍照</span>
              </label>
            </div>
          </div>
          <el-button type="primary" size="large" class="submit" :loading="saving" @click="submit">提交整改反馈</el-button>
        </div>

        <div class="card done" v-if="issue.status==='CLOSED'">
          该问题已整改闭环，感谢处理。
        </div>
        <div class="card done" v-else-if="issue.status==='REVIEWING' && !canFeedback">
          整改结果已提交，等待物业管理员复核。
        </div>
      </template>
      <el-empty v-else-if="!loading" description="整改链接无效或已失效" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { patrolApi } from '@/api/patrol'
const route = useRoute()
const token = String(route.params.token||'')
const loading = ref(false)
const saving = ref(false)
const issue = ref<any>(null)
const attachments = ref<any[]>([])
const canFeedback = ref(false)
const note = ref('')
const photos = ref<{file:File,preview:string}[]>([])
const overdue = computed(()=> !!issue.value?.dueAt && issue.value.status!=='CLOSED' && new Date(issue.value.dueAt).getTime()<Date.now())

function short(value:string){ return value ? value.replace('T',' ').slice(0,16) : '-' }
function statusLabel(status:string){
  return ({UNASSIGNED:'待分派',ASSIGNED:'待整改',RECTIFYING:'整改中',REVIEWING:'待复核',CLOSED:'已闭环'} as Record<string,string>)[status] || status
}
async function load(){
  loading.value=true
  try{
    const data:any = await patrolApi.publicIssue(token)
    issue.value = data.issue
    attachments.value = data.attachments || []
    canFeedback.value = !!data.canFeedback
  }catch{ issue.value = null }
  finally{ loading.value=false }
}
async function compress(file:File):Promise<File>{
  if(file.size <= 150*1024) return file
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 960/Math.max(bitmap.width,bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width*scale)
  canvas.height = Math.round(bitmap.height*scale)
  canvas.getContext('2d')!.drawImage(bitmap,0,0,canvas.width,canvas.height)
  const blob:Blob|null = await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',0.65))
  return blob ? new File([blob], file.name.replace(/\.\w+$/,'')+'.jpg',{type:'image/jpeg'}) : file
}
async function onFileChange(event:Event){
  const input = event.target as HTMLInputElement
  for(const file of Array.from(input.files||[])){
    if(photos.value.length>=6) break
    if(!file.type.startsWith('image/')) continue
    if(file.size > 8*1024*1024){ ElMessage.warning(`${file.name} 超过 8MB`); continue }
    const compressed = await compress(file)
    photos.value.push({file:compressed, preview:URL.createObjectURL(compressed)})
  }
  input.value=''
}
function removePhoto(index:number){
  URL.revokeObjectURL(photos.value[index].preview)
  photos.value.splice(index,1)
}
async function submit(){
  if(note.value.trim().length<2) return ElMessage.warning('请填写整改措施')
  saving.value=true
  try{
    await patrolApi.publicFeedback(token, note.value.trim(), photos.value.map(p=>p.file))
    photos.value.forEach(p=>URL.revokeObjectURL(p.preview))
    photos.value=[]
    note.value=''
    ElMessage.success('整改已提交并闭环')
    await load()
  }finally{ saving.value=false }
}
onMounted(load)
</script>

<style scoped>
.m-page{min-height:100vh;background:#f4f6f8;padding-bottom:24px}
.m-head{background:linear-gradient(135deg,#f56c6c,#d93a3a);color:#fff;padding:18px 16px}
.m-title{font-size:19px;font-weight:600}
.m-sub{margin-top:6px;font-size:13px;opacity:.9}
.m-body{padding:12px}
.card{background:#fff;border-radius:10px;padding:14px;margin-bottom:12px}
.card-title{font-size:14px;font-weight:600;color:#303133;margin-bottom:10px}
.row{display:flex;justify-content:space-between;font-size:13px;padding:5px 0;color:#606266;gap:10px}
.row label{color:#909399;flex-shrink:0}
.danger{color:#f56c6c}
.desc{margin:0;font-size:14px;line-height:1.7;color:#303133;white-space:pre-wrap}
.photos{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
.photo{position:relative;width:80px;height:80px;border-radius:8px;overflow:hidden;background:#f5f7fa}
.photo img{width:100%;height:100%;object-fit:cover}
.photo .remove{position:absolute;right:0;top:0;width:20px;height:20px;line-height:20px;text-align:center;background:rgba(0,0,0,.5);color:#fff;cursor:pointer}
.photo.add{display:flex;align-items:center;justify-content:center;border:1px dashed #c0c4cc;color:#909399;font-size:12px;cursor:pointer}
.photo.add input{display:none}
.field{margin-bottom:12px}
.field>label{display:block;font-size:13px;color:#606266;margin-bottom:6px}
.field label i{color:#f56c6c;font-style:normal}
.submit{width:100%;height:46px;font-size:16px}
.done{color:#909399;font-size:13px;text-align:center}
</style>
