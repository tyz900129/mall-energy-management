<template>
  <div class="qr-batch-export">
    <div class="page-header">
      <h2>二维码批量导出</h2>
      <div class="header-actions">
        <el-select v-model="printPreset" size="default" style="width:180px">
          <el-option label="精臣 B1 · 50×30mm 横向" value="50x30" />
          <el-option label="精臣 B1 · 50×70mm 横向" value="50x70" />
        </el-select>
        <el-button :disabled="!canNiimbot" @click="openNiimbotDialog">
          {{ niimbotConnected ? '✅ 已连打印机' : '🔌 连接 B1 打印机' }}
        </el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <!-- ===== 铺位二维码 tab ===== -->
      <el-tab-pane label="铺位二维码" name="stall">
        <div class="search-bar">
          <el-input v-model="stallKeyword" placeholder="输入铺位编码/名称/楼层" clearable style="width:260px" @keyup.enter="loadStalls" />
          <el-button type="primary" @click="()=>loadStalls()">查询</el-button>
          <el-button @click="loadStalls(true)">重置</el-button>
          <div class="spacer"></div>
          <el-button type="success" :disabled="!stallSelected.length" @click="downloadStallZip">
            📦 批量下载 ZIP（{{ stallSelected.length }}）
          </el-button>
          <el-button @click="printAllStalls">🖨️ 全部 HTML 打印</el-button>
        </div>

        <el-table
          ref="stallTableRef"
          :data="stallRows"
          row-key="id"
          :select-on-indeterminate="true"
          border
          size="default"
          style="width:100%"
          @selection-change="stallSelected = $event"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="stallCode" label="铺位编码" width="110" />
          <el-table-column prop="stallName" label="铺位名称" show-overflow-tooltip />
          <el-table-column prop="floorName" label="楼层" width="90" />
          <el-table-column prop="merchantName" label="当前商户" show-overflow-tooltip />
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" link @click="downloadSingleStall(row)">下载</el-button>
              <el-button size="small" link :disabled="!niimbotConnected" @click="niimbotPrintStall(row)">🖨️ B1</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="stallTotal"
            :page-size="stallPageSize"
            :current-page="stallPageNum"
            @current-change="stallPageNum = $event; loadStalls()"
          />
        </div>
      </el-tab-pane>

      <!-- ===== 巡检设备二维码 tab ===== -->
      <el-tab-pane label="巡检设备二维码" name="equipment">
        <div class="search-bar">
          <el-input v-model="eqKeyword" placeholder="输入设备编码/名称/位置" clearable style="width:260px" @keyup.enter="loadEquipments" />
          <el-button type="primary" @click="()=>loadEquipments()">查询</el-button>
          <el-button @click="loadEquipments(true)">重置</el-button>
          <div class="spacer"></div>
          <el-button type="success" :disabled="!eqSelected.length" @click="downloadEqZip">
            📦 批量下载 ZIP（{{ eqSelected.length }}）
          </el-button>
          <el-button @click="printAllEquipments">🖨️ 全部 HTML 打印</el-button>
        </div>

        <el-table
          ref="eqTableRef"
          :data="eqRows"
          row-key="id"
          border
          size="default"
          style="width:100%"
          @selection-change="eqSelected = $event"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column prop="equipmentCode" label="设备编码" width="120" />
          <el-table-column prop="equipmentName" label="设备名称" show-overflow-tooltip />
          <el-table-column prop="installationLocation" label="安装位置" show-overflow-tooltip />
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" link @click="downloadSingleEq(row)">下载</el-button>
              <el-button size="small" link :disabled="!niimbotConnected" @click="niimbotPrintEq(row)">🖨️ B1</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 精臣 B1 连接弹窗 ===== -->
    <el-dialog v-model="niimbotDialog" title="连接精臣 B1 打印机" width="480px">
      <div v-if="!canNiimbot" class="warn">
        ⚠️ 当前浏览器不支持 WebUSB/WebBluetooth。请在 <b>Chrome 或 Edge 桌面版</b>上打开本页面，
        并确保网站走 HTTPS 或 localhost。
      </div>
      <div v-else>
        <div class="hint">
          精臣 B1 的 USB 线缆在 Windows 上会被识别成 CH340 虚拟串口，因此<strong>USB 线缆请选「Web Serial」</strong>，不要选 WebUSB（会报 claimInterface 错误）。蓝牙版选蓝牙。
        </div>
        <el-button type="primary" size="large" style="width:100%;margin:8px 0" :disabled="niimbotConnecting" @click="doConnect('serial')">
          {{ niimbotConnecting ? '连接中...' : '🔌 USB 线缆 · Web Serial（推荐）' }}
        </el-button>
        <el-button type="primary" size="large" plain style="width:100%;margin:8px 0" :disabled="niimbotConnecting" @click="doConnect('bluetooth')">
          {{ niimbotConnecting ? '连接中...' : '📶 蓝牙 连接' }}
        </el-button>
        <el-button size="small" plain style="width:100%;margin:8px 0;color:#909399" :disabled="niimbotConnecting" @click="doConnect('usb')">
          WebUSB（需 Zadig 换驱动，一般不用）
        </el-button>
        <el-button v-if="niimbotConnected" type="danger" plain size="small" @click="doDisconnect">断开连接</el-button>
        <div class="status-box">状态：{{ niimbotStatusMessage }}</div>
      </div>
    </el-dialog>

    <!-- ===== 隐藏的 HTML print 区域 ===== -->
    <div v-if="printStalls.length" ref="printArea" class="print-area">
      <template v-for="row in printStalls" :key="row.stallId">
        <div class="print-tag" :class="`preset-${printPreset}`">
          <div class="qr-box">
            <img :src="stallPrintPngs[row.stallId] || ''" alt="QR" />
          </div>
          <div class="text-box">
            <div class="line1">{{ row.stallCode }}</div>
            <div class="line2">{{ row.floorName }}</div>
          </div>
        </div>
      </template>
    </div>
    <div v-if="printEquipments.length" ref="printAreaEq" class="print-area">
      <template v-for="row in printEquipments" :key="row.id">
        <div class="print-tag" :class="`preset-${printPreset}`">
          <div class="qr-box">
            <img :src="eqPrintPngs[row.id] || ''" alt="QR" />
          </div>
          <div class="text-box">
            <div class="line1">{{ row.equipmentName }}</div>
            <div class="line2">{{ row.equipmentCode }}</div>
            <div class="line3">{{ row.installationLocation }}</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { operationMerchantApi } from '@/api/merchant'
import { inspectionEquipmentApi } from '@/api/inspectionEquipment'
import { useNiimbotPrinter, NIIMBOT_B1_PRESETS } from '@/composables/useNiimbotPrinter'
import http from '@/api/http'

const {
  printer, status, connecting: niimbotConnecting,
  availableKinds, canPrintWithNiimbot,
  connectPrinter, disconnectPrinter, printCanvas,
} = useNiimbotPrinter()

// -------- 通用 --------
const tab = ref<'stall' | 'equipment'>('stall')
const printPreset = ref<keyof typeof NIIMBOT_B1_PRESETS>('50x30')
const niimbotDialog = ref(false)
const canNiimbot = ref(false)
const niimbotConnected = computed(() => status.connected)
const niimbotStatusMessage = computed(() => status.message || '未连接')

// -------- 铺位 --------
const stallKeyword = ref('')
const stallRows = ref<any[]>([])
const stallTotal = ref(0)
const stallPageNum = ref(1)
const stallPageSize = 100
const stallSelected = ref<any[]>([])
const stallPrintPngs = reactive<Record<number, string>>({})

// -------- 设备 --------
const eqKeyword = ref('')
const eqRows = ref<any[]>([])
const eqTotal = ref(0)
const eqSelected = ref<any[]>([])
const eqPrintPngs = reactive<Record<number, string>>({})

async function loadStalls(reset = false) {
  if (reset) { stallKeyword.value = ''; stallPageNum.value = 1 }
  const res: any = await operationMerchantApi.page({
    keyword: stallKeyword.value || undefined,
    pageNum: stallPageNum.value,
    pageSize: stallPageSize,
  })
  // merchant-responsibilities 返回 records + stall info
  stallRows.value = res?.records || res?.data?.records || []
  stallTotal.value = res?.total ?? stallRows.value.length
}

async function loadEquipments(reset = false) {
  if (reset) eqKeyword.value = ''
  const res: any = await inspectionEquipmentApi.list({
    keyword: eqKeyword.value || undefined,
    pageSize: 2000,
  })
  eqRows.value = res?.records || []
  eqTotal.value = res?.total ?? eqRows.value.length
}

async function downloadBlob(apiFn: () => Promise<any>, suggestedName: string) {
  const blob: Blob = await apiFn()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = suggestedName
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadStallZip() {
  if (!stallSelected.value.length) return
  await downloadBlob(
    () => operationMerchantApi.batchQrZip(stallSelected.value.map((r: any) => r.stallId)),
    `铺位二维码_${Date.now()}.zip`
  )
  ElMessage.success('ZIP 已开始下载')
}

async function downloadEqZip() {
  if (!eqSelected.value.length) return
  await downloadBlob(
    () => inspectionEquipmentApi.batchQrZip(eqSelected.value.map((r: any) => r.id)),
    `巡检设备二维码_${Date.now()}.zip`
  )
  ElMessage.success('ZIP 已开始下载')
}

async function downloadSingleStall(row: any) {
  const blob = await http.get(`/api/v1/operations/merchant-responsibilities/${row.stallId}/qr`, { responseType: 'blob' })
  const url = URL.createObjectURL(blob as any)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.stallCode}_qr.png`
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadSingleEq(row: any) {
  const blob = await http.get(`/api/v1/inspection-equipment/public-qr/${row.qrToken}`, { responseType: 'blob' })
  const url = URL.createObjectURL(blob as any)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.equipmentCode}_qr.png`
  a.click()
  URL.revokeObjectURL(url)
}

// ---------- HTML 打印（批量全部）----------
const printStalls = ref<any[]>([])
const printEquipments = ref<any[]>([])

async function printAllStalls() {
  // 从已选中 + 当前页合并（简单做法：取当前页所有）
  const rows = stallSelected.value.length ? stallSelected.value : stallRows.value
  printStalls.value = rows
  printEquipments.value = []
  for (const r of rows) {
    try {
      const blob = await http.get(`/api/v1/operations/merchant-responsibilities/${r.stallId}/qr`, { responseType: 'blob' }) as any
      stallPrintPngs[r.stallId] = URL.createObjectURL(blob)
    } catch {}
  }
  setTimeout(() => window.print(), 150)
}

async function printAllEquipments() {
  const rows = eqSelected.value.length ? eqSelected.value : eqRows.value
  printEquipments.value = rows
  printStalls.value = []
  for (const r of rows) {
    try {
      const blob = await http.get(`/api/v1/inspection-equipment/public-qr/${r.qrToken}`, { responseType: 'blob' }) as any
      eqPrintPngs[r.id] = URL.createObjectURL(blob)
    } catch {}
  }
  setTimeout(() => window.print(), 150)
}

// ---------- 精臣 B1 打印（单张）----------
function openNiimbotDialog() { niimbotDialog.value = true }
async function doConnect(kind: 'serial' | 'usb' | 'bluetooth') {
  await connectPrinter(kind)
  if (status.connected) {
    ElMessage.success('打印机已连接')
    niimbotDialog.value = false
  } else {
    ElMessage.error(status.message || '连接失败')
  }
}
async function doDisconnect() { await disconnectPrinter(); ElMessage.info('已断开') }

function makeLabelCanvas(pngBlob: Blob, lines: string[], preset: keyof typeof NIIMBOT_B1_PRESETS): Promise<HTMLCanvasElement> {
  return new Promise((resolve, reject) => {
    const spec = NIIMBOT_B1_PRESETS[preset]
    // 标签纸假设 203 DPI → 1mm ≈ 8px
    const DPI = 203
    const mm2px = DPI / 25.4
    const c = document.createElement('canvas')
    const W = Math.round(spec.widthMm * mm2px)
    const H = Math.round(spec.heightMm * mm2px)
    c.width = W
    c.height = H
    const ctx = c.getContext('2d')!
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, W, H)

    const img = new Image()
    img.onload = () => {
      // QR 占左边约 60% 高度，右边是文字
      const qrSize = Math.round(H * 0.7)
      const qrX = Math.round(W * 0.05)
      const qrY = Math.round((H - qrSize) / 2)
      ctx.drawImage(img, qrX, qrY, qrSize, qrSize)

      ctx.fillStyle = '#000'
      const textX = qrX + qrSize + Math.round(W * 0.06)
      const lineHeight = Math.round(H / (lines.length + 1))
      ctx.textAlign = 'left'
      ctx.textBaseline = 'top'
      lines.forEach((line, i) => {
        ctx.font = `bold ${Math.round(lineHeight * 0.55)}px "Microsoft YaHei", sans-serif`
        // 超长截断
        let text = line
        while (ctx.measureText(text).width > W - textX && text.length > 0) text = text.slice(0, -1) + '…'
        ctx.fillText(text, textX, Math.round(lineHeight * (i + 0.3)))
      })
      resolve(c)
    }
    img.onerror = reject
    img.src = URL.createObjectURL(pngBlob)
  })
}

async function niimbotPrintStall(row: any) {
  try {
    const blob: any = await http.get(`/api/v1/operations/merchant-responsibilities/${row.stallId}/qr`, { responseType: 'blob' })
    const lines = [row.stallCode || '', row.floorName || '']
    const canvas = await makeLabelCanvas(blob as Blob, lines, printPreset.value)
    const ok = await printCanvas(canvas, NIIMBOT_B1_PRESETS[printPreset.value])
    if (ok) ElMessage.success('B1 打印已发送')
    else ElMessage.error(status.message || '打印失败')
  } catch (e: any) { ElMessage.error(e?.message || '打印失败') }
}

async function niimbotPrintEq(row: any) {
  try {
    const blob: any = await http.get(`/api/v1/inspection-equipment/public-qr/${row.qrToken}`, { responseType: 'blob' })
    const lines = [
      row.equipmentName || '',
      row.equipmentCode || '',
      row.installationLocation || '',
    ]
    const canvas = await makeLabelCanvas(blob as Blob, lines, printPreset.value)
    const ok = await printCanvas(canvas, NIIMBOT_B1_PRESETS[printPreset.value])
    if (ok) ElMessage.success('B1 打印已发送')
    else ElMessage.error(status.message || '打印失败')
  } catch (e: any) { ElMessage.error(e?.message || '打印失败') }
}

onMounted(async () => {
  canNiimbot.value = canPrintWithNiimbot()
  await loadStalls()
})

onBeforeUnmount(() => {
  Object.values(stallPrintPngs).forEach(u => URL.revokeObjectURL(u))
  Object.values(eqPrintPngs).forEach(u => URL.revokeObjectURL(u))
})
</script>

<style scoped>
.qr-batch-export { padding: 16px; }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px }
.page-header h2 { margin:0; font-size:18px }
.header-actions { display:flex; gap:8px; align-items:center }
.search-bar { display:flex; gap:8px; align-items:center; margin-bottom:12px; flex-wrap:wrap }
.search-bar .spacer { flex:1 }
.pager { display:flex; justify-content:flex-end; margin-top:12px }
.warn { background:#fef0f0; border:1px solid #fbc4c4; padding:12px; border-radius:6px; color:#c0392b }
.hint { color:#606266; font-size:13px; margin-bottom:8px }
.status-box { margin-top:12px; padding:8px; background:#f4f4f5; border-radius:4px; font-size:12px; color:#606266 }

/* ===== print 样式 ===== */
.print-area { display:none }
@media print {
  .page-header, .el-tabs__nav-wrap, .search-bar, .el-table, .pager, .el-dialog, .el-button, .header-actions { display:none !important }
  body * { visibility: hidden }
  .print-area, .print-area * { visibility: visible }
  .print-area { display:block; position:absolute; left:0; top:0; width:100% }
}
.print-tag { float:left; box-sizing:border-box; overflow:hidden; padding:4px; border:0.5px dashed #999; margin:2px;
  display:flex; align-items:center; gap:4px; font-family:"Microsoft YaHei", sans-serif }
.print-tag.preset-50x30 { width:50mm; height:30mm; }
.print-tag.preset-50x70 { width:50mm; height:70mm; }
.print-tag .qr-box img { width:100%; height:auto; max-width:100%; max-height:100% }
.print-tag .text-box { display:flex; flex-direction:column; gap:2px; flex:1; min-width:0 }
.print-tag .line1 { font-weight:bold; font-size:11px }
.print-tag .line2 { font-size:9px; color:#555 }
.print-tag .line3 { font-size:8px; color:#777 }
</style>
