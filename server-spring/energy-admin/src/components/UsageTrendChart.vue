<template>
  <div class="trend-chart">
    <div v-if="!hasData" class="chart-empty">所选月份暂无数据</div>
    <svg v-else viewBox="0 0 760 280" preserveAspectRatio="none" role="img" :aria-label="title">
      <g class="grid">
        <line v-for="tick in ticks" :key="tick.y" x1="62" :y1="tick.y" x2="742" :y2="tick.y" />
        <text v-for="tick in ticks" :key="`label-${tick.y}`" x="54" :y="tick.y + 4" text-anchor="end">{{ compact(tick.value) }}</text>
      </g>
      <polyline v-if="previousPoints" class="previous-line" :points="previousPoints" fill="none" />
      <polyline v-if="currentPoints" class="current-line" :style="{ stroke: color }" :points="currentPoints" fill="none" />
      <g v-for="(point, index) in pointRows" :key="point.month">
        <circle v-if="point.current > 0" :cx="point.x" :cy="point.currentY" r="4" :style="{ fill: color }">
          <title>{{ point.monthLabel }}：{{ number(point.current) }} {{ unit }}</title>
        </circle>
        <text v-if="showLabel(index)" :x="point.x" y="270" text-anchor="middle">{{ point.shortLabel }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ rows: any[]; valueKey: string; previousKey: string; color: string; unit: string; title: string }>()
const left = 62, right = 742, top = 18, bottom = 238
const values = computed(() => props.rows.flatMap(row => [Number(row[props.valueKey]) || 0, Number(row[props.previousKey]) || 0]))
const maximum = computed(() => Math.max(0, ...values.value))
const hasData = computed(() => maximum.value > 0)
const y = (value: number) => bottom - (maximum.value ? value / maximum.value * (bottom - top) : 0)
const x = (index: number) => props.rows.length <= 1 ? (left + right) / 2 : left + index / (props.rows.length - 1) * (right - left)
const pointRows = computed(() => props.rows.map((row, index) => ({
  month: row.month,
  monthLabel: row.monthLabel,
  shortLabel: String(row.month).slice(5).replace('-', '/'),
  current: Number(row[props.valueKey]) || 0,
  x: x(index),
  currentY: y(Number(row[props.valueKey]) || 0)
})))
const currentPoints = computed(() => pointRows.value.map(point => `${point.x},${point.currentY}`).join(' '))
const previousPoints = computed(() => props.rows.map((row, index) => `${x(index)},${y(Number(row[props.previousKey]) || 0)}`).join(' '))
const ticks = computed(() => Array.from({ length: 5 }, (_, index) => ({ value: maximum.value * (4 - index) / 4, y: top + index * (bottom - top) / 4 })))
function showLabel(index: number) { const step = Math.max(1, Math.ceil(props.rows.length / 8)); return index % step === 0 || index === props.rows.length - 1 }
function number(value: number) { return value.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }
function compact(value: number) { if (value >= 1000000) return `${(value / 1000000).toFixed(1)}m`; if (value >= 1000) return `${(value / 1000).toFixed(1)}k`; return value.toFixed(value < 10 && value > 0 ? 1 : 0) }
</script>

<style scoped>
.trend-chart{height:280px;min-width:0}.trend-chart svg{width:100%;height:100%;overflow:visible}.grid line{stroke:rgba(148,163,184,.18);stroke-width:1}.grid text,.trend-chart text{fill:#8291aa;font-size:11px}.current-line{stroke-width:3;stroke-linecap:round;stroke-linejoin:round;filter:drop-shadow(0 0 4px rgba(56,189,248,.25))}.previous-line{stroke:#64748b;stroke-width:2;stroke-dasharray:7 6;stroke-linecap:round}.chart-empty{height:100%;display:flex;align-items:center;justify-content:center;color:#718096;font-size:13px}@media(max-width:768px){.trend-chart{height:230px}.trend-chart svg{height:230px}}
</style>
