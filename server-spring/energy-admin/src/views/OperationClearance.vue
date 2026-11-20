<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">商户日清记录</div>
        <div class="page-note">日清提交时段：{{ scheduleText }}；仓库和非在用铺位不参与统计。</div>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="viewMode">
          <el-radio-button label="list">列表</el-radio-button>
          <el-radio-button label="album">画册</el-radio-button>
        </el-radio-group>
        <el-button v-if="canConfigure" type="primary" plain @click="openSettings">配置提交时间</el-button>
      </div>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="铺位或商户名称" clearable/>
      <el-date-picker v-model="query.periodDate" type="date" value-format="YYYY-MM-DD" placeholder="日清日期" clearable/>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button v-if="canExport && viewMode==='list'" plain @click="exportCsv">导出</el-button>
    </div>

    <div v-if="viewMode==='list'" class="table-card">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="periodDate" label="日清日期" width="115"/>
        <el-table-column prop="stallCode" label="铺位" width="110"/>
        <el-table-column prop="merchantName" label="商户" min-width="160"/>
        <el-table-column prop="submitterName" label="提交人" width="110"/>
        <el-table-column prop="managerName" label="责任商管" width="110"/>
        <el-table-column label="检查结果" min-width="220"><template #default="{row}"><el-tag v-for="item in checks(row)" :key="item.name" :type="item.ok?'success':'danger'" size="small" class="tag">{{item.name}}{{item.ok?'已关':'未关'}}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='NORMAL'?'success':'danger'">{{row.status==='NORMAL'?'正常':'异常'}}</el-tag></template></el-table-column>
        <el-table-column label="清场照片" width="150">
          <template #default="{row}">
            <div class="row-photos">
              <template v-if="(row.photos||[]).length">
                <template v-for="(p,i) in row.photos.slice(0,3)" :key="p.id">
                  <img v-if="thumbUrls[p.id]" class="row-thumb" :src="thumbUrls[p.id]" loading="lazy" @click="openViewer(row.photos,i)"/>
                  <div v-else class="row-thumb thumb-state" @click="retryThumb(p.id)">{{thumbFailed[p.id]?'重试':''}}</div>
                </template>
                <span v-if="row.photos.length>3" class="more">+{{row.photos.length-3}}</span>
              </template>
              <span v-else class="muted">无</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="异常说明" min-width="170"><template #default="{row}">{{row.exceptionDescription||'-'}}</template></el-table-column>
        <el-table-column label="提交时间" width="175"><template #default="{row}">{{formatDateTime(row.submittedAt)}}</template></el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @change="load"/>
    </div>

    <div v-else class="album-card" v-loading="albumLoading">
      <div v-if="!albumPhotos.length" class="empty">暂无清场照片</div>
      <div class="photo-wall">
        <div v-for="(item,idx) in albumPhotos" :key="item.id" class="wall-item">
          <img v-if="thumbUrls[item.id]" class="wall-img" :src="thumbUrls[item.id]" loading="lazy" @click="openAlbumViewer(idx)"/>
          <div v-else class="wall-img wall-state" @click="retryThumb(item.id)">{{thumbFailed[item.id]?'点击重试':'加载中…'}}</div>
          <div class="wall-meta">
            <div class="wall-merchant">{{item.merchantName||'-'}} <span class="stall">{{item.stallCode}}</span></div>
            <div class="wall-sub">{{item.submitterName}} · {{formatDateTime(item.submittedAt)}}</div>
            <el-tag :type="item.status==='NORMAL'?'success':'danger'" size="small">{{item.status==='NORMAL'?'正常':'异常'}}</el-tag>
          </div>
        </div>
      </div>
      <div v-if="albumRows.length<albumTotal" class="album-more">
        <el-button :loading="albumLoading" @click="loadAlbum(false)">加载更多（{{albumRows.length}}/{{albumTotal}}）</el-button>
      </div>
    </div>

    <el-image-viewer v-if="viewerVisible" :url-list="viewerList" :initial-index="viewerIndex" hide-on-click-modal teleported @close="viewerVisible=false"/>

    <el-dialog v-model="settingsVisible" title="配置商户日清提交时间" width="460px">
      <el-alert title="支持跨日时段。例如开始21:00、结束09:00，表示每日21:00至次日09:00。" type="info" :closable="false"/>
      <el-form label-width="100px" class="settings-form">
        <el-form-item label="开始时间" required><el-time-select v-model="settingsForm.startTime" start="00:00" step="00:05" end="23:55" placeholder="选择开始时间" style="width:100%"/></el-form-item>
        <el-form-item label="结束时间" required><el-time-select v-model="settingsForm.endTime" start="00:00" step="00:05" end="23:55" placeholder="选择结束时间" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="settingsVisible=false">取消</el-button><el-button type="primary" :loading="settingsSaving" @click="saveSettings">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {operationClearanceApi,operationFormImageApi} from '@/api/operation'
import {formatDateTime} from '@/utils/format'
import {useUserStore} from '@/stores/user'

const user=useUserStore(),rows=ref<any[]>([]),total=ref(0),loading=ref(false),scheduleText=ref('加载中')
const settingsVisible=ref(false),settingsSaving=ref(false),settingsForm=reactive({startTime:'21:00',endTime:'09:00'})
const query=reactive({keyword:'',periodDate:'',pageNum:1,pageSize:20})
const viewMode=ref<'list'|'album'>('list')
const canConfigure=computed(()=>user.hasPermission('operation.clearance.setting.manage'))
const canExport=computed(()=>user.hasPermission('operation.clearance.export'))

// -------- 图片：缩略图常驻，原图仅在预览时按需加载，全局并发上限 4 --------
const thumbUrls=reactive<Record<string,string>>({})
const thumbFailed=reactive<Record<string,boolean>>({})
const thumbPending=reactive<Record<string,boolean>>({})
const fullUrls=reactive<Record<string,string>>({})
const fullPending=reactive<Record<string,boolean>>({})
const fullFailed=reactive<Record<string,boolean>>({})
const MAX_CONCURRENT=4
let activeLoads=0
const loadQueue:(()=>void)[]=[]
function withLimit<T>(task:()=>Promise<T>):Promise<T>{
  return new Promise<T>((resolve,reject)=>{
    const run=()=>{activeLoads++;task().then(resolve,reject).finally(()=>{activeLoads--;const next=loadQueue.shift();if(next)next()})}
    if(activeLoads<MAX_CONCURRENT)run();else loadQueue.push(run)
  })
}
async function loadBlob(cache:Record<string,string>,pending:Record<string,boolean>,failed:Record<string,boolean>,id:any,fetcher:(key:string)=>Promise<any>){
  const key=id?String(id):''
  if(!key||cache[key]||pending[key])return
  pending[key]=true
  try{
    const blob:any=await withLimit(()=>fetcher(key))
    if(!(blob instanceof Blob))throw new Error('响应不是图片')
    cache[key]=URL.createObjectURL(blob);failed[key]=false
  }catch(e){failed[key]=true}finally{pending[key]=false}
}
const fetchThumb=(id:any)=>loadBlob(thumbUrls,thumbPending,thumbFailed,id,operationFormImageApi.thumb)
const fetchFull=(id:any)=>loadBlob(fullUrls,fullPending,fullFailed,id,operationFormImageApi.image)
function retryThumb(id:any){const key=id?String(id):'';if(!key)return;thumbFailed[key]=false;thumbPending[key]=false;fetchThumb(key)}
function prefetchThumbs(list:any[]){for(const r of list)for(const p of (r.photos||[]))fetchThumb(p.id)}

// -------- 原图预览：点击时才加载原图 --------
const viewerVisible=ref(false),viewerIndex=ref(0),viewerList=ref<string[]>([])
async function openViewer(photos:any[],start:number){
  const list=(photos||[]).filter((p:any)=>p&&p.id)
  if(!list.length)return
  const idx=Math.max(0,Math.min(start,list.length-1))
  await fetchFull(list[idx].id)
  viewerIndex.value=idx
  viewerList.value=list.map((p:any)=>fullUrls[p.id]||thumbUrls[p.id]||'')
  viewerVisible.value=true
  list.forEach((p:any)=>fetchFull(p.id))
}

// -------- 画册独立分页，每页 24 张 --------
const albumRows=ref<any[]>([]),albumTotal=ref(0),albumLoading=ref(false),albumPage=ref(1)
const albumPhotos=computed(()=>albumRows.value.flatMap((r:any)=>(r.photos||[]).map((p:any)=>({...p,merchantName:r.merchantName,stallCode:r.stallCode,submitterName:r.submitterName,status:r.status,submittedAt:r.submittedAt,periodDate:r.periodDate}))))
const openAlbumViewer=(idx:number)=>openViewer(albumPhotos.value,idx)
async function loadAlbum(reset:boolean){
  albumLoading.value=true
  try{
    if(reset)albumPage.value=1
    const result:any=await operationClearanceApi.page({keyword:query.keyword,periodDate:query.periodDate,pageNum:albumPage.value,pageSize:24})
    const records=result.records||[]
    albumRows.value=reset?records:[...albumRows.value,...records]
    albumTotal.value=Number(result.total)||0
    prefetchThumbs(records)
    if(records.length)albumPage.value++
  }finally{albumLoading.value=false}
}

async function load(){loading.value=true;try{const result:any=await operationClearanceApi.page(query);rows.value=result.records||[];total.value=Number(result.total)||0;prefetchThumbs(rows.value)}finally{loading.value=false}}
async function loadSchedule(){const result:any=await operationClearanceApi.schedule();scheduleText.value=result.scheduleText||`${result.startTime||'21:00'}至${result.endTime||'09:00'}`}
function search(){query.pageNum=1;if(viewMode.value==='album')loadAlbum(true);else load()}
async function exportCsv(){const blob:any=await operationClearanceApi.export({keyword:query.keyword,periodDate:query.periodDate});const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`商户日清记录_${new Date().toISOString().slice(0,10)}.csv`;link.click();URL.revokeObjectURL(url)}
const checks=(row:any)=>[{name:'电',ok:!!row.electricityOff},{name:'水',ok:!!row.waterOff},{name:'气',ok:!!row.gasOff},{name:'门窗',ok:!!row.doorClosed}]
async function openSettings(){const result:any=await operationClearanceApi.settings();settingsForm.startTime=result.startTime;settingsForm.endTime=result.endTime;settingsVisible.value=true}
async function saveSettings(){if(!settingsForm.startTime||!settingsForm.endTime)return ElMessage.warning('请选择开始和结束时间');if(settingsForm.startTime===settingsForm.endTime)return ElMessage.warning('开始时间和结束时间不能相同');settingsSaving.value=true;try{const result:any=await operationClearanceApi.updateSettings(settingsForm);scheduleText.value=result.scheduleText;settingsVisible.value=false;ElMessage.success('日清提交时间已更新')}finally{settingsSaving.value=false}}
onMounted(async()=>{await Promise.all([load(),loadSchedule()])})
watch(viewMode,mode=>{if(mode==='album')loadAlbum(true);else load()})
onBeforeUnmount(()=>{for(const url of Object.values(thumbUrls))URL.revokeObjectURL(url);for(const url of Object.values(fullUrls))URL.revokeObjectURL(url)})
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.header-actions{display:flex;align-items:center;gap:12px}
.toolbar{display:flex;gap:10px;margin-bottom:12px}.toolbar .el-input{width:250px}.tag{margin:2px}.pager{margin-top:12px;justify-content:flex-end}.settings-form{margin-top:18px}
.row-photos{display:flex;align-items:center;gap:4px;flex-wrap:wrap}
.row-thumb{width:38px;height:38px;border-radius:4px;cursor:pointer;object-fit:cover;display:block}
.more{color:#909399;font-size:12px}.muted{color:#c0c4cc;font-size:12px}
.album-card{min-height:300px}.empty{text-align:center;color:#909399;padding:60px 0}
.photo-wall{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:14px}
.wall-item{border:1px solid #ebeef5;border-radius:8px;overflow:hidden;background:#fff}
.wall-img{width:100%;height:180px;display:block;cursor:pointer;object-fit:cover}
.album-more{margin-top:16px;text-align:center}
.thumb-state{background:#f5f7fa;display:flex;align-items:center;justify-content:center;color:#909399;font-size:11px}
.wall-state{background:#f5f7fa;display:flex;align-items:center;justify-content:center;color:#909399;font-size:13px}
.wall-meta{padding:8px 10px}
.wall-merchant{font-size:13px;font-weight:600;color:#303133}
.wall-merchant .stall{color:#909399;font-weight:400;margin-left:4px}
.wall-sub{font-size:12px;color:#909399;margin:3px 0 6px}
@media(max-width:768px){.page-header{align-items:flex-start;gap:12px;flex-direction:column}.header-actions{width:100%;justify-content:space-between}.toolbar{flex-wrap:wrap}.toolbar>*{width:100%!important}.table-card{overflow-x:auto}.photo-wall{grid-template-columns:repeat(auto-fill,minmax(150px,1fr))}:deep(.el-dialog){width:calc(100% - 20px)!important}}
</style>
