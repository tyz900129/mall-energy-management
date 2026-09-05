<template><div class="page"><div class="page-header"><div class="page-title">用量阅读分析</div></div><el-card><div class="toolbar"><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"/><el-select v-model="groupId" clearable placeholder="全部分组"><el-option v-for="g in groups" :key="g.id" :label="g.group_name" :value="g.id"/></el-select><el-button type="primary" @click="load">查询</el-button></div></el-card>
<el-row :gutter="14" class="summary"><el-col :span="8"><el-card><div class="label">总用量</div><div class="value">{{num(data.totalUsage)}}</div></el-card></el-col><el-col :span="8"><el-card><div class="label">有数据天数</div><div class="value">{{data.days||0}}</div></el-card></el-col><el-col :span="8"><el-card><div class="label">日均用量</div><div class="value">{{num((Number(data.totalUsage)||0)/(data.days||1))}}</div></el-card></el-col></el-row>
<el-row :gutter="14"><el-col :xs="24" :lg="13"><el-card><template #header><b>每日趋势</b></template><div v-if="!data.trend?.length" class="empty">暂无数据</div><div v-for="p in data.trend||[]" :key="p.usage_date" class="bar-row"><span>{{p.usage_date}}</span><div class="bar-bg"><i :style="{width:bar(p.total_usage)}"/></div><b>{{num(p.total_usage)}}</b></div></el-card></el-col><el-col :xs="24" :lg="11"><el-card><template #header><b>设备用量排行</b></template><el-table :data="data.ranking||[]" max-height="540"><el-table-column prop="device_name" label="设备" min-width="150" show-overflow-tooltip/><el-table-column prop="group_name" label="分组" width="100"/><el-table-column label="总量" width="100" align="right"><template #default="{row}">{{num(row.total_usage)}}</template></el-table-column><el-table-column label="日均" width="90" align="right"><template #default="{row}">{{num(row.avg_usage)}}</template></el-table-column></el-table></el-card></el-col></el-row></div></template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { energyApi } from '@/api/energy'

const groups = ref<any[]>([])
const groupId = ref<number>()
const data = ref<any>({ trend: [], ranking: [] })
const now = new Date()
const start = new Date(now.getFullYear(), now.getMonth() - 1, 1)
const end = new Date(now.getFullYear(), now.getMonth(), 0)
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const range = ref([fmt(start), fmt(end)])
const max = computed(() => Math.max(0, ...(data.value.trend || []).map((v: any) => Number(v.total_usage) || 0)))
const num = (v: any) => (Number(v) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const bar = (v: any) => `${max.value ? Math.max(1, Number(v) / max.value * 100) : 0}%`

async function load() {
  if (!range.value?.length) return
  data.value = (await energyApi.analysis({ startDate: range.value[0], endDate: range.value[1], groupId: groupId.value })) as any
}
onMounted(async () => {
  groups.value = (await energyApi.groups()) as any
  await load()
})
</script>
<style scoped>.page{padding:18px}.page-header{margin-bottom:14px}.page-title{font-size:22px;font-weight:700}.toolbar{display:flex;gap:10px}.toolbar .el-select{width:180px}.summary{margin:14px 0}.label{color:#909399}.value{font-size:28px;font-weight:700;margin-top:8px}.empty{text-align:center;color:#909399;padding:50px}.bar-row{display:grid;grid-template-columns:95px 1fr 90px;gap:10px;align-items:center;margin:11px 0;font-size:13px}.bar-row b{text-align:right}.bar-bg{height:12px;background:#f0f2f5;border-radius:6px;overflow:hidden}.bar-bg i{display:block;height:100%;background:linear-gradient(90deg,#409eff,#67c23a);border-radius:6px}</style>
