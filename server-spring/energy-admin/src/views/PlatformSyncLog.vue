<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-title">同步日志详情</div><div class="sub">查看同步结果、数据量和本批次发现的异常</div></div>
      <el-button @click="router.push('/platform/sync')">返回同步日志</el-button>
    </div>
    <el-card class="selector">
      <el-select v-model="selectedId" filterable placeholder="请选择同步批次" style="width:100%" @change="selectBatch">
        <el-option v-for="item in batches" :key="item.id" :value="item.id" :label="`${item.batchNo}　${formatDateTime(item.startedAt)}　${enumLabel(item.status)}`" />
      </el-select>
    </el-card>
    <div v-loading="loading">
      <template v-if="detail?.batch">
        <el-card class="summary">
          <div class="summary-head">
            <div><div class="batch-no">{{ detail.batch.batchNo }}</div><div class="sub">批次 ID：{{ detail.batch.id }}</div></div>
            <el-tag :type="statusType(detail.batch.status)" size="large">{{ enumLabel(detail.batch.status) }}</el-tag>
          </div>
          <el-descriptions :column="isMobile?1:3" border>
            <el-descriptions-item label="开始时间">{{ formatDateTime(detail.batch.startedAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ formatDateTime(detail.batch.completedAt) }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ duration(detail.batch.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="触发方式">{{ enumLabel(detail.batch.triggerType) }}</el-descriptions-item>
            <el-descriptions-item label="异常数量">{{ detail.batch.exceptionCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="完成流程">{{ detail.completedWorkflowCount || 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="detail.batch.errorMessage" class="error" type="error" :closable="false" show-icon :title="detail.batch.errorMessage" />
        </el-card>
        <div class="metrics">
          <el-card><div class="metric-value">{{ detail.batch.accountCount || 0 }}</div><div class="metric-label">计费账户</div></el-card>
          <el-card><div class="metric-value">{{ detail.batch.deviceCount || 0 }}</div><div class="metric-label">设备</div></el-card>
          <el-card><div class="metric-value">{{ detail.batch.relationCount || 0 }}</div><div class="metric-label">账户设备关系</div></el-card>
          <el-card><div class="metric-value warn">{{ detail.batch.exceptionCount || 0 }}</div><div class="metric-label">本批异常</div></el-card>
        </div>
        <el-card class="exceptions">
          <template #header><div class="card-title">异常明细 <span class="sub">最多展示 500 条</span></div></template>
          <el-empty v-if="!detail.exceptions?.length" description="本批次没有发现异常" />
          <template v-else>
            <div v-if="isMobile" class="exception-cards">
              <div v-for="row in detail.exceptions" :key="row.id" class="exception-item">
                <div class="exception-head"><el-tag :type="severityType(row.severity)" size="small">{{ enumLabel(row.severity) }}</el-tag><span>{{ row.title }}</span></div>
                <div class="sub">{{ row.description || '无补充说明' }}</div>
                <div class="mini">设备：{{ row.platformDeviceId || '—' }}　账户：{{ row.accountName || '—' }}</div>
                <div class="mini">{{ formatDateTime(row.detectedAt) }}　{{ enumLabel(row.status) }}</div>
              </div>
            </div>
            <el-table v-else :data="detail.exceptions" border>
              <el-table-column label="等级" width="90"><template #default="{row}"><el-tag :type="severityType(row.severity)" size="small">{{ enumLabel(row.severity) }}</el-tag></template></el-table-column>
              <el-table-column prop="title" label="异常" min-width="220" />
              <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />
              <el-table-column prop="platformDeviceId" label="设备 ID" min-width="150" />
              <el-table-column prop="accountName" label="账户名称" min-width="180" />
              <el-table-column label="发现时间" width="180"><template #default="{row}">{{ formatDateTime(row.detectedAt) }}</template></el-table-column>
              <el-table-column label="状态" width="90"><template #default="{row}">{{ enumLabel(row.status) }}</template></el-table-column>
            </el-table>
          </template>
        </el-card>
      </template>
      <el-empty v-else-if="!loading" description="请选择要查看的同步批次" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { platformApi } from '@/api/platform'
import { enumLabel, formatDateTime } from '@/utils/format'

const route=useRoute(),router=useRouter(),batches=ref<any[]>([]),selectedId=ref<number>(),detail=ref<any>(),loading=ref(false),isMobile=ref(innerWidth<=768)
const resize=()=>isMobile.value=innerWidth<=768
const statusType=(v:string)=>v==='SUCCESS'?'success':v==='RUNNING'?'warning':'danger'
const severityType=(v:string)=>v==='INFO'?'info':v==='WARN'?'warning':'danger'
const duration=(ms?:number)=>ms==null?'—':ms<1000?`${ms} 毫秒`:ms<60000?`${(ms/1000).toFixed(1)} 秒`:`${Math.floor(ms/60000)} 分 ${Math.round(ms%60000/1000)} 秒`
async function loadBatches(){const r:any=await platformApi.listBatches({pageNum:1,pageSize:100});batches.value=r.records||[];const queryId=Number(route.query.id);selectedId.value=(queryId&&batches.value.some(x=>x.id===queryId))?queryId:batches.value[0]?.id;if(selectedId.value)await loadDetail()}
async function loadDetail(){if(!selectedId.value)return;loading.value=true;try{detail.value=await platformApi.syncLogDetail(selectedId.value)}finally{loading.value=false}}
function selectBatch(){router.replace({path:'/platform/sync/log',query:{id:selectedId.value}});loadDetail()}
onMounted(()=>{addEventListener('resize',resize);loadBatches()});onBeforeUnmount(()=>removeEventListener('resize',resize))
</script>

<style scoped>
.sub{color:#909399;font-size:13px;margin-top:4px}.selector,.summary,.exceptions{margin-bottom:14px}.summary-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.batch-no{font-size:20px;font-weight:700}.error{margin-top:14px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:14px}.metric-value{font-size:28px;font-weight:700;color:#409eff}.metric-value.warn{color:#e6a23c}.metric-label{color:#606266;margin-top:6px}.card-title{font-weight:700}.mini{font-size:12px;color:#909399;margin-top:8px}.exception-item{padding:12px 0;border-bottom:1px solid #ebeef5}.exception-item:last-child{border-bottom:0}.exception-head{display:flex;align-items:flex-start;gap:8px;font-weight:600;margin-bottom:8px}
@media(max-width:768px){.page-header{align-items:flex-start}.metrics{grid-template-columns:repeat(2,1fr)}.metric-value{font-size:24px}.summary-head{align-items:flex-start}.batch-no{font-size:16px;word-break:break-all}.selector :deep(.el-select__wrapper){min-height:44px}}
</style>
