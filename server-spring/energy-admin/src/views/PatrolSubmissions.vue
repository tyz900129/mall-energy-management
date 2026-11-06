<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">巡场记录</div>
        <div class="page-note">按日查看点位提交记录；超级管理员可彻底删除测试记录（含照片与关联问题单）。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="全部日期" clearable @change="search" />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="submittedAt" label="提交时间" width="160">
        <template #default="{row}">{{formatTime(row.submittedAt)}}</template>
      </el-table-column>
      <el-table-column prop="area" label="区域" width="100" />
      <el-table-column prop="pointName" label="点位" min-width="140" />
      <el-table-column label="结果" width="90">
        <template #default="{row}">
          <el-tag :type="row.result==='ABNORMAL'?'danger':'success'" size="small">{{row.result==='ABNORMAL'?'异常':'正常'}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="问题描述" min-width="240" show-overflow-tooltip />
      <el-table-column prop="submitterName" label="提交人" width="120" />
      <el-table-column label="问题单" width="110">
        <template #default="{row}">
          <span v-if="row.issueId">{{issueLabel(row.issueStatus)}}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isSuperAdmin" label="操作" width="100" fixed="right">
        <template #default="{row}">
          <el-button link type="danger" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p:number)=>{query.page=p;load()}" />
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage,ElMessageBox } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const isSuperAdmin = computed(() => !!user.user?.roles?.includes('SUPER_ADMIN'))
const date = ref('')
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<any>({page:1,size:20})

function formatTime(value:string){ return value ? value.replace('T',' ').slice(0,16) : '-' }
function issueLabel(status:string){
  return ({UNASSIGNED:'待分派',ASSIGNED:'待整改',RECTIFYING:'整改中',REVIEWING:'待复核',CLOSED:'已闭环'} as Record<string,string>)[status] || '处理中'
}
async function load(){
  loading.value=true
  try{
    const data:any = await patrolApi.submissions({...query, date:date.value||undefined})
    rows.value = data?.items || []
    total.value = data?.total || 0
  }finally{ loading.value=false }
}
function search(){ query.page=1; load() }
async function removeRow(row:any){
  const extra = row.issueId ? '关联问题单、' : ''
  try{ await ElMessageBox.confirm(`确认彻底删除 ${row.pointName} 于 ${formatTime(row.submittedAt)} 的提交记录？${extra}照片会一并删除，且不可恢复。`,'删除测试记录',{type:'warning',confirmButtonText:'彻底删除',confirmButtonClass:'el-button--danger'}) }
  catch{ return }
  await patrolApi.removeSubmission(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
.pager{margin-top:14px;justify-content:flex-end}
@media(max-width:768px){.page{overflow-x:auto}.page-header{align-items:flex-start}.actions{flex-wrap:wrap}}
</style>
