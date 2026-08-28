<template>
  <div class="page">
    <div class="page-header"><div class="page-title">异常中心</div></div>
    <el-card>
      <el-form inline>
        <el-form-item label="状态"><el-select v-model="query.status" clearable style="width:130px"><el-option v-for="v in statuses" :key="v" :label="enumLabel(v)" :value="v" /></el-select></el-form-item>
        <el-form-item label="等级"><el-select v-model="query.severity" clearable style="width:130px"><el-option v-for="v in severities" :key="v" :label="enumLabel(v)" :value="v" /></el-select></el-form-item>
        <el-form-item><el-input v-model="query.keyword" clearable placeholder="设备号/标题/账户" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="detectedAt" label="发现时间" width="180"><template #default="{row}">{{ formatDateTime(row.detectedAt) }}</template></el-table-column>
        <el-table-column prop="severity" label="等级" width="90"><template #default="{row}"><el-tag :type="severityType(row.severity)">{{ enumLabel(row.severity) }}</el-tag></template></el-table-column>
        <el-table-column prop="status" label="状态" width="100"><template #default="{row}">{{ enumLabel(row.status) }}</template></el-table-column>
        <el-table-column prop="exceptionType" label="类型" min-width="210" show-overflow-tooltip />
        <el-table-column prop="platformDeviceId" label="设备号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="syncBatchId" label="批次" width="80" />
        <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openHandle(row)">处理</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" style="margin-top:12px;justify-content:flex-end" @change="load" />
    </el-card>
    <el-dialog v-model="dialog.visible" title="处理异常" width="520px">
      <el-form label-width="90px">
        <el-form-item label="异常"><span>{{ dialog.row?.title }}</span></el-form-item>
        <el-form-item label="状态"><el-select v-model="dialog.status" style="width:100%"><el-option v-for="v in statuses" :key="v" :label="enumLabel(v)" :value="v" /></el-select></el-form-item>
        <el-form-item label="处理结果"><el-input v-model="dialog.result" type="textarea" :rows="4" maxlength="255" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exceptionApi } from '@/api/exception'
import { enumLabel, formatDateTime } from '@/utils/format'
const statuses = ['OPEN','ACK','HANDLING','RESOLVED','IGNORED']
const severities = ['INFO','WARN','ERROR','CRITICAL']
const query = reactive({ status: 'OPEN', severity: '', keyword: '' })
const page = reactive({ current: 1, size: 20 }); const rows=ref<any[]>([]); const total=ref(0); const loading=ref(false); const saving=ref(false)
const dialog=reactive<any>({visible:false,row:null,status:'ACK',result:''})
function severityType(v:string){ return v==='CRITICAL'?'danger':v==='ERROR'?'danger':v==='WARN'?'warning':'info' }
async function load(){ loading.value=true; try { const r:any=await exceptionApi.list({...query,pageNum:page.current,pageSize:page.size}); rows.value=r.records||[]; total.value=Number(r.total)||0 } finally { loading.value=false } }
function search(){ page.current=1; load() }
function openHandle(row:any){ dialog.row=row; dialog.status=row.status==='OPEN'?'ACK':row.status; dialog.result=row.handleResult||''; dialog.visible=true }
async function save(){ saving.value=true; try { await exceptionApi.updateStatus(dialog.row.id,{status:dialog.status,handleResult:dialog.result}); ElMessage.success('处理状态已更新'); dialog.visible=false; load() } finally { saving.value=false } }
onMounted(load)
</script>

