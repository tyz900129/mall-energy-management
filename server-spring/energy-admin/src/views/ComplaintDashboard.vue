<template>
  <div class="dash">
    <el-card shadow="never">
      <div class="filters">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px" />
        <el-select v-model="filters.businessType" clearable placeholder="业态" style="width:130px">
          <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.floor" clearable placeholder="楼层" style="width:110px">
          <el-option v-for="item in floors" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.category" clearable placeholder="类别" style="width:140px">
          <el-option v-for="item in dictList('CATEGORY')" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
        </el-select>
        <el-select v-model="filters.channel" clearable placeholder="渠道" style="width:130px">
          <el-option v-for="item in dictList('CHANNEL')" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" style="width:110px">
          <el-option v-for="item in dictList('STATUS')" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
        </el-select>
        <el-select v-model="filters.satisfaction" clearable placeholder="满意度" style="width:110px">
          <el-option v-for="item in dictList('SATISFACTION')" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
        </el-select>
        <el-button type="primary" @click="loadAll">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button v-if="canExport" plain @click="exportReport">导出报表</el-button>
      </div>

      <el-row :gutter="12" class="kpis">
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">{{ overview.total ?? 0 }}</div><div class="kpi-label">客诉总数</div></div></el-col>
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">{{ overview.brandCount ?? 0 }}</div><div class="kpi-label">涉及品牌</div></div></el-col>
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">¥{{ money(overview.totalCost) }}</div><div class="kpi-label">费用合计</div></div></el-col>
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">{{ overview.onSiteRate ?? 0 }}%</div><div class="kpi-label">现场解决率</div></div></el-col>
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">{{ overview.satisfactionRate ?? 0 }}%</div><div class="kpi-label">满意率</div></div></el-col>
        <el-col :xs="12" :sm="8" :md="4"><div class="kpi"><div class="kpi-value">{{ overview.avgHandleDays ?? 0 }} 天</div><div class="kpi-label">平均处理时长</div></div></el-col>
      </el-row>
      <div class="kpi-sub">
        未结案 {{ overview.openingCount ?? 0 }} 条 · 已关闭 {{ overview.closedCount ?? 0 }} 条 ·
        公司承担 ¥{{ money(overview.companyCost) }} · 商户承担 ¥{{ money(overview.merchantCost) }}
      </div>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>维度分布</span>
          <el-radio-group v-model="dimension" size="small" @change="loadDistribution">
            <el-radio-button v-for="item in dimensions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-if="distribution.length" class="bars">
        <div v-for="item in distribution" :key="item.key" class="bar-row">
          <span class="bar-label">{{ item.key || '-' }}</span>
          <div class="bar-track"><div class="bar-fill" :style="{ width: barWidth(item.value, distribution) }" /></div>
          <span class="bar-value">{{ item.value }} 条</span>
          <span class="bar-amount">¥{{ money(item.amount) }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无数据" :image-size="60" />
    </el-card>

    <el-row :gutter="12" class="card">
      <el-col :xs="24" :md="14">
        <el-card shadow="never">
          <template #header><span>月度趋势</span></template>
          <div v-if="trend.length" class="trend">
            <svg viewBox="0 0 640 180" preserveAspectRatio="none" class="trend-svg">
              <polyline :points="trendPoints" fill="none" stroke="#1f6feb" stroke-width="2" />
              <circle v-for="(p, i) in trendDots" :key="i" :cx="p.x" :cy="p.y" r="3" fill="#1f6feb" />
            </svg>
            <div class="trend-axis">
              <span>{{ trend[0]?.key }}</span>
              <span>{{ trend[trend.length - 1]?.key }}</span>
            </div>
            <div class="muted">共 {{ trend.reduce((s: number, i: any) => s + Number(i.value || 0), 0) }} 条，合计 ¥{{ money(trend.reduce((s: number, i: any) => s + Number(i.amount || 0), 0)) }}</div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="50" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="never">
          <template #header><span>处理时效</span></template>
          <div class="eff-summary">
            平均 {{ efficiency.avgHandleDays ?? 0 }} 天 · 中位 {{ efficiency.medianHandleDays ?? 0 }} 天 ·
            现场解决 {{ efficiency.onSiteRate ?? 0 }}%
          </div>
          <div class="bars">
            <div v-for="item in efficiency.buckets || []" :key="item.bucket" class="bar-row">
              <span class="bar-label">{{ item.bucket }}</span>
              <div class="bar-track"><div class="bar-fill alt" :style="{ width: barWidth(item.value, efficiency.buckets || []) }" /></div>
              <span class="bar-value">{{ item.value }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="card">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>排名分析</span>
              <div class="mini-selects">
                <el-select v-model="rankingDimension" size="small" style="width:110px" @change="loadRanking">
                  <el-option label="品牌" value="brand" />
                  <el-option label="责任商管" value="manager" />
                  <el-option label="经办人" value="handler" />
                </el-select>
                <el-select v-model="rankingMetric" size="small" style="width:100px" @change="loadRanking">
                  <el-option label="按数量" value="count" />
                  <el-option label="按费用" value="cost" />
                </el-select>
              </div>
            </div>
          </template>
          <div v-if="ranking.length" class="bars">
            <div v-for="item in ranking" :key="item.key" class="bar-row">
              <span class="bar-label">{{ item.key }}</span>
              <div class="bar-track"><div class="bar-fill rank" :style="{ width: barWidth(item.value, ranking) }" /></div>
              <span class="bar-value">{{ rankingMetric === 'cost' ? '¥' + money(item.value) : item.value + ' 条' }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="50" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>费用分析</span></template>
          <div class="eff-summary">
            合计 ¥{{ money(cost.totalCost) }} · 公司 ¥{{ money(cost.companyCost) }} · 商户 ¥{{ money(cost.merchantCost) }} ·
            单均 ¥{{ money(cost.avgCost) }}
          </div>
          <div class="bars">
            <div v-for="item in cost.buckets || []" :key="item.bucket" class="bar-row">
              <span class="bar-label">{{ item.bucket }}</span>
              <div class="bar-track"><div class="bar-fill cost" :style="{ width: barWidth(item.value, cost.buckets || []) }" /></div>
              <span class="bar-value">{{ item.value }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>交叉分析</span>
          <div class="mini-selects">
            <el-select v-model="dim1" size="small" style="width:120px">
              <el-option v-for="item in crossDimensions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="muted">×</span>
            <el-select v-model="dim2" size="small" style="width:120px">
              <el-option v-for="item in crossDimensions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="metric" size="small" style="width:100px">
              <el-option label="按数量" value="count" />
              <el-option label="按费用" value="cost" />
            </el-select>
            <el-button size="small" type="primary" @click="loadCross">生成</el-button>
          </div>
        </div>
      </template>
      <el-table v-if="crossRows.length" :data="crossRows" border size="small" max-height="360">
        <el-table-column prop="dim1" :label="dimensionLabel(dim1)" min-width="140" />
        <el-table-column prop="dim2" :label="dimensionLabel(dim2)" min-width="140" />
        <el-table-column :label="metric === 'cost' ? '费用合计(元)' : '数量'" width="140" align="right">
          <template #default="{ row }">{{ metric === 'cost' ? money(row.value) : row.value }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="选择两个维度后点击生成" :image-size="50" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { complaintApi, saveBlob } from '@/api/complaint'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const canExport = computed(() => userStore.hasPermission('complaint.export') || userStore.hasPermission('complaint.stats.view'))

const dateRange = ref<string[]>([])
const filters = reactive({ businessType: '', floor: '', category: '', channel: '', status: '', satisfaction: '' })
const businessTypes = ref<any[]>([])
const floors = ref<any[]>([])
const dictionaries = ref<any[]>([])

const overview = ref<any>({})
const distribution = ref<any[]>([])
const trend = ref<any[]>([])
const efficiency = ref<any>({})
const cost = ref<any>({})
const ranking = ref<any[]>([])
const crossRows = ref<any[]>([])

const dimension = ref('category')
const dim1 = ref('businessType')
const dim2 = ref('category')
const metric = ref('count')
const rankingDimension = ref('brand')
const rankingMetric = ref('count')

const dimensions = [
  { label: '类别', value: 'category' }, { label: '业态', value: 'businessType' }, { label: '楼层', value: 'floor' },
  { label: '状态', value: 'status' }, { label: '渠道', value: 'channel' }, { label: '满意度', value: 'satisfaction' },
  { label: '品牌', value: 'brand' }, { label: '责任商管', value: 'manager' }, { label: '经办人', value: 'handler' }
]
const crossDimensions = dimensions.slice(0, 7)

onMounted(async () => {
  const data: any = await complaintApi.statsFilters()
  dictionaries.value = data.dictionaries || []
  businessTypes.value = data.businessTypes || []
  floors.value = data.floors || []
  await loadAll()
})

function params() {
  return {
    ...filters,
    start: dateRange.value?.[0] || null,
    end: dateRange.value?.[1] || null
  }
}

async function loadAll() {
  await Promise.all([loadOverview(), loadDistribution(), loadEfficiency(), loadCost(), loadRanking(), loadTrend()])
}

async function loadOverview() { overview.value = await complaintApi.statsOverview(params()) }
async function loadDistribution() {
  distribution.value = await complaintApi.statsDistribution({ ...params(), dimension: dimension.value }) as any
}
async function loadTrend() { trend.value = await complaintApi.statsTrend({ ...params(), granularity: 'month' }) as any }
async function loadEfficiency() { efficiency.value = await complaintApi.statsEfficiency(params()) }
async function loadCost() { cost.value = await complaintApi.statsCost(params()) }
async function loadRanking() {
  ranking.value = await complaintApi.statsRanking({ ...params(), rankingDimension: rankingDimension.value, metric: rankingMetric.value, limit: 10 }) as any
}
async function loadCross() {
  if (dim1.value === dim2.value) return ElMessage.warning('请选择两个不同的维度')
  crossRows.value = await complaintApi.statsCross({ ...params(), dim1: dim1.value, dim2: dim2.value, metric: metric.value }) as any
}

function reset() {
  dateRange.value = []
  Object.assign(filters, { businessType: '', floor: '', category: '', channel: '', status: '', satisfaction: '' })
  loadAll()
}

async function exportReport() {
  const blob: any = await complaintApi.statsExport(params())
  saveBlob(blob, '客诉数据报表.xlsx')
}

function dictList(type: string) {
  return dictionaries.value.filter((d: any) => d.dictType === type)
}

function dimensionLabel(value: string) {
  return dimensions.find(d => d.value === value)?.label || value
}

function barWidth(value: any, list: any[]) {
  const max = Math.max(...list.map((i: any) => Number(i.value || 0)), 1)
  return `${Math.max(3, Number(value || 0) / max * 100)}%`
}

function money(value: any) {
  return Number(value || 0).toFixed(2)
}

const trendMax = computed(() => Math.max(...trend.value.map((i: any) => Number(i.value || 0)), 1))
const trendDots = computed(() => trend.value.map((item: any, index: number) => ({
  x: trend.value.length === 1 ? 320 : index / (trend.value.length - 1) * 620 + 10,
  y: 160 - Number(item.value || 0) / trendMax.value * 140
})))
const trendPoints = computed(() => trendDots.value.map(p => `${p.x},${p.y}`).join(' '))
</script>

<style scoped>
.dash { padding: 4px; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; }
.kpis { margin-top: 14px; }
.kpi { background: #f7f9fc; border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; text-align: center; }
.kpi-value { font-size: 22px; font-weight: 700; color: #1f6feb; }
.kpi-label { font-size: 12px; color: #909399; margin-top: 4px; }
.kpi-sub { margin-top: 10px; color: #606266; font-size: 12px; }
.card { margin-top: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.mini-selects { display: flex; align-items: center; gap: 6px; }
.bars { display: flex; flex-direction: column; gap: 8px; }
.bar-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.bar-label { width: 96px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { flex: 1; height: 16px; background: #f0f2f5; border-radius: 8px; overflow: hidden; }
.bar-fill { height: 100%; background: #409eff; border-radius: 8px; }
.bar-fill.alt { background: #67c23a; }
.bar-fill.rank { background: #e6a23c; }
.bar-fill.cost { background: #909399; }
.bar-value { width: 78px; text-align: right; }
.bar-amount { width: 96px; text-align: right; color: #909399; }
.trend { padding-top: 6px; }
.trend-svg { width: 100%; height: 160px; }
.trend-axis { display: flex; justify-content: space-between; color: #909399; font-size: 12px; }
.muted { color: #909399; margin-top: 6px; font-size: 12px; }
.eff-summary { color: #606266; font-size: 13px; margin-bottom: 12px; }

@media (max-width: 768px) {
  .dash { padding: 0; }
  .filters { gap: 6px; }
  .filters .el-select,
  .filters .el-date-editor { width: calc(50% - 4px) !important; }
  .filters .el-button { margin-left: 0; flex: 1 1 auto; }
  .kpi { padding: 10px 6px; }
  .kpi-value { font-size: 18px; }
  .kpi-label { font-size: 11px; white-space: nowrap; }
  .kpi-sub { line-height: 1.9; }
  .card-head :deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: 4px; }
  .mini-selects { flex-wrap: wrap; }
  .bar-label { width: 70px; font-size: 12px; }
  .bar-value { width: 58px; font-size: 12px; }
  .bar-amount { display: none; }
  .eff-summary { font-size: 12px; }
  .dash :deep(.el-card__body) { padding: 10px; }
  .dash :deep(.el-card__header) { padding: 10px; }
}
</style>
