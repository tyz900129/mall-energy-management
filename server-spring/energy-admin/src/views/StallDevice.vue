<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">铺位设备绑定</div>
    </div>

    <el-tabs v-model="tab">
      <!-- 1. 选铺位看其已绑设备 -->
      <el-tab-pane label="按铺位维护" name="byStall">
        <div class="toolbar">
          <el-select v-model="stallId" placeholder="选择铺位" filterable style="width: 360px" @change="loadStallDevices">
            <el-option v-for="s in stalls" :key="s.id" :label="`${s.stallCode} - ${s.stallName}`" :value="s.id" />
          </el-select>
          <el-button type="primary" :disabled="!stallId" @click="openBindForSelectedStall" style="margin-left: 8px">绑定新设备</el-button>
        </div>
        <div class="table-card">
          <el-table :data="stallRows" v-loading="stallLoading" border>
            <el-table-column prop="platformDeviceId" label="设备 ID" width="200" />
            <el-table-column prop="deviceName" label="设备名称" min-width="200" />
            <el-table-column prop="energyType" label="能源类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="energyColor(row.energyType)">{{ enumLabel(row.energyType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="commAddress" label="通讯地址" width="140" />
            <el-table-column prop="serialNumber" label="序列号" width="140" />
            <el-table-column prop="boundBy" label="绑定人" width="100" />
            <el-table-column prop="effectiveFrom" label="生效时间" width="180"><template #default="{row}">{{ formatDateTime(row.effectiveFrom) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-popconfirm title="确认解绑？" @confirm="onUnbind(row)">
                  <template #reference><el-button size="small" link type="danger">解绑</el-button></template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 2. 设备搜索 -->
      <el-tab-pane label="设备搜索" name="search">
        <div class="toolbar">
          <el-input v-model="q.keyword" placeholder="按设备名/通讯地址/序列号/设备 ID 模糊" style="width: 320px" clearable @keyup.enter="doSearch" />
          <el-select v-model="q.energyType" placeholder="能源类型" clearable style="width: 140px; margin-left: 8px">
            <el-option label="电力 ELECTRIC" value="ELECTRIC" />
            <el-option label="给水 WATER" value="WATER" />
            <el-option label="燃气 GAS" value="GAS" />
            <el-option label="其它 OTHER" value="OTHER" />
          </el-select>
          <el-select v-model="q.bound" placeholder="绑定状态" clearable style="width: 140px; margin-left: 8px">
            <el-option label="已绑定" :value="true" />
            <el-option label="未绑定" :value="false" />
          </el-select>
          <el-button type="primary" @click="doSearch" style="margin-left: 8px">搜索</el-button>
        </div>
        <div class="table-card">
          <el-table :data="searchRows" v-loading="searchLoading" border>
            <el-table-column prop="platformDeviceId" label="设备 ID" width="200" />
            <el-table-column prop="deviceName" label="设备名称" min-width="200" />
            <el-table-column prop="energyType" label="能源" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="energyColor(row.energyType)">{{ enumLabel(row.energyType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="commAddress" label="通讯地址" width="140" />
            <el-table-column prop="serialNumber" label="序列号" width="140" />
            <el-table-column prop="gatewayName" label="网关" width="140" />
            <el-table-column prop="channelName" label="通道名称" width="150"><template #default="{ row }">{{ row.channelName || '-' }}</template></el-table-column>
            <el-table-column prop="productName" label="产品名称" min-width="160"><template #default="{ row }">{{ row.productName || '-' }}</template></el-table-column>
            <el-table-column prop="commStatus" label="通讯" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.commStatus === 1 ? 'success' : 'danger'">{{ row.commStatus === 1 ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link @click="showHistory(row)">历史</el-button>
                <el-button size="small" link type="primary" @click="quickBind(row)">绑定到铺位</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; display: flex; justify-content: flex-end"
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="doSearch"
            @size-change="doSearch"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 绑定弹窗 -->
    <el-dialog v-model="bindDialog" title="绑定设备到铺位" width="520px">
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="选择铺位">
          <el-select v-model="bindForm.stallId" filterable placeholder="选铺位" style="width: 100%" :disabled="bindForm._lockStall">
            <el-option v-for="s in stalls" :key="s.id" :label="`${s.stallCode} - ${s.stallName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备 ID">
          <el-input v-model="bindForm.platformDeviceId" :disabled="!!bindForm._presetDevice" />
        </el-form-item>
        <el-form-item v-if="bindForm._presetDevice">
          <span class="muted">{{ bindForm._presetDevice.deviceName }} ｜ {{ enumLabel(bindForm._presetDevice.energyType) }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="onBind">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 历史弹窗 -->
    <el-dialog v-model="historyDialog" title="设备铺位绑定历史" width="680px">
      <el-table :data="historyRows" border>
        <el-table-column prop="stallCode" label="铺位编码" width="120" />
        <el-table-column prop="stallName" label="铺位名称" min-width="160" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ enumLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveFrom" label="生效起" width="180"><template #default="{row}">{{ formatDateTime(row.effectiveFrom) }}</template></el-table-column>
        <el-table-column prop="effectiveTo" label="生效止" width="180"><template #default="{row}">{{ formatDateTime(row.effectiveTo) }}</template></el-table-column>
        <el-table-column prop="boundBy" label="绑定人" width="100" />
        <el-table-column prop="unboundBy" label="解绑人" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { stallApi, stallDeviceApi, deviceApi } from '@/api/merchant'
import { enumLabel, formatDateTime } from '@/utils/format'

const tab = ref('byStall')

// ---- byStall tab ----------------------------------------------------------
const stalls = ref<any[]>([])
const stallId = ref<number | null>(null)
const stallRows = ref<any[]>([])
const stallLoading = ref(false)

// ---- search tab -----------------------------------------------------------
const q = reactive<{ keyword: string; energyType: string; bound: boolean | '' }>({ keyword: '', energyType: '', bound: '' })
const searchRows = ref<any[]>([])
const searchLoading = ref(false)
const total = ref(0)
const page = reactive({ current: 1, size: 20 })

// ---- dialogs --------------------------------------------------------------
const bindDialog = ref(false)
const binding = ref(false)
const bindForm = reactive<{ stallId: number; platformDeviceId: string; remark: string; _presetDevice: any | null; _lockStall: boolean }>({
  stallId: 0,
  platformDeviceId: '',
  remark: '',
  _presetDevice: null,
  _lockStall: false
})
const historyDialog = ref(false)
const historyRows = ref<any[]>([])

// ---- actions --------------------------------------------------------------
async function loadStalls() {
  const r: any = await stallApi.page({ pageNum: 1, pageSize: 500 })
  stalls.value = (r.records || []).filter((s: any) => s.status !== 'DECOMMISSIONED')
}

async function loadStallDevices() {
  if (!stallId.value) { stallRows.value = []; return }
  stallLoading.value = true
  try {
    const r: any = await stallDeviceApi.listByStall(stallId.value)
    stallRows.value = r || []
  } finally { stallLoading.value = false }
}

async function doSearch() {
  searchLoading.value = true
  try {
    const r: any = await deviceApi.search({
      keyword: q.keyword,
      energyType: q.energyType,
      bound: q.bound === '' ? undefined : q.bound,
      pageNum: page.current,
      pageSize: page.size
    })
    searchRows.value = r.records || []
    total.value = Number(r.total) || 0
  } finally { searchLoading.value = false }
}

async function onUnbind(row: any) {
  await stallDeviceApi.unbind(row.stallId, row.platformDeviceId)
  ElMessage.success('已解绑')
  loadStallDevices()
}

function quickBind(row: any) {
  Object.assign(bindForm, { stallId: stallId.value || 0, platformDeviceId: row.platformDeviceId, remark: '', _presetDevice: row, _lockStall: false })
  bindDialog.value = true
}

function openBindForSelectedStall() {
  if (!stallId.value) return
  Object.assign(bindForm, { stallId: stallId.value, platformDeviceId: '', remark: '', _presetDevice: null, _lockStall: true })
  bindDialog.value = true
}

async function onBind() {
  if (!bindForm.stallId || !bindForm.platformDeviceId) {
    ElMessage.warning('请选择铺位并确认设备 ID')
    return
  }
  binding.value = true
  try {
    const targetStallId = bindForm.stallId
    await stallDeviceApi.bind(bindForm.stallId, { platformDeviceId: bindForm.platformDeviceId, remark: bindForm.remark })
    ElMessage.success('已绑定')
    bindDialog.value = false
    Object.assign(bindForm, { stallId: 0, platformDeviceId: '', remark: '', _presetDevice: null, _lockStall: false })
    if (tab.value === 'byStall') {
      stallId.value = targetStallId
      await Promise.all([loadStalls(), loadStallDevices()])
    } else {
      doSearch()
    }
  } finally { binding.value = false }
}

async function showHistory(row: any) {
  const r: any = await stallDeviceApi.historyByDevice(row.platformDeviceId)
  historyRows.value = r || []
  historyDialog.value = true
}

function energyColor(t?: string) {
  if (t === 'ELECTRIC') return 'warning'
  if (t === 'WATER') return 'primary'
  if (t === 'GAS') return 'success'
  return 'info'
}

onMounted(async () => {
  await loadStalls()
  loadStallDevices()
})
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; }
</style>
