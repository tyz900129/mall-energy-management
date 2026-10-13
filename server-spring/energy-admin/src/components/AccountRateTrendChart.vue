<template>
  <div class="rate-chart">
    <div v-if="!validRows.length" class="empty">所选时间范围暂无可计算的变化率记录</div>
    <svg v-else viewBox="0 0 820 320" preserveAspectRatio="none" role="img" aria-label="账户每日变化率走势">
      <g class="grid">
        <line v-for="tick in ticks" :key="tick.value" x1="64" :y1="tick.y" x2="774" :y2="tick.y" :class="{ zero: tick.value === 0 }" />
        <text v-for="tick in ticks" :key="`label-${tick.value}`" x="56" :y="tick.y + 4" text-anchor="end">{{ signed(tick.value) }}</text>
      </g>
      <line v-if="riseThreshold != null" x1="64" :y1="y(riseThreshold)" x2="774" :y2="y(riseThreshold)" class="threshold rise-threshold"><title>上涨报警阈值 {{ riseThreshold }}%</title></line>
      <line v-if="fallThreshold != null" x1="64" :y1="y(-fallThreshold)" x2="774" :y2="y(-fallThreshold)" class="threshold fall-threshold"><title>下降报警阈值 -{{ fallThreshold }}%</title></line>
      <path :d="path" class="rate-line" fill="none" />
      <g v-for="(point,index) in plotted" :key="point.billDate">
        <circle :cx="point.x" :cy="point.y" r="4" :class="point.value >= 0 ? 'rise-point' : 'fall-point'">
          <title>{{ point.billDate }}：{{ signed(point.value) }}；当日 {{ money(point.actualAmount) }}；基准 {{ money(point.baselineAverage) }}</title>
        </circle>
        <text v-if="showDate(index)" :x="point.x" y="307" text-anchor="middle">{{ String(point.billDate).slice(5) }}</text>
      </g>
      <g class="legend">
        <text x="70" y="16">红虚线：上涨阈值</text><text x="190" y="16">蓝虚线：下降阈值</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props=defineProps<{rows:any[];riseThreshold?:number|null;fallThreshold?:number|null}>()
const left=64,right=774,top=28,bottom=274
const validRows=computed(()=>props.rows.filter(r=>r.differencePct!==null&&r.differencePct!==undefined))
const bound=computed(()=>Math.max(10,...validRows.value.map(r=>Math.abs(Number(r.differencePct)||0)),Number(props.riseThreshold)||0,Number(props.fallThreshold)||0)*1.12)
const y=(value:number)=>top+(bound.value-value)/(bound.value*2)*(bottom-top)
const x=(index:number)=>validRows.value.length<=1?(left+right)/2:left+index/(validRows.value.length-1)*(right-left)
const plotted=computed(()=>validRows.value.map((row,index)=>({...row,value:Number(row.differencePct),x:x(index),y:y(Number(row.differencePct))})))
const path=computed(()=>plotted.value.map((p,index)=>`${index?'L':'M'} ${p.x} ${p.y}`).join(' '))
const ticks=computed(()=>[bound.value,bound.value/2,0,-bound.value/2,-bound.value].map(value=>({value,y:y(value)})))
const signed=(value:number)=>`${value>0?'+':''}${Number(value).toFixed(1)}%`
const money=(value:any)=>value==null?'-':`¥${Number(value).toFixed(2)}`
function showDate(index:number){const step=Math.max(1,Math.ceil(validRows.value.length/9));return index%step===0||index===validRows.value.length-1}
</script>

<style scoped>
.rate-chart{height:320px;min-width:0}.rate-chart svg{width:100%;height:100%;overflow:visible}.grid line{stroke:#e5e7eb;stroke-width:1}.grid line.zero{stroke:#94a3b8;stroke-width:1.4}.grid text,.rate-chart text{fill:#7c8799;font-size:11px}.rate-line{stroke:#7c3aed;stroke-width:2.6;stroke-linecap:round;stroke-linejoin:round}.rise-point{fill:#f56c6c}.fall-point{fill:#409eff}.threshold{stroke-width:1.5;stroke-dasharray:7 5}.rise-threshold{stroke:#f56c6c}.fall-threshold{stroke:#409eff}.legend text{font-size:11px;font-weight:600}.empty{height:100%;display:flex;align-items:center;justify-content:center;color:#909399}@media(max-width:768px){.rate-chart{height:270px}.rate-chart svg{height:270px}}
</style>
