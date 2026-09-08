<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">历史用量同步</div>
        <div class="hint">长期保存月累计用量；日明细仅保留最近三个自然月</div>
      </div>
      <el-button @click="openExclusions">过滤名单（{{ activeExclusionCount }}）</el-button>
    </div>

    <el-card>
      <el-form class="sync-form" inline>
        <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="设备分组">
          <el-select v-model="form.groupId" clearable placeholder="全部设备">
            <el-option v-for="g in groups" :key="g.id" :label="g.group_name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="补采选项">
          <el-checkbox v-model="form.includeExcluded">包含过滤设备</el-checkbox>
          <el-checkbox v-model="form.monthlyOnly">仅同步月累计值</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="syncing" :disabled="hasRunning" @click="sync">
            {{ hasRunning ? '已有同步正在执行' : '开始同步' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert
        :title="form.includeExcluded
          ? `历史补采将包含过滤名单中的 ${activeExclusionCount} 台设备；日常同步时请取消该选项。无历史数据的设备会记为“无数据”，不会计为失败。`
          : `测量点按产品自动识别；同步会跳过过滤名单中已启用的 ${activeExclusionCount} 台设备。源平台正常返回但所选时段没有读数的设备会单列为“无数据”，不会计为失败。`"
        type="info"
        :closable="false"
      />
    </el-card>

    <el-card class="history">
      <template #header><b>最近同步记录</b></template>
      <el-table :data="batches" border>
        <el-table-column prop="batch_no" label="批次" width="190" />
        <el-table-column prop="start_date" label="开始" width="110" />
        <el-table-column prop="end_date" label="结束" width="110" />
        <el-table-column prop="requested" label="同步设备" width="90" />
        <el-table-column prop="excluded_devices" label="已过滤" width="80" />
        <el-table-column prop="succeeded" label="成功" width="75" />
        <el-table-column prop="no_data_devices" label="无数据" width="80" />
        <el-table-column prop="failed" label="失败" width="75" />
        <el-table-column prop="monthly_points" label="月数据" width="85" />
        <el-table-column prop="daily_points" label="日数据" width="85" />
        <el-table-column prop="purged_daily_points" label="清理日数据" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' || row.status === 'PARTIAL' ? 'warning' : 'danger'">
              {{ enumLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="error_message" label="说明/错误" min-width="220" show-overflow-tooltip />
        <el-table-column label="执行时间" width="180"><template #default="{ row }">{{ formatDateTime(row.started_at) }}</template></el-table-column>
        <el-table-column label="完成时间" width="180"><template #default="{ row }">{{ formatDateTime(row.completed_at) }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="exclusionVisible" title="用量同步过滤名单" width="min(960px, 94vw)" append-to-body>
      <el-alert title="只有启用的名单项会被跳过；该名单不影响设备资料同步、铺位绑定和已保存的历史用量。" type="warning" :closable="false" />
      <div v-if="canEdit" class="exclusion-add">
        <el-select
          v-model="exclusionForm.deviceIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="搜索并选择设备"
        >
          <el-option
            v-for="device in availableDevices"
            :key="device.platform_device_id"
            :label="deviceOptionLabel(device)"
            :value="device.platform_device_id"
          />
        </el-select>
        <el-input v-model="exclusionForm.reason" maxlength="255" placeholder="过滤原因，例如：设备长期离线" />
        <el-button @click="selectAllOffline">选择全部离线（{{ offlineAvailableCount }}）</el-button>
        <el-button type="primary" :disabled="!exclusionForm.deviceIds.length" :loading="savingExclusions" @click="addExclusions">
          加入名单
        </el-button>
      </div>
      <div class="exclusion-toolbar">
        <el-input v-model="exclusionKeyword" clearable placeholder="搜索设备名称、ID、通讯地址或原因" />
        <span>共 {{ filteredExclusions.length }} 条，启用 {{ activeExclusionCount }} 条</span>
      </div>
      <el-table :data="filteredExclusions" border max-height="440" v-loading="exclusionLoading">
        <el-table-column label="设备" min-width="210">
          <template #default="{ row }"><b>{{ row.device_name || '-' }}</b><div class="device-id">{{ row.platform_device_id }}</div></template>
        </el-table-column>
        <el-table-column prop="product_name" label="产品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="comm_address" label="通讯地址" width="120" />
        <el-table-column label="通讯" width="75">
          <template #default="{ row }"><el-tag size="small" :type="row.comm_status === 1 ? 'success' : 'danger'">{{ row.comm_status === 1 ? '在线' : '离线' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="reason" label="过滤原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="启用" width="72">
          <template #default="{ row }"><el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" :disabled="!canEdit" @change="toggleExclusion(row)" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="170"><template #default="{ row }">{{ formatDateTime(row.updated_at) }}</template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editReason(row)">原因</el-button>
            <el-button link type="danger" @click="removeExclusion(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { energyApi } from '@/api/energy'
import { enumLabel, formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canEdit = computed(() => userStore.user?.roles?.some(role => ['SUPER_ADMIN', 'SYSTEM_ADMIN'].includes(role)))
const groups = ref<any[]>([])
const batches = ref<any[]>([])
const exclusions = ref<any[]>([])
const deviceOptions = ref<any[]>([])
const syncing = ref(false)
const exclusionVisible = ref(false)
const exclusionLoading = ref(false)
const savingExclusions = ref(false)
const exclusionKeyword = ref('')
const hasRunning = computed(() => batches.value.some(row => row.status === 'RUNNING'))
const activeExclusionCount = computed(() => exclusions.value.filter(row => row.enabled === 1).length)
const excludedIds = computed(() => new Set(exclusions.value.map(row => row.platform_device_id)))
const availableDevices = computed(() => deviceOptions.value.filter(row => !excludedIds.value.has(row.platform_device_id)))
const offlineAvailableCount = computed(() => availableDevices.value.filter(row => row.comm_status !== 1).length)
const filteredExclusions = computed(() => {
  const keyword = exclusionKeyword.value.trim().toLowerCase()
  if (!keyword) return exclusions.value
  return exclusions.value.filter(row => [row.device_name, row.platform_device_id, row.comm_address, row.serial_number, row.reason]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})

let refreshTimer: number | undefined
const now = new Date()
const first = new Date(now.getFullYear(), now.getMonth() - 1, 1)
const last = new Date(now.getFullYear(), now.getMonth(), 0)
const fmt = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const form = reactive<any>({ startDate: fmt(first), endDate: fmt(last), groupId: undefined, includeExcluded: false, monthlyOnly: false })
const exclusionForm = reactive<{ deviceIds: string[]; reason: string }>({ deviceIds: [], reason: '离线设备，暂停用量同步' })

async function load() {
  const [groupRows, exclusionRows] = await Promise.all([energyApi.groups(), energyApi.syncExclusions()])
  groups.value = groupRows as any
  exclusions.value = exclusionRows as any
  await loadBatches()
}

async function loadBatches() { batches.value = await energyApi.syncBatches() as any }

async function sync() {
  const excludedText = form.includeExcluded ? `，包含过滤名单中的 ${activeExclusionCount.value} 台设备` : `，跳过过滤名单中的 ${activeExclusionCount.value} 台设备`
  const detailText = form.monthlyOnly ? '，不获取每日明细' : '，并补充最近三个月日明细'
  await ElMessageBox.confirm(`将逐月获取月累计值${detailText}${excludedText}。确定开始？`, '历史用量同步')
  syncing.value = true
  try {
    const result: any = await energyApi.syncUsage(form)
    ElMessage.success(`完成：同步 ${result.requested} 台，过滤 ${result.excludedDevices || 0} 台，成功 ${result.succeeded} 台，无数据 ${result.noDataDevices || 0} 台，失败 ${result.failed} 台`)
    await load()
  } finally {
    syncing.value = false
    await loadBatches()
  }
}

async function openExclusions() {
  exclusionVisible.value = true
  exclusionLoading.value = true
  try {
    const [exclusionRows, devices] = await Promise.all([energyApi.syncExclusions(), energyApi.devices()])
    exclusions.value = exclusionRows as any
    deviceOptions.value = devices as any
  } finally { exclusionLoading.value = false }
}

function deviceOptionLabel(device: any) {
  return `${device.device_name || '未命名设备'} · ${device.platform_device_id} · ${device.comm_status === 1 ? '在线' : '离线'}`
}

function selectAllOffline() {
  exclusionForm.deviceIds = availableDevices.value.filter(row => row.comm_status !== 1).map(row => row.platform_device_id)
  if (!exclusionForm.deviceIds.length) ElMessage.info('当前没有可加入的离线设备')
}

async function addExclusions() {
  savingExclusions.value = true
  try {
    const count = await energyApi.addSyncExclusions({ deviceIds: exclusionForm.deviceIds, reason: exclusionForm.reason }) as any
    ElMessage.success(`已将 ${count} 台设备加入过滤名单`)
    exclusionForm.deviceIds = []
    exclusions.value = await energyApi.syncExclusions() as any
  } finally { savingExclusions.value = false }
}

async function toggleExclusion(row: any) {
  try {
    await energyApi.updateSyncExclusion(row.platform_device_id, { enabled: row.enabled === 1, reason: row.reason })
    ElMessage.success(row.enabled === 1 ? '已启用过滤' : '已停用过滤，该设备将恢复同步')
  } catch (error) {
    row.enabled = row.enabled === 1 ? 0 : 1
    throw error
  }
}

async function editReason(row: any) {
  const result = await ElMessageBox.prompt('请输入过滤原因', '修改过滤原因', { inputValue: row.reason || '', inputPlaceholder: '例如：设备长期离线' })
  await energyApi.updateSyncExclusion(row.platform_device_id, { enabled: row.enabled === 1, reason: result.value })
  row.reason = result.value
  ElMessage.success('过滤原因已更新')
}

async function removeExclusion(row: any) {
  await ElMessageBox.confirm(`确认将“${row.device_name || row.platform_device_id}”移出过滤名单？`, '移除过滤设备', { type: 'warning' })
  await energyApi.deleteSyncExclusion(row.platform_device_id)
  exclusions.value = await energyApi.syncExclusions() as any
  ElMessage.success('已移出过滤名单')
}

onMounted(async () => {
  await load()
  refreshTimer = window.setInterval(loadBatches, 10000)
})
onUnmounted(() => { if (refreshTimer) window.clearInterval(refreshTimer) })
</script>

<style scoped>
.page { padding: 18px; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.page-title { font-size: 22px; font-weight: 700; }
.hint, .device-id, .exclusion-toolbar span { color: #909399; font-size: 13px; }
.device-id { margin-top: 3px; font-family: monospace; }
.history { margin-top: 14px; }
.sync-form .el-select { width: 180px; }
.exclusion-add { display: grid; grid-template-columns: minmax(280px, 2fr) minmax(220px, 1.4fr) auto auto; gap: 8px; margin: 14px 0 12px; }
.exclusion-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 12px 0; }
.exclusion-toolbar .el-input { max-width: 380px; }
@media (max-width: 768px) {
  .page { padding: 10px; }
  .page-header { align-items: flex-start; }
  .sync-form { display: flex; flex-direction: column; }
  .sync-form :deep(.el-form-item), .sync-form :deep(.el-form-item__content), .sync-form :deep(.el-date-editor), .sync-form .el-select, .sync-form .el-button { width: 100%; }
  .exclusion-add { grid-template-columns: 1fr; }
  .exclusion-toolbar { align-items: flex-start; flex-direction: column; }
  .exclusion-toolbar .el-input { max-width: none; width: 100%; }
}
</style>
