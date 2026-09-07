<template><div class="page"><div class="page-header"><div><div class="page-title">历史用量同步</div><div class="hint">长期保存月累计用量；日明细仅保留最近三个自然月</div></div></div>
<el-card><el-form inline><el-form-item label="开始日期"><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="设备分组"><el-select v-model="form.groupId" clearable placeholder="全部设备"><el-option v-for="g in groups" :key="g.id" :label="g.group_name" :value="g.id"/></el-select></el-form-item><el-form-item><el-button type="primary" :loading="syncing" :disabled="hasRunning" @click="sync">{{hasRunning?'已有同步正在执行':'开始同步'}}</el-button></el-form-item></el-form>
<el-alert title="测量点由系统按设备产品自动识别：电表同步正向有功电能，水表同步累计流量，燃气表同步累计用量，无需人工配置。系统按年度获取月累计值；最近三个月日明细按每批最多 50 台同步。" type="info" :closable="false"/></el-card>
<el-card class="history"><template #header><b>最近同步记录</b></template><el-table :data="batches" border><el-table-column prop="batch_no" label="批次" width="190"/><el-table-column prop="start_date" label="开始" width="110"/><el-table-column prop="end_date" label="结束" width="110"/><el-table-column prop="requested" label="设备" width="75"/><el-table-column prop="succeeded" label="成功" width="75"/><el-table-column prop="failed" label="失败" width="75"/><el-table-column prop="monthly_points" label="月数据" width="85"/><el-table-column prop="daily_points" label="日数据" width="85"/><el-table-column prop="purged_daily_points" label="清理日数据" width="100"/><el-table-column prop="status" label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':row.status==='RUNNING'?'warning':row.status==='PARTIAL'?'warning':'danger'">{{enumLabel(row.status)}}</el-tag></template></el-table-column><el-table-column prop="error_message" label="错误信息" show-overflow-tooltip/><el-table-column label="执行时间" width="180"><template #default="{row}">{{formatDateTime(row.started_at)}}</template></el-table-column><el-table-column label="完成时间" width="180"><template #default="{row}">{{formatDateTime(row.completed_at)}}</template></el-table-column></el-table></el-card></div></template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { energyApi } from '@/api/energy'
import { enumLabel, formatDateTime } from '@/utils/format'

const groups = ref<any[]>([])
const batches = ref<any[]>([])
const syncing = ref(false)
const hasRunning = computed(() => batches.value.some(row => row.status === 'RUNNING'))
let refreshTimer: number | undefined
const now = new Date()
const first = new Date(now.getFullYear(), now.getMonth() - 1, 1)
const last = new Date(now.getFullYear(), now.getMonth(), 0)
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const form = reactive<any>({ startDate: fmt(first), endDate: fmt(last), groupId: undefined })
async function load() {
  groups.value = (await energyApi.groups()) as any
  await loadBatches()
}
async function loadBatches() { batches.value = (await energyApi.syncBatches()) as any }
async function sync() {
  await ElMessageBox.confirm('将按年度获取月累计值，并只补充最近三个月日明细。全部设备同步仍需一定时间，确定开始？', '历史用量同步')
  syncing.value = true
  try {
    const r: any = await energyApi.syncUsage(form)
    ElMessage.success(`完成：成功 ${r.succeeded} 台，失败 ${r.failed} 台；月数据 ${r.monthlyPoints} 条，近三个月日数据 ${r.dailyPoints} 条`)
    await load()
  } finally { syncing.value = false; await loadBatches() }
}
onMounted(async()=>{await load();refreshTimer=window.setInterval(loadBatches,10000)})
onUnmounted(()=>{if(refreshTimer)window.clearInterval(refreshTimer)})
</script>
<style scoped>.page{padding:18px}.page-header{margin-bottom:14px}.page-title{font-size:22px;font-weight:700}.hint{color:#909399;font-size:13px;margin-top:3px}.history{margin-top:14px}.el-select{width:180px}</style>
