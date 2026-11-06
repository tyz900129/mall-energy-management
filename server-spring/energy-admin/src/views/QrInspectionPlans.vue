<template>
  <div class="page">
    <h2>周期巡检计划</h2>
    <div class="desc">按部门配置「每月某日某时」自动推送巡检卡片到部门群，人员从卡片链接进入后扫码或搜索铺位填报。</div>
    <div class="toolbar">
      <el-select v-model="filterScene" placeholder="全部场景" clearable size="small" style="width:180px" @change="load">
        <el-option label="商管仓库巡检" value="WAREHOUSE_COMMERCIAL" />
        <el-option label="物业巡检" value="WAREHOUSE_PROPERTY" />
      </el-select>
      <el-button type="primary" size="small" @click="openCreate">+ 新建计划</el-button>
    </div>
    <el-table :data="plans" border size="small">
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column label="场景" width="150">
        <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
      </el-table-column>
      <el-table-column label="责任部门" width="160">
        <template #default="{ row }">{{ deptName(row.deptCode) }}</template>
      </el-table-column>
      <el-table-column label="执行规则" width="150">
        <template #default="{ row }">每月 {{ row.dayOfMonth }} 日 {{ row.pushTime }}</template>
      </el-table-column>
      <el-table-column prop="nextDueAt" label="下次推送" width="170" />
      <el-table-column prop="lastRunDate" label="上次执行" width="120" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="showRounds(row)">轮次</el-button>
          <el-button link :type="row.enabled ? 'danger' : 'primary'" size="small" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑计划' : '新建计划'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="巡检场景">
          <el-select v-model="form.scene" style="width:100%" @change="onSceneChange">
            <el-option label="商管仓库巡检（商管处）" value="WAREHOUSE_COMMERCIAL" />
            <el-option label="物业巡检（物业课）" value="WAREHOUSE_PROPERTY" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任部门">
          <el-select v-model="form.deptCode" style="width:100%" placeholder="选择部门">
            <el-option v-for="d in deptOptions" :key="d.code" :value="d.code" :label="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="每月执行日">
          <el-input-number v-model="form.dayOfMonth" :min="1" :max="31" />
          <span class="hint">每月该日自动推送（短月自动取月末）</span>
        </el-form-item>
        <el-form-item label="推送时间">
          <el-time-select v-model="form.pushTime" start="00:00" step="00:15" end="23:45" placeholder="选择时间" style="width:140px" />
        </el-form-item>
        <el-form-item label="巡检表单">
          <el-select v-model="form.templateId" style="width:100%" clearable placeholder="默认按场景自动匹配">
            <el-option v-for="t in templateOptions" :key="t.id" :value="t.id" :label="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roundVisible" title="执行轮次" width="560px">
      <el-table :data="rounds" border size="small">
        <el-table-column prop="dueDate" label="应执行日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">{{ row.status === 'COMPLETED' ? '已完成' : '待巡检' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pushedAt" label="推送时间" width="170" />
        <el-table-column prop="completedAt" label="完成时间" width="170" />
        <el-table-column prop="recordCount" label="巡检记录" width="90" align="center" />
      </el-table>
      <div v-if="rounds.length === 0" class="empty">暂无轮次</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const plans = ref<any[]>([])
const filterScene = ref('')
const departments = ref<any[]>([])
const templates = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref<any>(null)
const roundVisible = ref(false)
const rounds = ref<any[]>([])

const emptyForm = () => ({
  scene: 'WAREHOUSE_COMMERCIAL', deptCode: '', dayOfMonth: 1, pushTime: '09:00',
  templateId: null, enabled: 1, remark: '', nextDueAt: null
})
const form = ref<any>(emptyForm())

const deptOptions = computed(() => {
  const group = form.value.scene === 'WAREHOUSE_PROPERTY' ? 'PROPERTY' : 'OPERATION'
  return departments.value.filter(d => d.deptGroup === group)
})
const templateOptions = computed(() => templates.value.filter(t => !form.value.scene || t.scene === form.value.scene))

onMounted(async () => {
  await loadDepartments()
  await loadTemplates()
  load()
})

async function loadDepartments() {
  try { departments.value = (await http.get('/api/v1/qr-inspection/departments')) as any[] || [] }
  catch { departments.value = [] }
}
async function loadTemplates() {
  try { templates.value = (await http.get('/api/v1/qr-inspection/templates')) as any[] || [] }
  catch { templates.value = [] }
}

async function load() {
  const params = filterScene.value ? { scene: filterScene.value } : {}
  plans.value = (await http.get('/api/v1/qr-inspection/plans', { params })) as any[] || []
}

function sceneLabel(s: string) {
  return s === 'WAREHOUSE_PROPERTY' ? '物业巡检' : s === 'WAREHOUSE_COMMERCIAL' ? '商管仓库巡检' : s
}
function deptName(code: string) {
  return departments.value.find(d => d.code === code)?.name || code || '-'
}

function onSceneChange() {
  form.value.deptCode = ''
  if (form.value.templateId) form.value.templateId = null
}

function openCreate() {
  editing.value = null
  form.value = emptyForm()
  dialogVisible.value = true
}
function openEdit(r: any) {
  editing.value = r
  form.value = { ...r }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.deptCode) { ElMessage.warning('请选择责任部门'); return }
  try {
    if (editing.value) form.value.id = editing.value.id
    await http.post('/api/v1/qr-inspection/plans', form.value)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}

async function toggle(r: any) {
  try {
    await http.post('/api/v1/qr-inspection/plans', { ...r, enabled: r.enabled ? 0 : 1 })
    load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

async function showRounds(r: any) {
  try {
    rounds.value = (await http.get('/api/v1/qr-inspection/plans/' + r.id + '/rounds')) as any[] || []
    roundVisible.value = true
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '加载失败') }
}
</script>

<style scoped>
.page { padding: 16px; }
.desc { color:#6b7280; font-size:13px; margin:6px 0 12px; }
.toolbar { display:flex; gap:8px; margin-bottom:12px; }
.hint { color:#9ca3af; font-size:12px; margin-left:8px; }
.empty { color:#9ca3af; text-align:center; padding:16px; }
</style>
