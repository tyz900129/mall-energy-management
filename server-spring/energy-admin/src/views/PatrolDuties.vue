<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">值班排班</div>
        <div class="page-note">维护每日值班干部与备班人员（备班支持多人），用于巡场推送文案；未排班时显示“物业管理员”。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" @change="load" />
        <el-button v-if="canManage" type="primary" @click="edit()">新增排班</el-button>
      </div>
    </div>

    <el-alert v-if="round.entryToken" type="success" :closable="false" class="round-alert">
      <template #title>
        今日巡场入口：{{ entryUrl }}　
        <el-button link type="primary" @click="copy(entryUrl)">复制</el-button>
        <el-button link type="primary" @click="openEntry">打开</el-button>
        <el-button v-if="canManage" link type="warning" :loading="pushing" @click="pushNow">立即推送群消息</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="dutyDate" label="日期" width="140" />
      <el-table-column prop="userName" label="值班干部" width="140" />
      <el-table-column prop="backupNames" label="备班" min-width="180"><template #default="{row}">{{row.backupNames||'-'}}</template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160"><template #default="{row}">{{row.remark||'-'}}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="150" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.editing?'编辑排班':'新增排班'" width="520px">
      <el-form label-position="top">
        <el-form-item label="值班日期" required>
          <el-date-picker v-model="form.dutyDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="form.editing" />
        </el-form-item>
        <el-form-item label="值班干部" required>
          <el-select v-model="form.userId" filterable placeholder="选择人员" style="width:100%" :disabled="form.editing" @change="onUserChange">
            <el-option v-for="u in users" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备班人员（可多选）">
          <el-select v-model="form.backupUserIds" multiple clearable filterable collapse-tags collapse-tags-tooltip placeholder="可选，支持多人" style="width:100%">
            <el-option v-for="u in users" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const canManage = computed(() => user.hasPermission('patrol.config'))
const month = ref(new Date().toISOString().slice(0,7))
const rows = ref<any[]>([])
const users = ref<any[]>([])
const round = ref<any>({})
const loading = ref(false)
const saving = ref(false)
const pushing = ref(false)
const visible = ref(false)
const form = reactive<any>({editing:false,dutyDate:'',userId:null,userName:'',backupUserIds:[],remark:''})
const entryUrl = computed(() => round.value.entryToken ? `${location.origin}/patrol/entry?token=${round.value.entryToken}` : '')

async function load(){
  loading.value=true
  try{
    const data:any = await patrolApi.duties(month.value)
    rows.value = data?.rows || []
    users.value = data?.users || []
  }finally{ loading.value=false }
}
async function loadRound(){ round.value = await patrolApi.todayRound() as any || {} }
function edit(row?:any){
  const ids = (row?.backupUsers || []).map((name:string) => users.value.find(u=>u.displayName===name)?.id).filter((id:any)=>id!=null)
  Object.assign(form,{
    editing: !!row,
    dutyDate: row?.dutyDate || new Date().toISOString().slice(0,10),
    userId: row?.userId || null,
    userName: row?.userName || '',
    backupUserIds: ids,
    remark: row?.remark || ''
  })
  visible.value=true
}
function onUserChange(id:number){ form.userName = users.value.find(u=>u.id===id)?.displayName || '' }
async function save(){
  if(!form.dutyDate) return ElMessage.warning('请选择值班日期')
  if(!form.userName) return ElMessage.warning('请选择值班人员')
  saving.value=true
  try{
    await patrolApi.saveDuty({
      dutyDate: form.dutyDate,
      userId: form.userId,
      userName: form.userName,
      backupUsers: form.backupUserIds.map((id:number) => ({ id, name: users.value.find(u=>u.id===id)?.displayName || '' })).filter((u:any)=>u.name),
      remark: form.remark
    })
    ElMessage.success('已保存')
    visible.value=false
    await load()
    await loadRound()
  }finally{ saving.value=false }
}
async function remove(row:any){
  await ElMessageBox.confirm(`确认删除 ${row.dutyDate} 的排班？`,'删除确认',{type:'warning'})
  await patrolApi.deleteDuty(row.id)
  ElMessage.success('已删除')
  await load()
}
async function pushNow(){
  pushing.value=true
  try{
    await patrolApi.push()
    ElMessage.success('已提交推送，请在群内查看')
    await loadRound()
  }finally{ pushing.value=false }
}
async function copy(url:string){
  try{ await navigator.clipboard.writeText(url); ElMessage.success('链接已复制') }
  catch{ ElMessage.warning('复制失败') }
}
function openEntry(){ if(entryUrl.value) window.open(entryUrl.value,'_blank') }
onMounted(async()=>{ await Promise.all([load(),loadRound()]) })
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
.round-alert{margin-bottom:14px}
@media(max-width:768px){.page{overflow-x:auto}.page-header{align-items:flex-start}.actions{flex-wrap:wrap}}
</style>
