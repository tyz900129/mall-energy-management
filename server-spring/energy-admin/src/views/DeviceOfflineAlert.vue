<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">设备离线告警</div>
        <div class="page-note">按网关 → 端口 → 设备三级巡检通讯状态，连续离线达到阈值后向 Webhook 推送；已加入不监控清单的设备不再告警。
          当前实时离线 <b class="stat">{{ offlineSummary.total }}</b> 台设备（其中已加入不监控 {{ offlineSummary.excludedCount }} 台）。</div>
      </div>
      <div class="header-actions">
        <el-button :loading="offlineLoading" @click="loadOfflineDevices">刷新离线数</el-button>
        <el-button type="primary" :loading="running" @click="runNow">立即巡检</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="告警事件" name="events">
        <el-card shadow="never">
          <el-form inline>
            <el-form-item label="告警级别">
              <el-select v-model="eventQuery.level" clearable placeholder="全部" style="width:140px">
                <el-option label="网关" value="GATEWAY" />
                <el-option label="端口" value="PORT" />
                <el-option label="设备" value="DEVICE" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="eventQuery.status" clearable placeholder="全部" style="width:140px">
                <el-option label="未恢复" value="OPEN" />
                <el-option label="已恢复" value="RECOVERED" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button @click="searchEvents">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="events" v-loading="eventLoading" border empty-text="暂无离线告警">
            <el-table-column label="级别" width="90">
              <template #default="{row}"><el-tag :type="levelType(row.level)">{{levelLabel(row.level)}}</el-tag></template>
            </el-table-column>
            <el-table-column label="网关" min-width="180"><template #default="{row}">{{row.gatewayName||row.gatewayId||'-'}}</template></el-table-column>
            <el-table-column label="端口" min-width="120"><template #default="{row}">{{row.channelName||row.channelId||'-'}}</template></el-table-column>
            <el-table-column label="设备" min-width="170"><template #default="{row}">{{row.deviceName||row.platformDeviceId||'-'}}</template></el-table-column>
            <el-table-column prop="affectedCount" label="影响设备数" width="105" />
            <el-table-column label="首次离线" width="170"><template #default="{row}">{{formatDateTime(row.firstOfflineAt)}}</template></el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{row}"><el-tag :type="row.status==='OPEN'?'danger':'success'">{{row.status==='OPEN'?'未恢复':'已恢复'}}</el-tag></template>
            </el-table-column>
            <el-table-column label="通知时间" width="170"><template #default="{row}">{{formatDateTime(row.notifiedAt)}}</template></el-table-column>
            <el-table-column label="恢复时间" width="170"><template #default="{row}">{{formatDateTime(row.recoveredAt)}}</template></el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="eventPage.pageNum" v-model:page-size="eventPage.pageSize" :total="eventTotal" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadEvents" @size-change="searchEvents" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="不监控清单" name="exclusions">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="excludeQuery.keyword" clearable placeholder="设备编号或名称" style="max-width:260px" @keyup.enter="loadExclusions" />
            <el-select v-model="excludeQuery.enabled" clearable placeholder="全部状态" style="width:140px">
              <el-option label="生效中" :value="true" />
              <el-option label="已停用" :value="false" />
            </el-select>
            <el-button @click="loadExclusions">查询</el-button>
            <el-button type="primary" @click="openExcludeDialog">从离线设备添加</el-button>
          </div>
          <el-table :data="exclusions" v-loading="excludeLoading" border empty-text="暂无设备，维护或已拆除的设备可加入此清单">
            <el-table-column prop="platformDeviceId" label="设备编号" min-width="160" />
            <el-table-column prop="deviceName" label="设备名称" min-width="180" />
            <el-table-column label="原因" width="120"><template #default="{row}">{{reasonLabel(row.reason)}}</template></el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160"><template #default="{row}">{{row.remark||'-'}}</template></el-table-column>
            <el-table-column label="生效区间" min-width="200"><template #default="{row}">{{range(row.effectiveFrom,row.effectiveTo)}}</template></el-table-column>
            <el-table-column label="状态" width="95"><template #default="{row}"><el-tag :type="Number(row.enabled)?'success':'info'">{{Number(row.enabled)?'生效中':'已停用'}}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="danger" @click="removeExclusion(row)">删除</el-button></template></el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="excludePage.pageNum" v-model:page-size="excludePage.pageSize" :total="excludeTotal" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadExclusions" @size-change="searchExclusions" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="监控设置" name="settings">
        <el-card shadow="never">
          <el-form label-width="150px" style="max-width:560px">
            <el-form-item label="启用离线巡检"><el-switch v-model="form.enabled" /></el-form-item>
            <el-form-item label="巡检间隔"><el-input-number v-model="form.checkIntervalMinutes" :min="5" :max="1440" /><span class="unit">分钟</span></el-form-item>
            <el-form-item label="连续离线次数"><el-input-number v-model="form.consecutiveCount" :min="1" :max="10" /><span class="unit">次（达到后才告警）</span></el-form-item>
            <el-form-item label="离线判定阈值"><el-input-number v-model="form.offlineThresholdMinutes" :min="1" :max="1440" /><span class="unit">分钟无数据视为离线</span></el-form-item>
            <el-form-item label="静默时段"><el-time-picker v-model="form.quietStart" value-format="HH:mm:ss" placeholder="开始" style="width:150px" /><span class="unit">至</span><el-time-picker v-model="form.quietEnd" value-format="HH:mm:ss" placeholder="结束" style="width:150px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="saveSettings">保存设置</el-button><el-button @click="loadSettings">重置</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="运行记录" name="runs">
        <el-card shadow="never">
          <el-table :data="runs" border empty-text="暂无巡检记录">
            <el-table-column label="触发方式" width="110"><template #default="{row}">{{row.triggerType==='MANUAL'?'手动':'自动'}}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="statusType(row.status)">{{statusLabel(row.status)}}</el-tag></template></el-table-column>
            <el-table-column prop="scannedCount" label="扫描设备" width="100" />
            <el-table-column prop="alertedCount" label="新增告警" width="100" />
            <el-table-column prop="recoveredCount" label="恢复" width="90" />
            <el-table-column label="开始时间" width="170"><template #default="{row}">{{formatDateTime(row.startedAt)}}</template></el-table-column>
            <el-table-column label="结束时间" width="170"><template #default="{row}">{{formatDateTime(row.completedAt)}}</template></el-table-column>
            <el-table-column prop="errorMessage" label="结果" min-width="200"><template #default="{row}">{{row.errorMessage||'完成'}}</template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="excludeVisible" title="从离线设备添加到不监控清单" width="900px">
      <div class="dialog-tip">
        当前实时离线 {{ offlineSummary.total }} 台，已加入不监控 {{ offlineSummary.excludedCount }} 台；勾选后不再告警。
      </div>
      <el-table :data="offlineList" v-loading="offlineLoading" border height="360" @selection-change="onSelectOffline" empty-text="当前没有离线设备">
        <el-table-column type="selection" width="48" :selectable="selectableRow" />
        <el-table-column prop="deviceName" label="设备名称" min-width="180"><template #default="{row}">{{row.deviceName||'-'}}</template></el-table-column>
        <el-table-column prop="platformDeviceId" label="设备编号" min-width="150" />
        <el-table-column prop="gatewayName" label="网关" min-width="170"><template #default="{row}">{{row.gatewayName||'-'}}</template></el-table-column>
        <el-table-column prop="channelName" label="端口" width="110"><template #default="{row}">{{row.channelName||'-'}}</template></el-table-column>
        <el-table-column label="最后采集" width="165"><template #default="{row}">{{row.lastAcqTime||'-'}}</template></el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{row}"><el-tag :type="row.excluded?'info':'danger'">{{row.excluded?'已加入':'离线'}}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="原因">
          <el-select v-model="excludeForm.reason" style="width:100%">
            <el-option label="维护中" value="MAINTENANCE" />
            <el-option label="已拆除/停用" value="RETIRED" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="excludeForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-tip">已选 {{ selectedDevices.length }} 台</span>
        <el-button @click="excludeVisible=false">取消</el-button>
        <el-button type="primary" :loading="savingExclude" :disabled="!selectedDevices.length" @click="saveExclusion">加入不监控清单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceAlertApi } from '@/api/deviceAlert'
import { formatDateTime } from '@/utils/format'

const tab = ref('events')
const events = ref<any[]>([])
const exclusions = ref<any[]>([])
const runs = ref<any[]>([])
const eventTotal = ref(0)
const excludeTotal = ref(0)
const eventLoading = ref(false)
const excludeLoading = ref(false)
const running = ref(false)
const excludeVisible = ref(false)
const offlineLoading = ref(false)
const savingExclude = ref(false)
const offlineList = ref<any[]>([])
const offlineSummary = reactive({ total: 0, excludedCount: 0 })
const selectedDevices = ref<any[]>([])

const eventQuery = reactive<{ level?: string; status?: string }>({})
const eventPage = reactive({ pageNum: 1, pageSize: 20 })
const excludeQuery = reactive<{ keyword: string; enabled?: boolean }>({ keyword: '' })
const excludePage = reactive({ pageNum: 1, pageSize: 20 })
const form = reactive<any>({ enabled: true, checkIntervalMinutes: 120, consecutiveCount: 2, offlineThresholdMinutes: 30, quietStart: null, quietEnd: null })
const excludeForm = reactive<any>({ reason: 'MAINTENANCE', remark: '' })

const levelLabel = (v: string) => ({ GATEWAY: '网关', PORT: '端口', DEVICE: '设备' } as any)[v] || v
const levelType = (v: string) => (v === 'GATEWAY' ? 'danger' : v === 'PORT' ? 'warning' : 'info')
const reasonLabel = (v: string) => ({ MAINTENANCE: '维护中', RETIRED: '已拆除/停用', OTHER: '其他' } as any)[v] || v || '-'
const statusLabel = (v: string) => ({ RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败' } as any)[v] || v
const statusType = (v: string) => (v === 'SUCCESS' ? 'success' : v === 'RUNNING' ? 'warning' : 'danger')
const range = (from: any, to: any) => (!from && !to ? '长期有效' : `${from || '不限'} ~ ${to || '不限'}`)

async function loadEvents() {
  eventLoading.value = true
  try {
    const r: any = await deviceAlertApi.events({ ...eventPage, level: eventQuery.level || undefined, status: eventQuery.status || undefined })
    events.value = r.records || []
    eventTotal.value = Number(r.total || 0)
  } finally {
    eventLoading.value = false
  }
}
function searchEvents() {
  eventPage.pageNum = 1
  loadEvents()
}

async function loadExclusions() {
  excludeLoading.value = true
  try {
    const r: any = await deviceAlertApi.exclusions({ ...excludePage, keyword: excludeQuery.keyword || undefined, enabled: excludeQuery.enabled })
    exclusions.value = r.records || []
    excludeTotal.value = Number(r.total || 0)
  } finally {
    excludeLoading.value = false
  }
}
function searchExclusions() {
  excludePage.pageNum = 1
  loadExclusions()
}

async function loadSettings() {
  const r: any = await deviceAlertApi.settings()
  Object.assign(form, {
    enabled: !!Number(r.enabled),
    checkIntervalMinutes: Number(r.checkIntervalMinutes || 120),
    consecutiveCount: Number(r.consecutiveCount || 2),
    offlineThresholdMinutes: Number(r.offlineThresholdMinutes || 30),
    quietStart: r.quietStart || null,
    quietEnd: r.quietEnd || null
  })
}

async function saveSettings() {
  await deviceAlertApi.saveSettings({ ...form })
  ElMessage.success('监控设置已保存')
  await loadSettings()
}

async function loadRuns() {
  runs.value = (await deviceAlertApi.runs()) as any
}

async function runNow() {
  await ElMessageBox.confirm('将立即扫描全部设备通讯状态，并向已配置的设备离线 Webhook 推送新告警，是否继续？', '立即巡检')
  running.value = true
  try {
    await deviceAlertApi.run()
    ElMessage.success('设备离线巡检已完成')
    await Promise.all([loadEvents(), loadRuns(), loadOfflineDevices()])
  } finally {
    running.value = false
  }
}

async function loadOfflineDevices() {
  offlineLoading.value = true
  try {
    const r: any = await deviceAlertApi.offlineDevices()
    offlineList.value = r.devices || []
    offlineSummary.total = Number(r.total || 0)
    offlineSummary.excludedCount = Number(r.excludedCount || 0)
  } finally {
    offlineLoading.value = false
  }
}

function onSelectOffline(rows: any[]) {
  selectedDevices.value = rows
}
const selectableRow = (row: any) => !row.excluded

async function openExcludeDialog() {
  Object.assign(excludeForm, { reason: 'MAINTENANCE', remark: '' })
  selectedDevices.value = []
  excludeVisible.value = true
  await loadOfflineDevices()
}

async function saveExclusion() {
  if (!selectedDevices.value.length) {
    ElMessage.warning('请勾选要加入不监控清单的离线设备')
    return
  }
  savingExclude.value = true
  try {
    const r: any = await deviceAlertApi.addExclusionBatch({
      devices: selectedDevices.value.map((d) => ({ platformDeviceId: d.platformDeviceId, deviceName: d.deviceName || '' })),
      reason: excludeForm.reason,
      remark: excludeForm.remark
    })
    ElMessage.success(`已加入不监控清单 ${r?.added ?? selectedDevices.value.length} 台设备`)
    excludeVisible.value = false
    await Promise.all([loadExclusions(), loadOfflineDevices()])
  } finally {
    savingExclude.value = false
  }
}

async function removeExclusion(row: any) {
  await ElMessageBox.confirm(`确认将设备「${row.deviceName || row.platformDeviceId}」移出不监控清单？移出后将恢复离线告警。`, '删除确认', { type: 'warning' })
  await deviceAlertApi.deleteExclusion(Number(row.id))
  ElMessage.success('已移出不监控清单')
  await Promise.all([loadExclusions(), loadOfflineDevices()])
}

watch(tab, (v) => {
  if (v === 'events') loadEvents()
  if (v === 'exclusions') loadExclusions()
  if (v === 'settings') loadSettings()
  if (v === 'runs') loadRuns()
})

onMounted(() => Promise.all([loadEvents(), loadRuns(), loadOfflineDevices()]))
</script>

<style scoped>
.page-note { margin-top: 6px; color: #909399; font-size: 13px; }
.stat { color: #f56c6c; font-size: 15px; }
.header-actions { display: flex; gap: 10px; }
.dialog-tip { color: #909399; font-size: 13px; margin-bottom: 10px; }
.dialog-form { margin-top: 14px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.unit { margin-left: 8px; color: #909399; }
@media (max-width: 768px) {
  .page-header { align-items: flex-start; gap: 10px; }
  .el-form { display: flex; flex-direction: column; }
  .el-form-item { margin-right: 0; }
  .pager { overflow-x: auto; justify-content: flex-start; }
}
</style>
