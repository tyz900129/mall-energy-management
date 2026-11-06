<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">履职报表</div>
        <div class="page-note">按日统计各处室任务完成与问题闭环情况，用于值班干部履职考核。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div class="sec-title">任务完成情况（检查任务，不含交接）</div>
      <el-table :data="taskStats" border>
        <el-table-column prop="deptName" label="处室" :width="isMobile ? 90 : 110" />
        <el-table-column v-if="!isMobile" label="表单" width="80">
          <template #default="{row}"><el-tag size="small" :type="row.formType==='FOOD'?'warning':'primary'" effect="plain">{{ row.formType==='FOOD'?'餐饮':'零售' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="total" label="任务数" width="80" align="center" />
        <el-table-column prop="done" label="已完成" width="80" align="center" />
        <el-table-column prop="lateDone" label="迟报" :width="isMobile ? 66 : 80" align="center">
          <template #default="{row}"><span :class="{warn:row.lateDone>0}">{{row.lateDone}}</span></template>
        </el-table-column>
        <el-table-column prop="overdue" label="逾期未报" :width="isMobile ? 84 : 90" align="center">
          <template #default="{row}"><span :class="{danger:row.overdue>0}">{{row.overdue}}</span></template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="exempt" label="豁免" width="70" align="center" />
        <el-table-column label="完成率" width="100" align="center">
          <template #default="{row}">{{ rate(row.done, row.total - row.exempt) }}</template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="passCount" label="合格项" width="80" align="center" />
        <el-table-column v-if="!isMobile" prop="failCount" label="不合格项" width="90" align="center">
          <template #default="{row}"><span :class="{danger:row.failCount>0}">{{row.failCount}}</span></template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="skipCount" label="不适用/漏检" width="110" align="center" />
      </el-table>

      <div class="sec-title" style="margin-top:24px">问题闭环情况（按上报日期）</div>
      <el-table :data="issueStats" border>
        <el-table-column prop="deptName" label="处室" width="110" />
        <el-table-column prop="total" label="新增问题" width="90" align="center" />
        <el-table-column prop="closed" label="已闭环" width="90" align="center" />
        <el-table-column label="闭环率" width="100" align="center">
          <template #default="{row}">{{ rate(row.closed, row.total) }}</template>
        </el-table-column>
        <el-table-column prop="overdue" label="当前逾期未闭环" width="140" align="center">
          <template #default="{row}"><span :class="{danger:row.overdue>0}">{{row.overdue}}</span></template>
        </el-table-column>
        <el-table-column prop="repeated" label="7日内复发" :width="isMobile ? 96 : 110" align="center">
          <template #default="{row}"><span :class="{warn:row.repeated>0}">{{row.repeated}}</span></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useIsMobile } from '@/composables/useIsMobile'

const isMobile = useIsMobile()
const date = ref(localDate())
const taskStats = ref<any[]>([])
const issueStats = ref<any[]>([])
const loading = ref(false)

function localDate() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
async function load() {
  loading.value = true
  try {
    const data: any = await opsPatrolApi.dailyReport(date.value)
    taskStats.value = data?.taskStats || []
    issueStats.value = data?.issueStats || []
  } finally { loading.value = false }
}
function rate(part: number, total: number) {
  if (!total) return '-'
  return Math.round((part || 0) * 100 / total) + '%'
}
onMounted(load)
</script>

<style scoped>
.actions{display:flex;align-items:center;gap:10px}
.sec-title{font-size:14px;font-weight:600;margin-bottom:10px}
.danger{color:#f56c6c;font-weight:600}
.warn{color:#e6a23c;font-weight:600}
</style>
