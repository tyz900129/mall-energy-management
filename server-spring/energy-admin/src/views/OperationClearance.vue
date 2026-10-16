<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">商户日清记录</div>
        <div class="page-note">日清提交时段：{{ scheduleText }}；仓库和非在用铺位不参与统计。</div>
      </div>
      <el-button v-if="canConfigure" type="primary" plain @click="openSettings">配置提交时间</el-button>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="铺位或商户名称" clearable/>
      <el-date-picker v-model="query.periodDate" type="date" value-format="YYYY-MM-DD" placeholder="日清日期" clearable/>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <div class="table-card">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="periodDate" label="日清日期" width="115"/>
        <el-table-column prop="stallCode" label="铺位" width="110"/>
        <el-table-column prop="merchantName" label="商户" min-width="160"/>
        <el-table-column prop="submitterName" label="提交人" width="110"/>
        <el-table-column prop="managerName" label="责任商管" width="110"/>
        <el-table-column label="检查结果" min-width="220"><template #default="{row}"><el-tag v-for="item in checks(row)" :key="item.name" :type="item.ok?'success':'danger'" size="small" class="tag">{{item.name}}{{item.ok?'已关':'未关'}}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='NORMAL'?'success':'danger'">{{row.status==='NORMAL'?'正常':'异常'}}</el-tag></template></el-table-column>
        <el-table-column label="异常说明" min-width="170"><template #default="{row}">{{row.exceptionDescription||'-'}}</template></el-table-column>
        <el-table-column label="提交时间" width="175"><template #default="{row}">{{formatDateTime(row.submittedAt)}}</template></el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @change="load"/>
    </div>

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
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {operationClearanceApi} from '@/api/operation'
import {formatDateTime} from '@/utils/format'
import {useUserStore} from '@/stores/user'

const user=useUserStore(),rows=ref<any[]>([]),total=ref(0),loading=ref(false),scheduleText=ref('加载中')
const settingsVisible=ref(false),settingsSaving=ref(false),settingsForm=reactive({startTime:'21:00',endTime:'09:00'})
const query=reactive({keyword:'',periodDate:'',pageNum:1,pageSize:20})
const canConfigure=computed(()=>user.hasPermission('operation.clearance.setting.manage'))

async function load(){loading.value=true;try{const result:any=await operationClearanceApi.page(query);rows.value=result.records||[];total.value=Number(result.total)||0}finally{loading.value=false}}
async function loadSchedule(){const result:any=await operationClearanceApi.schedule();scheduleText.value=result.scheduleText||`${result.startTime||'21:00'}至${result.endTime||'09:00'}`}
function search(){query.pageNum=1;load()}
const checks=(row:any)=>[{name:'电',ok:!!row.electricityOff},{name:'水',ok:!!row.waterOff},{name:'气',ok:!!row.gasOff},{name:'门窗',ok:!!row.doorClosed}]
async function openSettings(){const result:any=await operationClearanceApi.settings();settingsForm.startTime=result.startTime;settingsForm.endTime=result.endTime;settingsVisible.value=true}
async function saveSettings(){if(!settingsForm.startTime||!settingsForm.endTime)return ElMessage.warning('请选择开始和结束时间');if(settingsForm.startTime===settingsForm.endTime)return ElMessage.warning('开始时间和结束时间不能相同');settingsSaving.value=true;try{const result:any=await operationClearanceApi.updateSettings(settingsForm);scheduleText.value=result.scheduleText;settingsVisible.value=false;ElMessage.success('日清提交时间已更新')}finally{settingsSaving.value=false}}
onMounted(async()=>{await Promise.all([load(),loadSchedule()])})
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}.toolbar{display:flex;gap:10px;margin-bottom:12px}.toolbar .el-input{width:250px}.tag{margin:2px}.pager{margin-top:12px;justify-content:flex-end}.settings-form{margin-top:18px}@media(max-width:768px){.page-header{align-items:flex-start;gap:12px}.toolbar{flex-wrap:wrap}.toolbar>*{width:100%!important}.table-card{overflow-x:auto}:deep(.el-dialog){width:calc(100% - 20px)!important}}
</style>
