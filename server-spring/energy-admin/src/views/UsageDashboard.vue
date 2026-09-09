<template>
  <div class="usage-dashboard" v-loading="loading" element-loading-background="rgba(7, 18, 37, .82)">
    <div class="screen-head">
      <div>
        <div class="screen-title"><span class="pulse"/>用量数据大屏</div>
        <div class="screen-subtitle">月度水电用量趋势、去年同期对比与分析维度占比</div>
      </div>
      <div class="screen-controls">
        <el-select v-model="preset" aria-label="快捷统计期间" style="width:150px" @change="choosePeriod"><el-option label="近12个完整月" value="recent"/><el-option label="本年已结束月份" value="year"/><el-option label="上一年度" value="lastYear"/><el-option label="自定义月份" value="custom"/></el-select>
        <el-date-picker v-model="monthRange" type="monthrange" value-format="YYYY-MM" range-separator="至" start-placeholder="开始月份" end-placeholder="结束月份" :clearable="false" :disabled-date="futureMonth" @change="preset='custom'" />
        <el-button type="primary" :loading="loading" @click="load">刷新数据</el-button>
      </div>
    </div>

    <el-alert v-if="errorText" class="screen-alert" :title="errorText" type="error" :closable="false" show-icon />

    <div class="data-note" role="status">{{ dateCaption }} · 本页更新：{{ updatedAt || '尚未加载' }}<br/>按已同步月度用量汇总，离线或同步过滤设备已有的历史用量仍计入。月份有记录不代表全部设备同步完整；同比为总量对比，设备范围变化也会影响结果。</div>
    <div v-if="qualityNote" class="data-note warning">{{ qualityNote }}</div>
    <div class="kpi-grid">
      <div class="kpi-card electric">
        <div class="kpi-label">期间总用电量</div>
        <div class="kpi-value">{{ displayTotal('electric') }}<small>kWh</small></div>
        <div class="kpi-foot"><span :class="yoyClass(electricYoy)">同比 {{ periodYoyText(electricYoy) }}</span><span>有数据月均 {{ average('electric') }}</span></div>
      </div>
      <div class="kpi-card water">
        <div class="kpi-label">期间总用水量</div>
        <div class="kpi-value">{{ displayTotal('water') }}<small>m³</small></div>
        <div class="kpi-foot"><span :class="yoyClass(waterYoy)">同比 {{ periodYoyText(waterYoy) }}</span><span>有数据月均 {{ average('water') }}</span></div>
      </div>
      <div class="kpi-card neutral">
        <div class="kpi-label">有数据的月份</div>
        <div class="kpi-value">{{ coverage }}<small>/ {{ trendRows.length }} 个月</small></div>
        <div class="kpi-foot"><span>电 {{ observedCount('electric') }} 月 / 水 {{ observedCount('water') }} 月</span><span>按记录统计</span></div>
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
      <div class="panel-head"><div><b>用量观察</b><span>帮助定位高用量月份与主要用量设备</span></div></div>
      <div class="insight-grid"><p v-for="line in insights" :key="line">{{ line }}</p></div>
    </section>
    <section class="panel month-share-panel">
      <div class="panel-head"><div><b>设备用量 TOP 10</b><span>所选期间内仍在设备台账中的设备；历史总量可能还包含已移除设备</span></div><el-radio-group v-model="rankingEnergy" size="small"><el-radio-button value="ELECTRIC">用电</el-radio-button><el-radio-button value="WATER">用水</el-radio-button></el-radio-group></div>
      <div v-if="!topDevices.length" class="empty compact">暂无设备排行数据</div>
      <div v-for="(device,i) in topDevices" :key="device.platform_device_id" class="rank-row"><span>{{ i + 1 }}</span><div><b>{{ device.device_name || device.platform_device_id }}</b><small>{{ device.platform_device_id }} · 有数据 {{ device.covered_periods }} 个月</small></div><strong>{{ num(device.total_usage) }} {{ rankingEnergy==='WATER'?'m³':'kWh' }}</strong><span>{{ pct(device.total_usage, rankingEnergy==='WATER'?totals.water:totals.electric) }}</span></div>
    </section>
    <section class="panel month-share-panel">
      <div class="panel-head"><div><b>各月用量占比与同比</b><span>电、水单位不同，分别计算各自在所选期间内的占比</span></div></div>
      <div v-if="!trendRows.length" class="empty">所选月份暂无数据</div>
      <div v-else class="month-table-wrap">
        <table class="month-table">
          <thead><tr><th>月份</th><th>用电量</th><th>电量占比</th><th>用电同比</th><th>用水量</th><th>水量占比</th><th>用水同比</th></tr></thead>
          <tbody><tr v-for="row in trendRows" :key="row.month"><td>{{ row.monthLabel }}<small v-if="row.incomplete">（未结束）</small></td><td>{{ num(row.electric) }} kWh</td><td><div class="share-cell"><i class="electric-fill" :style="{width:pct(row.electric, totals.electric)}"/><span>{{ pct(row.electric, totals.electric) }}</span></div></td><td :class="yoyClass(row.electricYoy)">{{ row.incomplete ? '本月未结束' : yoyText(row.electricYoy) }}</td><td>{{ num(row.water) }} m³</td><td><div class="share-cell"><i class="water-fill" :style="{width:pct(row.water, totals.water)}"/><span>{{ pct(row.water, totals.water) }}</span></div></td><td :class="yoyClass(row.waterYoy)">{{ row.incomplete ? '本月未结束' : yoyText(row.waterYoy) }}</td></tr></tbody>
        </table>
      </div>
    </section>

    <div class="dimension-title"><div><b>分析维度占比</b><span>按当前设备归属分析历史用量；每个维度独立计算，占比分母含未分类用量</span></div></div>
    <div v-if="!dimensionSections.length" class="panel empty">暂无已启用的分析维度</div>
    <div v-else class="dimension-grid">
      <section v-for="section in dimensionSections" :key="section.id" class="panel dimension-panel">
        <div class="panel-head"><div><b>{{ section.name }}</b><span>{{ section.rows.length }} 个分类</span></div></div>
        <div v-if="section.error" class="data-note warning">{{ section.error }}，请刷新重试</div>
        <div v-else-if="!section.rows.length" class="empty compact">该维度暂无归属用量</div>
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
import { observed, percentage, periodComparison, dimensionRows } from '@/utils/usageDashboard'
import { formatDateTime } from '@/utils/format'

const preset = ref('recent'), updatedAt = ref(''), loadedRange = ref<string[]>([]), ranking = ref<any[]>([]), rankingEnergy = ref('ELECTRIC')
const loading = ref(false), errorText = ref(''), monthRange = ref<string[]>(defaultMonths()), trendRows = ref<any[]>([]), dimensionSections = ref<any[]>([])
const totals = computed(() => trendRows.value.reduce((sum, row) => ({ electric: sum.electric + row.electric, water: sum.water + row.water, previousElectric: sum.previousElectric + row.previousElectric, previousWater: sum.previousWater + row.previousWater }), { electric: 0, water: 0, previousElectric: 0, previousWater: 0 }))
const electricYoy = computed(() => periodComparison(trendRows.value, 'electric'))
const waterYoy = computed(() => periodComparison(trendRows.value, 'water'))
const categoryCount = computed(() => dimensionSections.value.reduce((sum, section) => sum + section.rows.length, 0))
const dateCaption = computed(() => loadedRange.value.length === 2 ? `${loadedRange.value[0]} 至 ${loadedRange.value[1]}` : '-')

function defaultMonths() { const now = new Date(), end = new Date(now.getFullYear(), now.getMonth(), 0), start = new Date(end.getFullYear(), end.getMonth() - 11, 1); return [monthKey(start), monthKey(end)] }
function monthKey(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
function parseMonth(value: string) { const [year, month] = value.split('-').map(Number); return new Date(year, month - 1, 1) }
function apiRange(range: string[]) { const start = parseMonth(range[0]), endMonth = parseMonth(range[1]), end = new Date(endMonth.getFullYear(), endMonth.getMonth() + 1, 0); return { startDate: `${range[0]}-01`, endDate: `${monthKey(end)}-${String(end.getDate()).padStart(2, '0')}` } }
function previousYearRange(range: string[]) { return range.map(value => { const date = parseMonth(value); date.setFullYear(date.getFullYear() - 1); return monthKey(date) }) }
function monthsBetween(range: string[]) { const result: string[] = [], cursor = parseMonth(range[0]), end = parseMonth(range[1]); while (cursor <= end && result.length < 60) { result.push(monthKey(cursor)); cursor.setMonth(cursor.getMonth() + 1) } return result }
function responseMap(rows: any[]) { return new Map((rows || []).map(row => [String(row.period_start || row.periodStart).slice(0, 7), row])) }
function num(value: any) { return value == null ? '暂无数据' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }
function pct(value: any, total: any) { return value == null || total <= 0 ? '—' : `${(Number(value) / Number(total) * 100).toFixed(1)}%` }
function yoyText(value: number | null) { return value === null ? '无数据或同期为零' : value === 0 ? '持平' : `${value > 0 ? '↑' : '↓'} ${Math.abs(value).toFixed(1)}%` }
function periodYoyText(value: number | null) { return value === null ? '数据不足或基数为零' : yoyText(value) }
function yoyClass(value: number | null) { return value === null ? 'yoy-flat' : value > 0 ? 'yoy-up' : value < 0 ? 'yoy-down' : 'yoy-flat' }
function observedCount(energy: 'electric' | 'water') { return trendRows.value.filter(row => row[energy] != null).length }
function displayTotal(energy: 'electric' | 'water') { return observedCount(energy) ? num(totals.value[energy]) : '暂无数据' }
function average(energy: 'electric' | 'water') { const count = observedCount(energy); return count ? num(totals.value[energy] / count) : '—' }
const coverage = computed(() => trendRows.value.filter(row => row.electric != null || row.water != null).length)
const topDevices = computed(() => ranking.value.filter(row => row.energy_type === rankingEnergy.value).sort((a,b) => Number(b.total_usage) - Number(a.total_usage)).slice(0,10))
const qualityNote = computed(() => {
  if (!trendRows.value.length) return ''
  const missing = trendRows.value.filter(row => row.electric == null || row.water == null)
  const notes = missing.length ? [`${missing.length} 个月缺少电量或水量记录，缺失数据未按零用量计算。`] : []
  if (trendRows.value.some(row => row.previousElectric == null || row.previousWater == null)) notes.push('去年同期存在缺失记录，对应同比不予计算。')
  if (trendRows.value.some(row => row.incomplete)) notes.push('含尚未结束的月份，期间同比暂不计算。')
  return notes.join(' ')
})
const insights = computed(() => {
  const lines: string[] = []
  for (const energy of ['electric','water'] as const) {
    const available = trendRows.value.filter(row => row[energy] != null)
    if (!available.length) { lines.push(`${energy==='electric'?'电':'水'}：所选期间暂无记录，请先补充历史用量。`); continue }
    const peak = available.reduce((a,b) => a[energy] >= b[energy] ? a : b)
    lines.push(`${energy==='electric'?'用电':'用水'}峰值：${peak.monthLabel}，${num(peak[energy])} ${energy==='electric'?'kWh':'m³'}，占期间总量 ${pct(peak[energy],totals.value[energy])}。`)
  }
  return lines
})
function futureMonth(date: Date) { return monthKey(date) > monthKey(new Date()) }
function choosePeriod() {
  const now = new Date(), year = now.getFullYear()
  if (preset.value === 'custom') return
  if (preset.value === 'recent') monthRange.value = defaultMonths()
  else if (preset.value === 'lastYear') monthRange.value = [`${year-1}-01`,`${year-1}-12`]
  else if (now.getMonth() === 0) { ElMessage.info('本年度尚无已结束的月份'); preset.value='recent'; monthRange.value=defaultMonths() }
  else monthRange.value = [`${year}-01`,monthKey(new Date(year,now.getMonth(),0))]
  load()
}

async function load() {
  if (loading.value || !monthRange.value || monthRange.value.length !== 2) return
  const requested = [...monthRange.value]
  const a = parseMonth(requested[0]), b = parseMonth(requested[1])
  const count = (b.getFullYear()-a.getFullYear())*12+b.getMonth()-a.getMonth()+1
  if (count < 1 || count > 36 || requested[1] > monthKey(new Date())) { ElMessage.warning('请选择不超过36个月且不含未来月份的范围'); return }
  loading.value = true; errorText.value = ''
  try {
    const currentParams = { ...apiRange(requested), period: 'MONTH' }
    const priorParams = { ...apiRange(previousYearRange(requested)), period: 'MONTH' }
    const [current, previous, dimensions]: any[] = await Promise.all([energyApi.analysis(currentParams), energyApi.analysis(priorParams), energyApi.dimensions()])
    const currentMap = responseMap(current?.trend), previousMap = responseMap(previous?.trend)
    const nextRows = monthsBetween(requested).map(month => {
      const date = parseMonth(month), priorDate = new Date(date); priorDate.setFullYear(priorDate.getFullYear() - 1)
      const currentRow = currentMap.get(month), previousRow = previousMap.get(monthKey(priorDate))
      const electric = observed(currentRow, 'electric'), water = observed(currentRow, 'water'), previousElectric = observed(previousRow, 'electric'), previousWater = observed(previousRow, 'water')
      return { month, monthLabel: `${date.getFullYear()}年${date.getMonth() + 1}月`, incomplete: month >= monthKey(new Date()), electric, water, previousElectric, previousWater, electricYoy: percentage(electric, previousElectric), waterYoy: percentage(water, previousWater) }
    })
    const electricTotal = nextRows.reduce((n,row) => n + (row.electric ?? 0),0), waterTotal = nextRows.reduce((n,row) => n + (row.water ?? 0),0)
    const enabledDimensions = (dimensions || []).filter((dimension: any) => dimension.status === undefined || Number(dimension.status) === 1)
    // At most three dimension requests in flight; one failure leaves other panels usable.
    const sections: any[] = []
    for (let i=0;i<enabledDimensions.length;i+=3) {
      const batch = enabledDimensions.slice(i,i+3)
      const results = await Promise.allSettled(batch.map((dimension: any) => energyApi.analysis({ ...currentParams, dimensionId: dimension.id })))
      results.forEach((result, index) => {
        const dimension=batch[index]
        sections.push({ id:dimension.id, name:dimension.dimension_name, electricTotal, waterTotal,
          rows:result.status==='fulfilled'?dimensionRows((result.value as any)?.dimensionTotals || [],electricTotal,waterTotal):[],
          error:result.status==='rejected'?'该维度加载失败':'' })
      })
    }
    trendRows.value = nextRows; dimensionSections.value = sections; ranking.value = current.ranking || []
    loadedRange.value=requested; updatedAt.value=formatDateTime(new Date().toISOString())
  } catch (error: any) {
    errorText.value = `加载失败，当前保留上次成功结果。 ${error?.response?.data?.message || error?.message || ''}`
    ElMessage.error(errorText.value)
  } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.data-note{font-size:12px;line-height:1.8;color:#9fb3ce;margin-bottom:14px;padding:10px 14px;background:#10233d;border-radius:8px}.warning{color:#fcd34d}.insight-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.insight-grid p{color:#c7d7ed;font-size:13px;line-height:1.8}.rank-row{display:grid;grid-template-columns:25px minmax(0,1fr) 160px 65px;gap:12px;align-items:center;padding:12px 0;border-top:1px solid #ffffff12;font-size:12px}.rank-row small{display:block;color:#8da1bd;margin-top:5px}.rank-row b{overflow-wrap:anywhere}.rank-row strong,.rank-row>span:last-child{text-align:right}.screen-controls{flex-wrap:wrap}@media(max-width:768px){.insight-grid{grid-template-columns:1fr}.rank-row{grid-template-columns:20px minmax(0,1fr) 105px;gap:6px}.rank-row>span:last-child{grid-column:3}.screen-controls :deep(.el-select){width:100%!important}}

.usage-dashboard{min-height:calc(100vh - 60px);padding:20px;color:#dbeafe;background:radial-gradient(circle at 12% 0,rgba(14,116,144,.2),transparent 30%),radial-gradient(circle at 92% 8%,rgba(37,99,235,.18),transparent 32%),#071225;border-radius:10px}.screen-head,.panel-head,.kpi-foot,.dimension-title{display:flex;align-items:center;justify-content:space-between}.screen-head{gap:18px;margin-bottom:18px}.screen-title{font-size:24px;font-weight:750;letter-spacing:1px}.screen-subtitle{color:#7f94b2;font-size:13px;margin:6px 0 0 16px}.pulse{display:inline-block;width:9px;height:9px;margin-right:8px;border-radius:50%;background:#22d3ee;box-shadow:0 0 0 5px rgba(34,211,238,.12),0 0 14px #22d3ee}.screen-controls{display:flex;gap:10px}.screen-controls :deep(.el-date-editor){width:300px}.screen-controls :deep(.el-range-input),.screen-controls :deep(.el-range-separator){color:#dbeafe}.screen-controls :deep(.el-date-editor.el-input__wrapper){background:#0b1c35;box-shadow:0 0 0 1px #1e3a5f inset}.screen-alert{margin-bottom:14px}.kpi-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:14px}.kpi-card,.panel{border:1px solid rgba(71,108,151,.35);background:linear-gradient(145deg,rgba(15,35,63,.92),rgba(9,24,45,.94));box-shadow:0 12px 30px rgba(0,0,0,.12);border-radius:10px}.kpi-card{padding:18px;position:relative;overflow:hidden}.kpi-card:after{content:"";position:absolute;width:80px;height:80px;right:-25px;top:-25px;border-radius:50%;background:var(--glow);filter:blur(8px)}.kpi-card.electric{--glow:rgba(245,158,11,.17);border-top-color:#f59e0b}.kpi-card.water{--glow:rgba(56,189,248,.18);border-top-color:#38bdf8}.kpi-card.neutral{--glow:rgba(99,102,241,.14);border-top-color:#6366f1}.kpi-label{color:#91a4bf;font-size:13px}.kpi-value{font:700 28px/1.4 ui-monospace,SFMono-Regular,Menlo,monospace;margin:7px 0}.kpi-value small{font:12px/1.2 sans-serif;color:#7890ad;margin-left:7px}.kpi-foot{font-size:12px;color:#7086a4;gap:8px}.chart-grid,.dimension-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.panel{padding:16px;min-width:0}.panel-head{margin-bottom:8px}.panel-head b,.dimension-title b{font-size:16px}.panel-head div span,.dimension-title div span{display:block;color:#7086a4;font-size:12px;margin-top:5px}.unit{color:#64748b;font-size:12px}.month-share-panel{margin-top:14px}.month-table-wrap{overflow:auto;max-height:460px}.month-table{width:100%;min-width:920px;border-collapse:collapse;font-size:12px}.month-table th{position:sticky;top:0;background:#10233d;color:#8fa4c0;font-weight:600;text-align:left;padding:11px 10px;z-index:1}.month-table td{border-top:1px solid rgba(71,108,151,.22);padding:10px;color:#c7d7ed}.share-cell{position:relative;width:110px;height:18px;border-radius:4px;background:rgba(51,65,85,.55);overflow:hidden}.share-cell i{position:absolute;inset:0 auto 0 0;opacity:.48}.share-cell span{position:relative;display:block;text-align:center;line-height:18px;color:#e2e8f0}.electric-fill{display:block;background:linear-gradient(90deg,#f59e0b,#fbbf24)}.water-fill{display:block;background:linear-gradient(90deg,#0284c7,#38bdf8)}.yoy-up{color:#fb7185!important}.yoy-down{color:#34d399!important}.yoy-flat{color:#94a3b8!important}.dimension-title{margin:22px 2px 12px}.dimension-panel{padding-bottom:8px}.dimension-row{padding:12px 0;border-top:1px solid rgba(71,108,151,.22)}.dimension-name{font-size:13px;font-weight:600;margin-bottom:9px}.dimension-name>i{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:7px;box-shadow:0 0 8px currentColor}.dimension-measure{display:grid;grid-template-columns:145px 1fr 52px;align-items:center;gap:9px;margin:7px 0;font-size:11px;color:#8da1bd}.dimension-measure b{text-align:right;color:#bdcde1}.measure-track{height:6px;background:rgba(51,65,85,.72);border-radius:4px;overflow:hidden}.measure-track i{height:100%;border-radius:4px}.empty{display:flex;align-items:center;justify-content:center;min-height:130px;color:#718096}.empty.compact{min-height:90px}@media(max-width:1100px){.kpi-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:768px){.usage-dashboard{padding:12px;border-radius:0;margin:-10px;min-height:calc(100vh - 60px)}.screen-head{align-items:flex-start;flex-direction:column}.screen-title{font-size:21px}.screen-controls{width:100%;flex-direction:column}.screen-controls :deep(.el-date-editor),.screen-controls .el-button{width:100%}.kpi-grid,.chart-grid,.dimension-grid{grid-template-columns:1fr}.kpi-card{padding:15px}.kpi-value{font-size:24px}.panel{padding:12px}.dimension-measure{grid-template-columns:120px 1fr 46px}.measure-label{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
</style>
