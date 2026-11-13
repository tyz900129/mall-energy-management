<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">巡场问题台账</div>
        <div class="page-note">待分派问题置顶显示，可补充或变更整改部门；确认分派后立即推送部门群。</div>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><b>{{overview.openIssues??'-'}}</b><span>未闭环</span></div>
      <div class="stat"><b class="warn">{{overview.unassigned??'-'}}</b><span>待分派</span></div>
      <div class="stat"><b class="danger">{{overview.overdue??'-'}}</b><span>已逾期</span></div>
      <div class="stat"><b>{{overview.todayAbnormal??'-'}}</b><span>今日异常</span></div>
      <div class="stat"><b>{{overview.closedToday??'-'}}</b><span>今日闭环</span></div>
    </div>

    <div class="filters">
      <el-select v-model="query.status" clearable placeholder="全部状态" style="width:150px" @change="search">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.departmentId" clearable placeholder="全部部门" style="width:150px" @change="search">
        <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="问题编号 / 点位 / 描述" clearable style="width:240px" @keyup.enter="search" />
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:260px" @change="search" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="issueNo" label="编号" width="150" />
      <el-table-column prop="area" label="区域" width="110" />
      <el-table-column prop="pointName" label="点位" min-width="130" />
      <el-table-column prop="description" label="问题描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="部门" width="110">
        <template #default="{row}">
          <span v-if="row.departmentName">{{row.departmentName}}</span>
          <el-tag v-else type="warning">待分派</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="紧急" width="80">
        <template #default="{row}"><el-tag :type="row.urgency==='URGENT'?'danger':'info'" size="small">{{row.urgency==='URGENT'?'紧急':'一般'}}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{row}"><el-tag :type="statusType(row.status)">{{statusLabel(row.status)}}</el-tag></template>
      </el-table-column>
      <el-table-column label="时限" width="150">
        <template #default="{row}">
          <span :class="{danger: isOverdue(row)}">{{ formatTime(row.dueAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上报时间" width="150">
        <template #default="{row}">{{formatTime(row.submittedAt)}}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button v-if="canAssign && row.status!=='CLOSED'" link type="primary" @click="openAssign(row)">分派</el-button>
          <el-button v-if="canReview && (row.status==='REVIEWING'||row.status==='RECTIFYING')" link type="success" @click="openReview(row)">复核</el-button>
          <el-button v-if="isSuperAdmin" link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p:number)=>{query.page=p;load()}" />

    <el-drawer v-model="drawer" title="问题详情" :size="isMobile?'100%':'480px'">
      <div v-if="current" class="detail">
        <div class="kv"><label>编号</label><span>{{current.issueNo}}</span></div>
        <div class="kv"><label>区域 / 点位</label><span>{{current.area}} · {{current.pointName}}</span></div>
        <div class="kv"><label>问题分类</label><span>{{current.category||'-'}}</span></div>
        <div class="kv"><label>紧急程度</label><span>{{current.urgency==='URGENT'?'紧急':'一般'}}</span></div>
        <div class="kv"><label>整改部门</label><span>{{current.departmentName||'待分派'}}</span></div>
        <div class="kv"><label>状态</label><span>{{statusLabel(current.status)}}</span></div>
        <div class="kv"><label>上报人</label><span>{{current.submitterName}} · {{formatTime(current.submittedAt)}}</span></div>
        <div class="kv"><label>整改时限</label><span :class="{danger: isOverdue(current)}">{{formatTime(current.dueAt)}}</span></div>
        <div class="kv block"><label>问题描述</label><p>{{current.description}}</p></div>
        <div class="photos" v-if="current.attachments?.length">
          <el-image v-for="a in current.attachments" :key="a.id" :src="photoUrl(a)" :preview-src-list="current.attachments.map(photoUrl)" fit="cover" class="photo" />
        </div>
        <div class="kv block" v-if="current.rectificationNote"><label>整改反馈</label><p>{{current.rectificationNote}}</p></div>
        <div class="kv block" v-if="current.reviewNote"><label>复核意见</label><p>{{current.reviewNote}}</p></div>
        <div class="logs">
          <div class="log" v-for="(log,idx) in current.logs" :key="idx">
            <span>{{formatTime(log.createdAt)}}</span>
            <b>{{actionLabel(log.action)}}</b>
            <em>{{log.note||''}}</em>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="assignVisible" title="分派整改部门" :width="isMobile?'92%':'480px'">
      <el-form label-position="top">
        <el-form-item label="整改部门" required>
          <el-select v-model="assignForm.departmentId" placeholder="请选择" style="width:100%">
            <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" :disabled="!item.webhookConfigId">
              <span>{{item.name}}</span>
              <span v-if="!item.webhookConfigId" style="color:#e6a23c;font-size:12px">（未配置群组）</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="分派说明"><el-input v-model="assignForm.note" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssign">确认分派并推送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="复核整改结果" :width="isMobile?'92%':'480px'">
      <el-form label-position="top">
        <el-form-item label="复核意见"><el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible=false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="submitReview(false)">驳回重改</el-button>
        <el-button type="success" :loading="saving" @click="submitReview(true)">通过闭环</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,reactive,ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const route = useRoute()
const canAssign = computed(() => user.hasPermission('patrol.issue.assign'))
const canReview = computed(() => user.hasPermission('patrol.issue.review'))
const isSuperAdmin = computed(() => user.hasPermission('patrol.delete'))
const statusOptions = [
  {value:'UNASSIGNED',label:'待分派'},{value:'ASSIGNED',label:'待整改'},{value:'REVIEWING',label:'待复核'},{value:'CLOSED',label:'已闭环'}
]
const rows = ref<any[]>([])
const departments = ref<any[]>([])
const overview = ref<any>({})
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dateRange = ref<string[]>([])
const drawer = ref(false)
const assignVisible = ref(false)
const reviewVisible = ref(false)
const current = ref<any>(null)
const query = reactive<any>({page:1,size:20,status:'',departmentId:null,keyword:''})
const assignForm = reactive<any>({id:null,departmentId:null,note:''})
const reviewForm = reactive<any>({id:null,note:''})
const isMobile = ref(false)
function syncMobile(){ isMobile.value = window.innerWidth <= 768 }

const ACTION_LABELS: Record<string,string> = {
  CREATE:'上报异常',
  ASSIGN:'分派整改部门',
  TRANSFER:'变更整改部门',
  FEEDBACK_CLOSE:'提交整改反馈',
  REVIEW_PASS:'复核通过',
  REVIEW_REJECT:'复核驳回',
  OVERDUE_NOTICE:'逾期提醒'
}
function actionLabel(action:string){ return ACTION_LABELS[action] || '状态变更' }

function statusLabel(status:string){
  return ({UNASSIGNED:'待分派',ASSIGNED:'待整改',RECTIFYING:'整改中',REVIEWING:'待复核',CLOSED:'已闭环',REJECTED:'已驳回'} as Record<string,string>)[status] || status
}
function statusType(status:string):'primary'|'success'|'warning'|'info'|'danger'{
  return ({UNASSIGNED:'warning',ASSIGNED:'primary',RECTIFYING:'primary',REVIEWING:'warning',CLOSED:'success',REJECTED:'danger'} as Record<string,'primary'|'success'|'warning'|'info'|'danger'>)[status] || 'info'
}
function formatTime(value:string){ return value ? value.replace('T',' ').slice(0,16) : '-' }
function isOverdue(row:any){ return !!row.dueAt && !['CLOSED'].includes(row.status) && new Date(row.dueAt).getTime() < Date.now() }
function photoUrl(a:any){ return current.value ? `/api/v1/patrol-public/attachments/${current.value.feedbackToken}/${a.id}` : '' }

async function load(){
  loading.value=true
  try{
    const data:any = await patrolApi.issues({...query, dateFrom:dateRange.value?.[0], dateTo:dateRange.value?.[1]})
    rows.value = data?.items || []
    total.value = data?.total || 0
  }finally{ loading.value=false }
}
async function loadOverview(){ overview.value = await patrolApi.overview() as any || {} }
async function loadDepartments(){ departments.value = await patrolApi.departments() as any || [] }
function search(){ query.page=1; load() }
async function detail(row:any){
  current.value = await patrolApi.issue(row.id) as any
  drawer.value=true
}
function openAssign(row:any){
  Object.assign(assignForm,{id:row.id,departmentId:row.departmentId||null,note:''})
  assignVisible.value=true
}
async function saveAssign(){
  if(!assignForm.departmentId) return ElMessage.warning('请选择整改部门')
  saving.value=true
  try{
    await patrolApi.assign(assignForm.id,{departmentId:assignForm.departmentId,note:assignForm.note})
    ElMessage.success('已分派并提交推送')
    assignVisible.value=false
    await Promise.all([load(),loadOverview()])
  }finally{ saving.value=false }
}
function openReview(row:any){ Object.assign(reviewForm,{id:row.id,note:''}); reviewVisible.value=true }
async function submitReview(approved:boolean){
  saving.value=true
  try{
    await patrolApi.review(reviewForm.id,{approved,note:reviewForm.note})
    ElMessage.success(approved?'已闭环':'已驳回')
    reviewVisible.value=false
    await Promise.all([load(),loadOverview()])
  }finally{ saving.value=false }
}
async function removeRow(row:any){
  try{ await ElMessageBox.confirm(`确认彻底删除问题单 ${row.issueNo}？照片与流转记录会一并删除，且不可恢复。`,'删除测试记录',{type:'warning',confirmButtonText:'彻底删除',confirmButtonClass:'el-button--danger'}) }
  catch{ return }
  await patrolApi.removeIssue(row.id)
  ElMessage.success('已删除')
  await Promise.all([load(),loadOverview()])
}
onMounted(async()=>{
  syncMobile()
  window.addEventListener('resize', syncMobile)
  await Promise.all([load(),loadOverview(),loadDepartments()])
  // 群消息里的「分配整改部门」链接：/patrol/issues?assign=问题ID，直接弹出该问题的分派框
  const assignId = Number(route.query.assign)
  if(assignId && canAssign.value){
    const row:any = await patrolApi.issue(assignId)
    if(row && row.status!=='CLOSED') openAssign(row)
  }
})
onBeforeUnmount(()=> window.removeEventListener('resize', syncMobile))
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
.stats{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-bottom:14px}
.stat{background:#fff;border:1px solid #ebeef5;border-radius:8px;padding:12px 16px;display:flex;flex-direction:column;gap:4px}
.stat b{font-size:22px}.stat span{color:#909399;font-size:12px}
.stat .warn{color:#e6a23c}.stat .danger,.danger{color:#f56c6c}
.filters{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px}
.pager{margin-top:14px;justify-content:flex-end}
.detail .kv{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px dashed #ebeef5;font-size:13px}
.detail .kv label{color:#909399}
.detail .kv.block{flex-direction:column;gap:6px}
.detail .kv p{margin:0;color:#303133;line-height:1.7;white-space:pre-wrap}
.photos{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}
.photo{width:96px;height:96px;border-radius:6px}
.logs{margin-top:14px;border-top:1px solid #ebeef5;padding-top:10px}
.log{font-size:12px;color:#606266;display:flex;gap:8px;padding:4px 0}
.log b{color:#409eff}
.log em{color:#909399;font-style:normal}
@media(max-width:900px){.stats{grid-template-columns:repeat(2,1fr)}}
@media(max-width:768px){.page{overflow-x:auto}.page-header{align-items:flex-start}.filters{flex-direction:column}.filters>*{width:100%!important}}
</style>
