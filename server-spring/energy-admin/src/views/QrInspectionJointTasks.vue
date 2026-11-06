<template>
  <div class="page">
    <h2>联合巡检</h2>
    <div class="desc">发起后推送卡片到参与部门群；现场随机巡检，走到任意商户/仓库后扫码或搜索铺位填报，不预选铺位。</div>
    <div class="toolbar">
      <el-button type="primary" size="small" :disabled="!!activeTask" @click="openCreate">+ 发起联合巡检</el-button>
    </div>
    <el-alert v-if="activeTask" :title="'进行中的任务：' + activeTask.name + '（截止 ' + fmt(activeTask.endAt) + '）'" type="warning" show-icon style="margin-bottom:12px" />
    <el-table :data="tasks" border size="small">
      <el-table-column prop="taskNo" label="编号" width="140" />
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status==='ACTIVE' ? 'warning' : 'info'" size="small">{{ row.status==='ACTIVE' ? '进行中' : '已截止' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参与部门" min-width="180">
        <template #default="{ row }">{{ deptNames(row.participatingDeptCodes) }}</template>
      </el-table-column>
      <el-table-column label="开始" width="160">
        <template #default="{ row }">{{ fmt(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="截止" width="160">
        <template #default="{ row }">{{ fmt(row.endAt) }}</template>
      </el-table-column>
      <el-table-column prop="createdByName" label="发起人" width="100" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="showDetail(row)">详情</el-button>
          <el-button v-if="row.status==='ACTIVE'" link type="primary" size="small" @click="openClose(row)">手动截止</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="发起联合巡检" width="560px">
      <el-form :model="create" label-width="100px">
        <el-form-item label="任务名称"><el-input v-model="create.name" placeholder="如 2026-11 月度联合巡检" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="create.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="create.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="参与部门">
          <el-select v-model="create.participatingDeptCodes" multiple collapse-tags style="width:100%" placeholder="选择参与部门">
            <el-option v-for="d in departments" :key="d.code" :value="d.code" :label="d.name" />
          </el-select>
          <div class="hint">留空表示所有巡检部门均可参与。</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="create.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" @click="createSubmit">发起</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detail?.name" width="760px">
      <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="编号">{{ detail?.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail?.status === 'ACTIVE' ? '进行中' : '已截止' }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail?.createdByName }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ fmt(detail?.startAt) }}</el-descriptions-item>
        <el-descriptions-item label="截止">{{ fmt(detail?.endAt) }}</el-descriptions-item>
        <el-descriptions-item label="参与部门">{{ detailParticipatingNames }}</el-descriptions-item>
        <el-descriptions-item label="已巡检铺位">{{ (detailTargets||[]).length }}</el-descriptions-item>
        <el-descriptions-item label="整改完成">{{ closedCount }} / {{ (detailIssues||[]).length }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin:8px 0">实际巡检铺位 ({{ (detailTargets||[]).length }})</h4>
      <el-table :data="detailTargets" size="small" border empty-text="暂无巡检记录">
        <el-table-column label="铺位" min-width="180">
          <template #default="{ row }">{{ row.stallCode }} {{ row.stallName }}</template>
        </el-table-column>
        <el-table-column prop="floorCode" label="楼层" width="90" />
        <el-table-column label="巡检人部门" width="130">
          <template #default="{ row }">{{ deptName(row.deptCode) }}</template>
        </el-table-column>
        <el-table-column prop="submittedBy" label="提交人" width="110" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
        </el-table-column>
      </el-table>

      <h4 style="margin:8px 0">整改任务 ({{ (detailIssues||[]).length }})</h4>
      <el-table :data="detailIssues" size="small" border empty-text="暂无整改任务">
        <el-table-column prop="deptName" label="责任部门" width="110" />
        <el-table-column label="异常数" width="80" align="center">
          <template #default="{ row }">{{ (parseItems(row.itemsJson)||[]).length }}</template>
        </el-table-column>
        <el-table-column label="截止" width="160">
          <template #default="{ row }">{{ fmt(row.deadlineAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status==='CLOSED' ? 'success' : 'warning'" size="small">{{ row.status === 'CLOSED' ? '已整改' : '待整改' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const tasks = ref<any[]>([])
const activeTask = computed(() => tasks.value.find(t => t.status === 'ACTIVE'))
const departments = ref<any[]>([])
const createVisible = ref(false)
const create = ref<any>({ name: '', startAt: '', endAt: '', remark: '', participatingDeptCodes: [] })
const detailVisible = ref(false)
const detail = ref<any>(null)
const detailTargets = ref<any[]>([])
const detailIssues = ref<any[]>([])
const detailParticipatingNames = ref('全体部门')

onMounted(async () => {
  await loadDepartments()
  load()
})

async function loadDepartments() {
  try {
    const list: any = await http.get('/api/v1/qr-inspection/departments')
    departments.value = (list || []).filter((d: any) => d.deptGroup === 'OPERATION' || d.deptGroup === 'PROPERTY')
  } catch { departments.value = [] }
}

async function load() { tasks.value = (await http.get('/api/v1/qr-inspection/tasks')) as any[] || [] }

function deptName(code: string) { return departments.value.find(d => d.code === code)?.name || code || '-' }
function deptNames(codes?: string) {
  if (!codes) return '全体部门'
  return codes.split(',').map(c => deptName(c.trim())).join('、')
}
function fmt(t?: string) { return t ? String(t).replace('T', ' ').substring(0, 16) : '-' }
function parseItems(s?: string) { try { return JSON.parse(s || '[]') } catch { return [] } }
const closedCount = computed(() => (detailIssues.value || []).filter(i => i.status === 'CLOSED').length)

function openCreate() {
  const now = new Date()
  const end = new Date(now.getTime() + 24 * 3600 * 1000)
  create.value = { name: '', startAt: toIso(now), endAt: toIso(end), remark: '', participatingDeptCodes: [] }
  createVisible.value = true
}
function toIso(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`
}

async function createSubmit() {
  if (!create.value.name) { ElMessage.warning('请填写任务名称'); return }
  if (!create.value.endAt) { ElMessage.warning('请选择截止时间'); return }
  try {
    await http.post('/api/v1/qr-inspection/joint/create', create.value)
    ElMessage.success('已发起，卡片已推送到参与部门群')
    createVisible.value = false
    load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '发起失败') }
}

async function showDetail(r: any) {
  try {
    const res: any = await http.get('/api/v1/qr-inspection/tasks/' + r.id)
    detail.value = res.task
    detailTargets.value = res.targets || []
    detailIssues.value = res.issues || []
    detailParticipatingNames.value = res.participatingDeptNames || '全体部门'
    detailVisible.value = true
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '加载失败') }
}

async function openClose(r: any) {
  try {
    await ElMessageBox.confirm('截止后将按异常责任部门聚合整改任务并推送群消息，确认手动截止吗？', '手动截止')
    await http.post('/api/v1/qr-inspection/tasks/' + r.id + '/close')
    ElMessage.success('已截止，整改推送已触发')
    load()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '失败') }
}
</script>

<style scoped>
.page { padding: 16px; }
.desc { color:#6b7280; font-size:13px; margin:6px 0 12px; }
.toolbar { display:flex; gap:8px; margin-bottom:12px; }
.hint { color:#9ca3af; font-size:12px; margin-top:4px; }
</style>
