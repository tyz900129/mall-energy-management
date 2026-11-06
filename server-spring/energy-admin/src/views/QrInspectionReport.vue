<template>
  <div class="page">
    <h2>巡检汇总报告</h2>
    <div class="toolbar">
      <el-select v-model="scene" placeholder="场景" clearable size="small" style="width:200px">
        <el-option label="仓库商管巡检" value="WAREHOUSE_COMMERCIAL" />
        <el-option label="物业仓库巡检"  value="WAREHOUSE_PROPERTY" />
        <el-option label="联合巡检"      value="JOINT" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" size="small" start-placeholder="开始" end-placeholder="结束" />
      <el-button type="primary" size="small" @click="load">查询</el-button>
    </div>
    <div v-if="data" class="report">
      <div class="cards">
        <div class="card">
          <div class="num">{{ data.totalRecords }}</div>
          <div class="label">巡检记录</div>
        </div>
        <div class="card warn">
          <div class="num">{{ data.abnormalRecords }}</div>
          <div class="label">异常记录</div>
        </div>
        <div class="card">
          <div class="num">{{ data.totalIssues }}</div>
          <div class="label">整改任务</div>
        </div>
        <div class="card good">
          <div class="num">{{ (data.remedyRate*100).toFixed(1) }}%</div>
          <div class="label">整改完成率</div>
        </div>
      </div>
      <div class="section">
        <h4>按部门</h4>
        <el-table :data="data.byDepartment || []" border size="small">
          <el-table-column prop="deptName" label="部门" />
          <el-table-column prop="total" label="整改任务" width="100" align="center" />
          <el-table-column prop="closed" label="已整改" width="100" align="center" />
          <el-table-column prop="pending" label="待整改" width="100" align="center" />
        </el-table>
      </div>
      <div class="section">
        <h4>按问题分类</h4>
        <el-table :data="catRows" border size="small">
          <el-table-column prop="category" label="分类" />
          <el-table-column prop="count" label="异常次数" width="120" align="center" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '@/api/http'

const scene = ref('')
const range = ref<any>([])
const data = ref<any>(null)
const catRows = computed(() => Object.entries(data.value?.byCategory || {}).map(([category, count]) => ({ category, count })))

onMounted(load)

async function load() {
  const params: any = {}
  if (scene.value) params.scene = scene.value
  if (range.value?.length === 2) { params.start = range.value[0]; params.end = range.value[1] }
  data.value = await http.get('/api/v1/qr-inspection/report', { params })
}
</script>

<style scoped>
.page { padding: 16px; }
.toolbar { display:flex; gap:8px; margin-bottom:12px; }
.cards { display:flex; gap:12px; margin-bottom:16px; flex-wrap:wrap; }
.card { flex:1; min-width:120px; background:#fff; border-radius:10px; padding:16px; text-align:center; box-shadow:0 1px 3px rgba(0,0,0,.08); }
.card .num { font-size:28px; font-weight:600; color:#111827; }
.card .label { color:#6b7280; font-size:13px; margin-top:4px; }
.card.warn .num { color:#dc2626; }
.card.good .num { color:#16a34a; }
.section { margin-bottom:16px; }
.section h4 { margin-bottom:8px; }
</style>
