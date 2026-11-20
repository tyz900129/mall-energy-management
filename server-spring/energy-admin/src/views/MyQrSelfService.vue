<template>
  <div class="qr-self-service">
    <div class="page-header">
      <h2>我的二维码</h2>
      <div class="header-actions">
        <el-select v-model="printPreset" style="width:180px">
          <el-option label="精臣 B1 · 50×30mm 横向" value="50x30" />
          <el-option label="精臣 B1 · 50×70mm 横向" value="50x70" />
        </el-select>
        <el-button :disabled="!canNiimbot" @click="niimbotDialog = true">
          {{ niimbotConnected ? '✅ B1 已连' : '🔌 连 B1 打印机' }}
        </el-button>
        <el-button type="success" :disabled="!stalls.length" @click="downloadMyZip">
          📦 一键下载全部 ZIP
        </el-button>
        <el-button plain type="warning" :disabled="!stalls.length" @click="downloadAllLabelImages">
          📱 下载全部标签图（供精臣 App 使用）
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="!stalls.length" class="empty">您当前没有负责的铺位</div>
    <div v-else class="grid">
      <div v-for="row in stalls" :key="row.stallId" class="stall-card">
        <div class="head">
          <div class="code">{{ row.stallCode }}</div>
          <div class="sub">{{ row.floorName }} · {{ row.stallName || '未命名' }}</div>
        </div>
        <div class="qr">
          <img :src="stallPngs[row.stallId] || ''" alt="QR" />
        </div>
        <div class="actions">
          <el-button size="small" @click="downloadSingle(row)">下载 PNG</el-button>
          <el-button size="small" :disabled="!niimbotConnected" @click="printB1(row)">🖨️ B1 打印</el-button>
        </div>
      </div>
    </div>

    <!-- B1 连接弹窗 -->
    <el-dialog v-model="niimbotDialog" title="连接精臣 B1 打印机" width="480px">
      <div v-if="!canNiimbot" class="warn">
        <div style="font-weight:600;margin-bottom:6px">🚫 浏览器无法直连 B1 打印机</div>
        <div style="line-height:1.6">{{ whyNoNiimbot || '当前浏览器不支持 Web Serial / WebUSB / Web Bluetooth' }}</div>
        <div style="margin-top:10px;padding:10px;background:#fff8e1;border-radius:6px;font-size:12px;color:#7a5a00">
          <b>解决方案：</b><br/>
          ① 电脑 Chrome/Edge + USB 线（最稳）<br/>
          ② 电脑 Chrome + 蓝牙<br/>
          ③ 安卓手机 Chrome + 蓝牙<br/>
          ④ <b>iOS Safari 用户</b>：点页面上的「📱 下载全部标签图」保存到相册 → 打开<b>精臣官方 App</b> → 选相册打印
        </div>
      </div>
      <div v-else>
        <div class="hint" style="font-size:12px;color:#606266;margin-bottom:8px">
          精臣 B1 的 USB 线缆在 Windows 上被识别成 CH340 串口，<strong>请选 Web Serial</strong>（不要选 WebUSB，会报 claimInterface 错误）。
        </div>
        <el-button type="primary" size="large" style="width:100%" :disabled="niimbotConnecting" @click="doConnect('serial')">
          {{ niimbotConnecting ? '连接中...' : '🔌 USB 线缆 · Web Serial（推荐）' }}
        </el-button>
        <el-button type="primary" size="large" plain style="width:100%;margin-top:8px" :disabled="niimbotConnecting" @click="doConnect('bluetooth')">
          {{ niimbotConnecting ? '连接中...' : '📶 蓝牙 连接' }}
        </el-button>
        <div class="status-box">状态：{{ niimbotStatusMessage }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { operationMerchantApi } from '@/api/merchant'
import { useNiimbotPrinter, NIIMBOT_B1_PRESETS, whyNotSupported, isIOSSafari } from '@/composables/useNiimbotPrinter'
import http from '@/api/http'

const {
  status, connecting: niimbotConnecting,
  canPrintWithNiimbot,
  connectPrinter, disconnectPrinter, printCanvas,
} = useNiimbotPrinter()

const loading = ref(true)
const stalls = ref<any[]>([])
const stallPngs = reactive<Record<number, string>>({})
const printPreset = ref<keyof typeof NIIMBOT_B1_PRESETS>('50x30')
const niimbotDialog = ref(false)
const canNiimbot = ref(false)
const niimbotConnected = computed(() => status.connected)
const niimbotStatusMessage = computed(() => status.message || '未连接')
const whyNoNiimbot = computed(() => whyNotSupported())

async function load() {
  try {
    const res: any = await operationMerchantApi.page({ pageNum: 1, pageSize: 2000 })
    stalls.value = res?.records || []
    // 预取单张 QR png URL 用于展示
    for (const s of stalls.value) {
      try {
        const blob: any = await http.get(`/api/v1/operations/merchant-responsibilities/${s.stallId}/qr`, { responseType: 'blob' })
        stallPngs[s.stallId] = URL.createObjectURL(blob)
      } catch {}
    }
  } finally { loading.value = false }
}

async function downloadMyZip() {
  const blob: any = await operationMerchantApi.myStallsQrZip()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `我的铺位二维码_${Date.now()}.zip`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('ZIP 已开始下载')
}

/** 降级：把我负责的所有铺位生成精臣标签 PNG 逐张下载，供 iOS Safari 保存相册后在精臣 App 打印 */
async function downloadAllLabelImages() {
  if (!stalls.value.length) return
  ElMessage.info(`开始生成 ${stalls.value.length} 张标签图，请稍候...（iOS Safari 请确保允许下载）`)
  let ok = 0, fail = 0
  for (const s of stalls.value) {
    try {
      const pngBlob: any = await http.get(`/api/v1/operations/merchant-responsibilities/${s.stallId}/qr`, { responseType: 'blob' })
      const canvas = await makeLabelCanvas(pngBlob as Blob, [s.stallCode || '', s.floorName || ''], printPreset.value)
      await new Promise<void>((resolve) => {
        canvas.toBlob((b) => {
          if (!b) { fail++; return resolve() }
          const url = URL.createObjectURL(b)
          const a = document.createElement('a')
          a.href = url
          a.download = `铺位_${s.stallCode || s.stallId}_${printPreset.value}mm.png`
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          setTimeout(() => URL.revokeObjectURL(url), 500)
          ok++
          resolve()
        }, 'image/png')
      })
    } catch { fail++ }
  }
  if (fail === 0) ElMessage.success(`全部 ${ok} 张标签图已下载`)
  else ElMessage.warning(`完成 ${ok} 张，失败 ${fail} 张`)
}

async function downloadSingle(row: any) {
  const blob: any = await http.get(`/api/v1/operations/merchant-responsibilities/${row.stallId}/qr`, { responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.stallCode}_qr.png`
  a.click()
  URL.revokeObjectURL(url)
}

async function doConnect(kind: 'serial' | 'usb' | 'bluetooth') {
  await connectPrinter(kind)
  if (status.connected) { ElMessage.success('已连接'); niimbotDialog.value = false }
  else ElMessage.error(status.message || '连接失败')
}
async function doDisconnect() { await disconnectPrinter(); ElMessage.info('已断开') }

function makeLabelCanvas(pngBlob: Blob, lines: string[], preset: keyof typeof NIIMBOT_B1_PRESETS): Promise<HTMLCanvasElement> {
  return new Promise((resolve, reject) => {
    const spec = NIIMBOT_B1_PRESETS[preset]
    const DPI = 203
    const mm2px = DPI / 25.4
    const c = document.createElement('canvas')
    c.width = Math.round(spec.widthMm * mm2px)
    c.height = Math.round(spec.heightMm * mm2px)
    const ctx = c.getContext('2d')!
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, c.width, c.height)
    const img = new Image()
    img.onload = () => {
      const H = c.height, W = c.width
      const qrSize = Math.round(H * 0.7)
      const qrX = Math.round(W * 0.05), qrY = Math.round((H - qrSize) / 2)
      ctx.drawImage(img, qrX, qrY, qrSize, qrSize)
      ctx.fillStyle = '#000'
      const textX = qrX + qrSize + Math.round(W * 0.06)
      const lineHeight = Math.round(H / (lines.length + 1))
      lines.forEach((line, i) => {
        ctx.font = `bold ${Math.round(lineHeight * 0.55)}px "Microsoft YaHei", sans-serif`
        let t = line
        while (ctx.measureText(t).width > W - textX && t.length > 0) t = t.slice(0, -1) + '…'
        ctx.fillText(t, textX, Math.round(lineHeight * (i + 0.3)))
      })
      resolve(c)
    }
    img.onerror = reject
    img.src = URL.createObjectURL(pngBlob)
  })
}

async function printB1(row: any) {
  try {
    const blob: any = await http.get(`/api/v1/operations/merchant-responsibilities/${row.stallId}/qr`, { responseType: 'blob' })
    const lines = [row.stallCode || '', row.floorName || '']
    const canvas = await makeLabelCanvas(blob as Blob, lines, printPreset.value)
    const ok = await printCanvas(canvas, NIIMBOT_B1_PRESETS[printPreset.value])
    if (ok) ElMessage.success('B1 打印已发送')
    else ElMessage.error(status.message || '打印失败')
  } catch (e: any) { ElMessage.error(e?.message || '打印失败') }
}

onMounted(() => { canNiimbot.value = canPrintWithNiimbot(); load() })
onBeforeUnmount(() => { Object.values(stallPngs).forEach(u => URL.revokeObjectURL(u)) })
</script>

<style scoped>
.qr-self-service { padding: 16px }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px }
.page-header h2 { margin:0; font-size:18px }
.header-actions { display:flex; gap:8px; align-items:center; flex-wrap:wrap }
.loading, .empty { padding:40px; text-align:center; color:#909399 }
.grid { display:grid; gap:16px; grid-template-columns:repeat(auto-fill, minmax(220px, 1fr)) }
.stall-card { background:#fff; border:1px solid #ebeef5; border-radius:8px; padding:12px; text-align:center; display:flex; flex-direction:column; gap:8px }
.stall-card .head .code { font-size:15px; font-weight:600; color:#303133 }
.stall-card .head .sub { font-size:12px; color:#909399 }
.stall-card .qr img { width:120px; height:120px; margin:0 auto }
.stall-card .actions { display:flex; gap:4px; justify-content:center }
.warn { background:#fef0f0; border:1px solid #fbc4c4; padding:12px; border-radius:6px; color:#c0392b }
.status-box { margin-top:12px; padding:8px; background:#f4f4f5; border-radius:4px; font-size:12px; color:#606266 }
</style>
