<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { calendarApi } from '@/api/calendar'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('calendar.manage'))

const rows = ref<any[]>([])
const loading = ref(false)
const query = reactive({ keyword: '', category: '', status: '' })
const options = reactive<{ departments: any[]; roles: any[]; users: any[] }>({ departments: [], roles: [], users: [] })

const pad = (n: number) => String(n).padStart(2, '0')
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function blankPlan() {
  return {
    id: null as number | null,
    name: '',
    category: 'DAILY',
    ownerType: 'DEPT',
    ownerId: null as number | null,
    targetMode: 'UNIT',
    claimMode: 'CLAIM',
    frequencyType: 'DAILY',
    frequencyValue: 1,
    startDate: today(),
    endDate: '',
    executeTime: '09:00',
    overdueMinutes: 1440,
    formMode: 'NONE',
    requirePhoto: false,
    advanceMinutes: 0,
    urgeMinutes: 30,
    enabled: true
  }
}

const planVisible = ref(false)
const saving = ref(false)
const planForm = reactive<any>(blankPlan())

const logVisible = ref(false)
const logLoading = ref(false)
const logRows = ref<any[]>([])
const logPlanName = ref('')

const ownerChoices = computed(() => {
  if (planForm.ownerType === 'DEPT') return options.departments.map((d: any) => ({ value: d.id, label: d.name }))
  if (planForm.ownerType === 'ROLE') return options.roles.map((r: any) => ({ value: r.id, label: r.name }))
  return options.users.map((u: any) => ({ value: u.id, label: u.name }))
})

watch(
  () => planForm.ownerType,
  (type) => {
    planForm.ownerId = null
    planForm.claimMode = type === 'USER' ? 'NONE' : 'CLAIM'
    if (type !== 'DEPT') planForm.targetMode = 'UNIT'
  }
)

const categoryText = (c: string) =>
  ({ DAILY: '日常', SAFETY: '安全', CLEANING: '保洁', OPERATION: '运维', MEETING: '会议', OTHER: '其他' } as any)[c] || c || '-'

function cycleText(row: any) {
  const n = Number(row.frequencyValue || 1)
  if (row.frequencyType === 'WEEKLY') return n > 1 ? `每 ${n} 周` : '每周'
  if (row.frequencyType === 'MONTHLY') return n > 1 ? `每 ${n} 月` : '每月'
  if (row.frequencyType === 'INTERVAL_DAYS') return `每 ${n} 天`
  return n > 1 ? `每 ${n} 天` : '每天'
}

const ownerText = (row: any) => {
  const name = row.ownerName || '-'
  return row.ownerDeptName && row.ownerDeptName !== '-' ? `${name}（${row.ownerDeptName}）` : name
}

const fmtTime = (v: any) => (v ? String(v).slice(0, 5) : '-')
const fmtDateTime = (v: any) => (v ? formatDateTime(v) : '-')

async function load() {
  loading.value = true
  try {
    rows.value = ((await calendarApi.plans({ ...query })) as any) || []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.category = ''
  query.status = ''
  load()
}

async function editPlan(row?: any) {
  if (!row) {
    Object.assign(planForm, blankPlan())
    planVisible.value = true
    return
  }
  try {
    const d: any = await calendarApi.plan(row.id)
    Object.assign(planForm, {
      id: d.id,
      name: d.name,
      category: d.category || 'OTHER',
      ownerType: d.ownerType || 'DEPT',
      ownerId: d.ownerId,
      targetMode: d.targetMode || 'UNIT',
      claimMode: d.claimMode || 'CLAIM',
      frequencyType: d.frequencyType || 'DAILY',
      frequencyValue: Number(d.frequencyValue || 1),
      startDate: d.startDate ? String(d.startDate).slice(0, 10) : today(),
      endDate: d.endDate ? String(d.endDate).slice(0, 10) : '',
      executeTime: d.executeTime ? String(d.executeTime).slice(0, 5) : '09:00',
      overdueMinutes: Number(d.overdueMinutes || 1440),
      formMode: d.formMode || 'NONE',
      requirePhoto: Number(d.requirePhoto || 0) === 1,
      advanceMinutes: Number(d.advanceMinutes || 0),
      urgeMinutes: Number(d.urgeMinutes || 30),
      enabled: Number(d.enabled || 0) === 1
    })
    planVisible.value = true
  } catch {
    /* 错误提示已由 http 拦截器处理 */
  }
}

async function savePlan() {
  if (!planForm.name.trim()) return ElMessage.warning('请填写计划名称')
  if (!planForm.ownerId) return ElMessage.warning('请选择责任对象')
  if (!planForm.startDate) return ElMessage.warning('请选择开始日期')
  saving.value = true
  try {
    const payload = {
      ...planForm,
      enabled: planForm.enabled ? 1 : 0,
      requirePhoto: planForm.requirePhoto ? 1 : 0,
      endDate: planForm.endDate || null
    }
    if (planForm.id) await calendarApi.updatePlan(planForm.id, payload)
    else await calendarApi.createPlan(payload)
    ElMessage.success('计划已保存')
    planVisible.value = false
    await load()
  } catch {
    /* 错误提示已由 http 拦截器处理 */
  } finally {
    saving.value = false
  }
}

async function togglePlan(row: any) {
  try {
    await ElMessageBox.confirm(`确认${row.enabled ? '暂停' : '启用'}「${row.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await calendarApi.togglePlan(row.id, !row.enabled)
    ElMessage.success(row.enabled ? '已暂停' : '已启用')
    await load()
  } catch {
    /* 错误提示已由 http 拦截器处理 */
  }
}

async function generateNow(row: any) {
  try {
    await calendarApi.generatePlan(row.id)
    ElMessage.success('已生成今日任务')
    await load()
  } catch {
    /* 错误提示已由 http 拦截器处理 */
  }
}

async function openLogs(row: any) {
  logPlanName.value = row.name
  logVisible.value = true
  logLoading.value = true
  try {
    const d: any = await calendarApi.plan(row.id)
    logRows.value = d.logs || []
  } catch {
    logRows.value = []
  } finally {
    logLoading.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    const data: any = await calendarApi.ownerOptions()
    options.departments = data.departments || []
    options.roles = data.roles || []
    options.users = data.users || []
  } catch {
    /* 选项加载失败时仍可查看列表 */
  }
})
</script>

<template>
  <div class="plan-page">
    <div class="page-head">
      <div>
        <h2>日历计划</h2>
        <p>按周期自动生成任务中心任务；生成结果可在任务日历与任务列表中查看。</p>
      </div>
      <div class="head-actions">
        <el-button @click="planVisible = false; load()">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="editPlan()">新建计划</el-button>
      </div>
    </div>

    <div class="filters">
      <el-input v-model="query.keyword" clearable placeholder="计划名称或责任对象" style="width: 220px" @keyup.enter="load" />
      <el-select v-model="query.category" clearable placeholder="全部分类" style="width: 140px" @change="load">
        <el-option label="日常" value="DAILY" />
        <el-option label="安全" value="SAFETY" />
        <el-option label="保洁" value="CLEANING" />
        <el-option label="运维" value="OPERATION" />
        <el-option label="会议" value="MEETING" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px" @change="load">
        <el-option label="启用" value="1" />
        <el-option label="暂停" value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无日历计划" class="plan-table">
      <el-table-column prop="name" label="计划名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="分类" width="90">
        <template #default="{ row }">{{ categoryText(row.category) }}</template>
      </el-table-column>
      <el-table-column label="责任对象" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ ownerText(row) }}</template>
      </el-table-column>
      <el-table-column label="周期" width="110">
        <template #default="{ row }">{{ cycleText(row) }}</template>
      </el-table-column>
      <el-table-column label="执行时间" width="100">
        <template #default="{ row }">{{ fmtTime(row.executeTime) }}</template>
      </el-table-column>
      <el-table-column label="下次生成" width="150">
        <template #default="{ row }">{{ fmtDateTime(row.nextRunAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'warning'">{{ row.enabled ? '启用' : '暂停' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage" link type="primary" @click="editPlan(row)">编辑</el-button>
          <el-button v-if="canManage" link @click="generateNow(row)">立即生成</el-button>
          <el-button v-if="canManage" link :type="row.enabled ? 'warning' : 'success'" @click="togglePlan(row)">
            {{ row.enabled ? '暂停' : '启用' }}
          </el-button>
          <el-button link @click="openLogs(row)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="planVisible" :title="planForm.id ? '编辑日历计划' : '新建日历计划'" width="720px" destroy-on-close>
      <el-form label-width="96px" class="plan-form">
        <el-form-item label="计划名称" required>
          <el-input v-model="planForm.name" maxlength="100" show-word-limit placeholder="例如：配电房周巡检" />
        </el-form-item>
        <el-form-item label="任务分类">
          <el-select v-model="planForm.category" style="width: 180px">
            <el-option label="日常" value="DAILY" />
            <el-option label="安全" value="SAFETY" />
            <el-option label="保洁" value="CLEANING" />
            <el-option label="运维" value="OPERATION" />
            <el-option label="会议" value="MEETING" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任归属" required>
          <div class="inline">
            <el-select v-model="planForm.ownerType" style="width: 110px">
              <el-option label="部门" value="DEPT" />
              <el-option label="岗位" value="ROLE" />
              <el-option label="个人" value="USER" />
            </el-select>
            <el-select v-model="planForm.ownerId" filterable placeholder="选择责任对象" style="width: 240px">
              <el-option v-for="o in ownerChoices" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item v-if="planForm.ownerType === 'DEPT'" label="下发方式">
          <el-radio-group v-model="planForm.targetMode">
            <el-radio label="UNIT">部门整体一条任务</el-radio>
            <el-radio label="PER_CHILD">每个子部门各一条</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="认领方式">
          <el-select v-model="planForm.claimMode" style="width: 180px">
            <el-option label="需认领" value="CLAIM" />
            <el-option label="自动指派" value="AUTO" />
            <el-option label="无需认领" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行频率" required>
          <div class="inline">
            <el-select v-model="planForm.frequencyType" style="width: 130px">
              <el-option label="每天" value="DAILY" />
              <el-option label="每周" value="WEEKLY" />
              <el-option label="每月" value="MONTHLY" />
              <el-option label="按天间隔" value="INTERVAL_DAYS" />
            </el-select>
            <template v-if="planForm.frequencyType !== 'DAILY'">
              <span class="hint">每</span>
              <el-input-number v-model="planForm.frequencyValue" :min="1" :max="90" controls-position="right" style="width: 100px" />
              <span class="hint">{{ planForm.frequencyType === 'WEEKLY' ? '周' : planForm.frequencyType === 'MONTHLY' ? '月' : '天' }}</span>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="执行日期" required>
          <div class="inline">
            <el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 160px" />
            <span class="hint">至</span>
            <el-date-picker v-model="planForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="长期有效" style="width: 160px" />
          </div>
        </el-form-item>
        <el-form-item label="执行时间" required>
          <el-time-picker v-model="planForm.executeTime" value-format="HH:mm" format="HH:mm" placeholder="选择时间" style="width: 160px" />
          <span class="hint">任务在该时间生成并进入任务中心</span>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="planForm.overdueMinutes" :min="0" :step="30" controls-position="right" style="width: 140px" />
          <span class="hint">分钟，0 表示不限制</span>
        </el-form-item>
        <el-form-item label="回执模式">
          <el-select v-model="planForm.formMode" style="width: 200px">
            <el-option label="仅确认完成" value="NONE" />
            <el-option label="表单回执" value="CHECKLIST" />
          </el-select>
          <el-checkbox v-model="planForm.requirePhoto" class="ml">必须上传现场照片</el-checkbox>
        </el-form-item>
        <el-form-item label="提醒">
          <div class="inline">
            <span class="hint">提前</span>
            <el-input-number v-model="planForm.advanceMinutes" :min="0" :step="10" controls-position="right" style="width: 110px" />
            <span class="hint">分钟推送；超时后每</span>
            <el-input-number v-model="planForm.urgeMinutes" :min="0" :step="10" controls-position="right" style="width: 110px" />
            <span class="hint">分钟催办</span>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="planForm.enabled" />
          <span class="hint">保存后立即生成当天任务（若在有效期内）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logVisible" :title="`生成日志 · ${logPlanName}`" width="760px">
      <el-table v-loading="logLoading" :data="logRows" empty-text="最近 7 天暂无生成记录" max-height="380">
        <el-table-column prop="occurDate" label="生成日期" width="120" />
        <el-table-column prop="periodKey" label="周期标识" min-width="140" />
        <el-table-column prop="taskCount" label="任务数" width="80" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.plan-page {
  padding: 16px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.page-head p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.plan-table {
  border-radius: 8px;
}
.inline {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.ml {
  margin-left: 12px;
}
</style>
