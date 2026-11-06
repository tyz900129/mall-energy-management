<template>
  <div class="page">
    <h2>巡检记录管理</h2>
    <div class="toolbar">
      <el-select v-model="scene" placeholder="全部场景" clearable size="small" style="width:190px">
        <el-option label="仓库商管巡检" value="WAREHOUSE_COMMERCIAL" />
        <el-option label="仓库物业巡检" value="WAREHOUSE_PROPERTY" />
        <el-option label="联合巡检"      value="JOINT" />
      </el-select>
      <el-input v-model="keyword" placeholder="铺位编码 / 名称 / 提交人" clearable size="small" style="width:240px" @keyup.enter="search" />
      <el-date-picker v-model="range" type="daterange" size="small" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" border size="small" style="width:100%" v-loading="loading">
      <el-table-column prop="submittedAt" label="提交时间" width="170" />
      <el-table-column prop="sceneLabel" label="场景" width="120" />
      <el-table-column prop="stallCode" label="铺位" width="130">
        <template #default="{ row }">
          <div>{{ row.stallCode || row.stallName }}</div>
          <div class="sub">{{ row.stallName }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="templateName" label="巡检表单" min-width="150" />
      <el-table-column prop="submitterName" label="提交人" width="110">
        <template #default="{ row }">
          <div>{{ row.submitterName }}</div>
          <div class="sub">{{ row.submitterDept }}</div>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'ABNORMAL'" type="danger" size="small">异常</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="issueCount" label="整改任务" width="90" align="center" />
      <el-table-column label="来源" width="130">
        <template #default="{ row }">
          <span v-if="row.taskId">联合巡检 #{{ row.taskId }}</span>
          <span v-else-if="row.planId">周期巡检 #{{ row.planId }}</span>
          <span v-else class="sub">扫码/手动</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="size"
      :page-sizes="[20, 50, 100]"
      @current-change="onPage"
      @size-change="onSize" />

    <el-drawer v-model="detailVisible" title="巡检记录详情" size="640px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="铺位">{{ detail.record.stallCode }} {{ detail.record.stallName }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ detail.sceneLabel }}</el-descriptions-item>
          <el-descriptions-item label="巡检表单">{{ detail.templateName }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="detail.record.status === 'ABNORMAL' ? 'danger' : 'success'" size="small">
              {{ detail.record.status === 'ABNORMAL' ? '异常' : '正常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交人">{{ detail.record.submitterName }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.record.submittedAt }}</el-descriptions-item>
          <el-descriptions-item label="提交部门">{{ detail.record.submitterDept }}</el-descriptions-item>
          <el-descriptions-item label="记录编号">#{{ detail.record.id }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">检查项</h4>
        <el-table :data="detail.fields || []" border size="small">
          <el-table-column prop="category" label="分类" width="110" />
          <el-table-column prop="label" label="检查项" min-width="160" />
          <el-table-column label="填报值" min-width="120">
            <template #default="{ row }">{{ answerText(row, detail.answers) }}</template>
          </el-table-column>
          <el-table-column label="判定" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="isAbnormal(row, detail.answers)" type="danger" size="small">异常</el-tag>
              <span v-else class="sub">—</span>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="block-title">整改任务</h4>
        <el-table v-if="detail.issues?.length" :data="detail.issues" border size="small">
          <el-table-column prop="deptName" label="责任部门" width="120" />
          <el-table-column prop="summaryText" label="问题摘要" min-width="180" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="deadlineAt" label="截止时间" width="160" />
        </el-table>
        <div v-else class="sub">本次巡检无整改任务</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '@/api/http'

const scene = ref('')
const keyword = ref('')
const range = ref<any>([])
const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<any>(null)

onMounted(search)

function params() {
  const p: any = { page: page.value, size: size.value }
  if (scene.value) p.scene = scene.value
  if (keyword.value.trim()) p.keyword = keyword.value.trim()
  if (range.value?.length === 2) { p.start = range.value[0]; p.end = range.value[1] }
  return p
}

async function search() {
  page.value = 1
  await load()
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/api/v1/qr-inspection/records', { params: params() })
    rows.value = res.list || []
    total.value = res.total || 0
  } finally { loading.value = false }
}

function reset() {
  scene.value = ''; keyword.value = ''; range.value = []; search()
}

function onPage(p: number) { page.value = p; load() }
function onSize(s: number) { size.value = s; page.value = 1; load() }

async function openDetail(id: number) {
  detail.value = await http.get('/api/v1/qr-inspection/records/' + id)
  detailVisible.value = true
}

function answerText(f: any, answers: any) {
  const v = answers?.[f.code]
  if (v === undefined || v === null || v === '') return '—'
  if (Array.isArray(v)) return v.join('、')
  if (typeof v === 'boolean') return v ? '是' : '否'
  const map: Record<string, string> = { NORMAL: '正常', ABNORMAL: '异常' }
  return map[String(v)] || String(v)
}

function isAbnormal(f: any, answers: any) {
  const v = answers?.[f.code]
  if (v === undefined || v === null) return false
  if (f.controlType === 'NORMAL_ABNORMAL') return v === 'ABNORMAL'
  if (f.controlType === 'BOOLEAN' && f.normalValue != null) return String(v) !== String(f.normalValue)
  return false
}
</script>

<style scoped>
.page { padding: 16px; }
.toolbar { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
.pager { margin-top:12px; display:flex; justify-content:flex-end; }
.sub { color:#9ca3af; font-size:12px; }
.block-title { margin:16px 0 8px; font-weight:600; }
</style>
