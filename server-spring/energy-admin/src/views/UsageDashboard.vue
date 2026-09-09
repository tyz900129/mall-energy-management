<template>
  <div class="usage-dashboard" v-loading="loading" element-loading-background="rgba(7, 18, 37, .82)">
    <div class="screen-head">
      <div>
        <div class="screen-title"><span class="pulse"/>用量数据大屏</div>
        <div class="screen-subtitle">月度水电用量趋势、去年同期对比与分析维度占比</div>
      </div>
      <div class="screen-controls">
        <el-date-picker v-model="monthRange" type="monthrange" value-format="YYYY-MM" range-separator="至" start-placeholder="开始月份" end-placeholder="结束月份" :clearable="false" />
        <el-button type="primary" :loading="loading" @click="load">刷新数据</el-button>
      </div>
    </div>

    <el-alert v-if="errorText" class="screen-alert" :title="errorText" type="error" :closable="false" show-icon />

    <div class="kpi-grid">
      <div class="kpi-card electric">
        <div class="kpi-label">期间总用电量</div>
        <div class="kpi-value">{{ num(totals.electric) }}<small>kWh</small></div>
        <div class="kpi-foot"><span :class="yoyClass(electricYoy)">同比 {{ yoyText(electricYoy) }}</span><span>月均 {{ num(totals.electric / Math.max(trendRows.length, 1)) }}</span></div>
      </div>
      <div class="kpi-card water">
        <div class="kpi-label">期间总用水量</div>
        <div class="kpi-value">{{ num(totals.water) }}<small>m³</small></div>
        <div class="kpi-foot"><span :class="yoyClass(waterYoy)">同比 {{ yoyText(waterYoy) }}</span><span>月均 {{ num(totals.water / Math.max(trendRows.length, 1)) }}</span></div>
      </div>
      <div class="kpi-card neutral">
        <div class="kpi-label">覆盖月份</div>
        <div class="kpi-value">{{ trendRows.length }}<small>个月</small></div>
        <div class="kpi-foot"><span>{{ dateCaption }}</span><span>按自然月统计</span></div>
      </div>
      <div class="kpi-card neutral">
        <div class="kpi-label">分析维度</div>
        <div class="kpi-value">{{ dimensionSections.length }}<small>个</small></div>
        <div class="kpi-foot"><span>{{ categoryCount }} 个分类</span><span>当前设备归属</span></div>
      </div>
    </div>

    <div class="chart-grid">
      <section class="panel">
        <div class="panel-head"><div><b>月度用电趋势</b><span>实线为本期，虚线为去年同期</span></div><span class="unit">kWh</span></div>
        <UsageTrendChart :rows="trendRows" value-key="electric" previous-key="previousElectric" color="#f59e0b" unit="kWh" title="月度用电趋势" />
      </section>
      <section class="panel">
        <div class="panel-head"><div><b>月度用水趋势</b><span>实线为本期，虚线为去年同期</span></div><span class="unit">m³</span></div>
        <UsageTrendChart :rows="trendRows" value-key="water" previous-key="previousWater" color="#38bdf8" unit="m³" title="月度用水趋势" />
      </section>
    </div>

    <section class="panel month-share-panel">
      <div class="panel-head"><div><b>各月用量占比与同比</b><span>电、水单位不同，分别计算各自在所选期间内的占比</span></div></div>
      <div v-if="!trendRows.length" class="empty">所选月份暂无数据</div>
      <div v-else class="month-table-wrap">
        <table class="month-table">
          <thead><tr><th>月份</th><th>用电量</th><th>电量占比</th><th>用电同比</th><th>用水量</th><th>水量占比</th><th>用水同比</th></tr></thead>
          <tbody><tr v-for="row in trendRows" :key="row.month"><td>{{ row.monthLabel }}</td><td>{{ num(row.electric) }} kWh</td><td><div class="share-cell"><i class="electric-fill" :style="{width:pct(row.electric, totals.electric)}"/><span>{{ pct(row.electric, totals.electric) }}</span></div></td><td :class="yoyClass(row.electricYoy)">{{ yoyText(row.electricYoy) }}</td><td>{{ num(row.water) }} m³</td><td><div class="share-cell"><i class="water-fill" :style="{width:pct(row.water, totals.water)}"/><span>{{ pct(row.water, totals.water) }}</span></div></td><td :class="yoyClass(row.waterYoy)">{{ yoyText(row.waterYoy) }}</td></tr></tbody>
        </table>
      </div>
    </section>

    <div class="dimension-title"><div><b>分析维度占比</b><span>自动展示所有已启用的分析维度；新增维度后无需改造大屏</span></div></div>
    <div v-if="!dimensionSections.length" class="panel empty">暂无已启用的分析维度</div>
    <div v-else class="dimension-grid">
      <section v-for="section in dimensionSections" :key="section.id" class="panel dimension-panel">
        <div class="panel-head"><div><b>{{ section.name }}</b><span>{{ section.rows.length }} 个分类</span></div></div>
        <div v-if="!section.rows.length" class="empty compact">该维度暂无归属用量</div>
        <div v-for="row in section.rows" :key="row.id" class="dimension-row">
          <div class="dimension-name"><i :style="{background:row.color}"/>{{ row.name }}</div>
          <div class="dimension-measure"><span class="measure-label">电 {{ num(row.electric) }} kWh</span><div class="measure-track"><i class="electric-fill" :style="{width:pct(row.electric, section.electricTotal)}"/></div><b>{{ pct(row.electric, section.electricTotal) }}</b></div>
          <div class="dimension-measure"><span class="measure-label">水 {{ num(row.water) }} m³</span><div class="measure-track"><i class="water-fill" :style="{width:pct(row.water, section.waterTotal)}"/></div><b>{{ pct(row.water, section.waterTotal) }}</b></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { energyApi } from '@/api/energy'
import UsageTrendChart from '@/components/UsageTrendChart.vue'

const loading = ref(false), errorText = ref(''), monthRange = ref<string[]>(defaultMonths()), trendRows = ref<any[]>([]), dimensionSections = ref<any[]>([])
const totals = computed(() => trendRows.value.reduce((sum, row) => ({ electric: sum.electric + row.electric, water: sum.water + row.water, previousElectric: sum.previousElectric + row.previousElectric, previousWater: sum.previousWater + row.previousWater }), { electric: 0, water: 0, previousElectric: 0, previousWater: 0 }))
const electricYoy = computed(() => yoy(totals.value.electric, totals.value.previousElectric))
const waterYoy = computed(() => yoy(totals.value.water, totals.value.previousWater))
const categoryCount = computed(() => dimensionSections.value.reduce((sum, section) => sum + section.rows.length, 0))
const dateCaption = computed(() => monthRange.value?.length === 2 ? `${monthRange.value[0]} 至 ${monthRange.value[1]}` : '-')

function defaultMonths() { const now = new Date(), end = new Date(now.getFullYear(), now.getMonth(), 0), start = new Date(end.getFullYear(), end.getMonth() - 11, 1); return [monthKey(start), monthKey(end)] }
function monthKey(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
function parseMonth(value: string) { const [year, month] = value.split('-').map(Number); return new Date(year, month - 1, 1) }
function apiRange(range: string[]) { const start = parseMonth(range[0]), endMonth = parseMonth(range[1]), end = new Date(endMonth.getFullYear(), endMonth.getMonth() + 1, 0); return { startDate: `${range[0]}-01`, endDate: `${monthKey(end)}-${String(end.getDate()).padStart(2, '0')}` } }
function previousYearRange(range: string[]) { return range.map(value => { const date = parseMonth(value); date.setFullYear(date.getFullYear() - 1); return monthKey(date) }) }
function monthsBetween(range: string[]) { const result: string[] = [], cursor = parseMonth(range[0]), end = parseMonth(range[1]); while (cursor <= end && result.length < 60) { result.push(monthKey(cursor)); cursor.setMonth(cursor.getMonth() + 1) } return result }
function responseMap(rows: any[]) { return new Map((rows || []).map(row => [String(row.period_start || row.periodStart).slice(0, 7), row])) }
function value(row: any, key: string) { return Number(row?.[key] ?? row?.[key.replace(/_([a-z])/g, (_m: string, c: string) => c.toUpperCase())]) || 0 }
function yoy(current: number, previous: number) { return previous > 0 ? (current - previous) / previous * 100 : null }
function num(value: any) { return (Number(value) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }
function pct(value: any, total: any) { return `${total > 0 ? (Number(value) / Number(total) * 100).toFixed(1) : '0.0'}%` }
function yoyText(value: number | null) { return value === null ? '无同期数据' : `${value >= 0 ? '↑' : '↓'} ${Math.abs(value).toFixed(1)}%` }
function yoyClass(value: number | null) { return value === null ? 'yoy-flat' : value > 0 ? 'yoy-up' : value < 0 ? 'yoy-down' : 'yoy-flat' }

async function load() {
  if (!monthRange.value?.length || monthRange.value.length !== 2) return
  loading.value = true; errorText.value = ''
  try {
    const currentParams = { ...apiRange(monthRange.value), period: 'MONTH' }
    const priorParams = { ...apiRange(previousYearRange(monthRange.value)), period: 'MONTH' }
    const [current, previous, dimensions]: any[] = await Promise.all([energyApi.analysis(currentParams), energyApi.analysis(priorParams), energyApi.dimensions()])
    const currentMap = responseMap(current?.trend), previousMap = responseMap(previous?.trend)
    trendRows.value = monthsBetween(monthRange.value).map(month => {
      const date = parseMonth(month), priorDate = new Date(date); priorDate.setFullYear(priorDate.getFullYear() - 1)
      const currentRow = currentMap.get(month), previousRow = previousMap.get(monthKey(priorDate))
      const electric = value(currentRow, 'electric_usage'), water = value(currentRow, 'water_usage'), previousElectric = value(previousRow, 'electric_usage'), previousWater = value(previousRow, 'water_usage')
      return { month, monthLabel: `${date.getFullYear()}年${date.getMonth() + 1}月`, electric, water, previousElectric, previousWater, electricYoy: yoy(electric, previousElectric), waterYoy: yoy(water, previousWater) }
    })
    const enabledDimensions = (dimensions || []).filter((dimension: any) => dimension.status === undefined || Number(dimension.status) === 1)
    const results = await Promise.all(enabledDimensions.map((dimension: any) => energyApi.analysis({ ...currentParams, dimensionId: dimension.id }).then((analysis: any) => ({ dimension, analysis }))))
    dimensionSections.value = results.map(({ dimension, analysis }: any) => {
      const rows = (analysis?.dimensionTotals || []).map((row: any) => ({ id: row.dimensionValueId, name: row.dimensionValueName || '未命名', color: row.color || '#38bdf8', electric: Number(row.electricUsage) || 0, water: Number(row.waterUsage) || 0 })).filter((row: any) => row.electric > 0 || row.water > 0)
      return { id: dimension.id, name: dimension.dimension_name, rows, electricTotal: rows.reduce((sum: number, row: any) => sum + row.electric, 0), waterTotal: rows.reduce((sum: number, row: any) => sum + row.water, 0) }
    })
  } catch (error: any) {
    errorText.value = error?.response?.data?.message || error?.message || '数据加载失败'
    ElMessage.error(errorText.value)
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.usage-dashboard{min-height:calc(100vh - 60px);padding:20px;color:#dbeafe;background:radial-gradient(circle at 12% 0,rgba(14,116,144,.2),transparent 30%),radial-gradient(circle at 92% 8%,rgba(37,99,235,.18),transparent 32%),#071225;border-radius:10px}.screen-head,.panel-head,.kpi-foot,.dimension-title{display:flex;align-items:center;justify-content:space-between}.screen-head{gap:18px;margin-bottom:18px}.screen-title{font-size:24px;font-weight:750;letter-spacing:1px}.screen-subtitle{color:#7f94b2;font-size:13px;margin:6px 0 0 16px}.pulse{display:inline-block;width:9px;height:9px;margin-right:8px;border-radius:50%;background:#22d3ee;box-shadow:0 0 0 5px rgba(34,211,238,.12),0 0 14px #22d3ee}.screen-controls{display:flex;gap:10px}.screen-controls :deep(.el-date-editor){width:300px}.screen-controls :deep(.el-range-input),.screen-controls :deep(.el-range-separator){color:#dbeafe}.screen-controls :deep(.el-date-editor.el-input__wrapper){background:#0b1c35;box-shadow:0 0 0 1px #1e3a5f inset}.screen-alert{margin-bottom:14px}.kpi-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:14px}.kpi-card,.panel{border:1px solid rgba(71,108,151,.35);background:linear-gradient(145deg,rgba(15,35,63,.92),rgba(9,24,45,.94));box-shadow:0 12px 30px rgba(0,0,0,.12);border-radius:10px}.kpi-card{padding:18px;position:relative;overflow:hidden}.kpi-card:after{content:"";position:absolute;width:80px;height:80px;right:-25px;top:-25px;border-radius:50%;background:var(--glow);filter:blur(8px)}.kpi-card.electric{--glow:rgba(245,158,11,.17);border-top-color:#f59e0b}.kpi-card.water{--glow:rgba(56,189,248,.18);border-top-color:#38bdf8}.kpi-card.neutral{--glow:rgba(99,102,241,.14);border-top-color:#6366f1}.kpi-label{color:#91a4bf;font-size:13px}.kpi-value{font:700 28px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;margin:7px 0}.kpi-value small{font:12px/1.2 sans-serif;color:#7890ad;margin-left:7px}.kpi-foot{font-size:12px;color:#7086a4;gap:8px}.chart-grid,.dimension-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.panel{padding:16px;min-width:0}.panel-head{margin-bottom:8px}.panel-head b,.dimension-title b{font-size:16px}.panel-head div span,.dimension-title div span{display:block;color:#7086a4;font-size:12px;margin-top:5px}.unit{color:#64748b;font-size:12px}.month-share-panel{margin-top:14px}.month-table-wrap{overflow:auto;max-height:460px}.month-table{width:100%;min-width:920px;border-collapse:collapse;font-size:12px}.month-table th{position:sticky;top:0;background:#10233d;color:#8fa4c0;font-weight:600;text-align:left;padding:11px 10px;z-index:1}.month-table td{border-top:1px solid rgba(71,108,151,.22);padding:10px;color:#c7d7ed}.share-cell{position:relative;width:110px;height:18px;border-radius:4px;background:rgba(51,65,85,.55);overflow:hidden}.share-cell i{position:absolute;inset:0 auto 0 0;opacity:.48}.share-cell span{position:relative;display:block;text-align:center;line-height:18px;color:#e2e8f0}.electric-fill{display:block;background:linear-gradient(90deg,#f59e0b,#fbbf24)}.water-fill{display:block;background:linear-gradient(90deg,#0284c7,#38bdf8)}.yoy-up{color:#fb7185!important}.yoy-down{color:#34d399!important}.yoy-flat{color:#94a3b8!important}.dimension-title{margin:22px 2px 12px}.dimension-panel{padding-bottom:8px}.dimension-row{padding:12px 0;border-top:1px solid rgba(71,108,151,.22)}.dimension-name{font-size:13px;font-weight:600;margin-bottom:9px}.dimension-name>i{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:7px;box-shadow:0 0 8px currentColor}.dimension-measure{display:grid;grid-template-columns:145px 1fr 52px;align-items:center;gap:9px;margin:7px 0;font-size:11px;color:#8da1bd}.dimension-measure b{text-align:right;color:#bdcde1}.measure-track{height:6px;background:rgba(51,65,85,.72);border-radius:4px;overflow:hidden}.measure-track i{height:100%;border-radius:4px}.empty{display:flex;align-items:center;justify-content:center;min-height:130px;color:#718096}.empty.compact{min-height:90px}@media(max-width:1100px){.kpi-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:768px){.usage-dashboard{padding:12px;border-radius:0;margin:-10px;min-height:calc(100vh - 60px)}.screen-head{align-items:flex-start;flex-direction:column}.screen-title{font-size:21px}.screen-controls{width:100%;flex-direction:column}.screen-controls :deep(.el-date-editor),.screen-controls .el-button{width:100%}.kpi-grid,.chart-grid,.dimension-grid{grid-template-columns:1fr}.kpi-card{padding:15px}.kpi-value{font-size:24px}.panel{padding:12px}.dimension-measure{grid-template-columns:120px 1fr 46px}.measure-label{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
</style>
