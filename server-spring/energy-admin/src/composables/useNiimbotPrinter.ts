/**
 * 精臣 B1 标签机网页打印 composable
 * 支持 WebUSB (Chrome/Edge 桌面) 和 WebBluetooth (手机/蓝牙版)
 * 依赖 libniimai npm 包
 */
import { ref, reactive, computed } from 'vue'
import {
  createPrinter,
  supportsUsb,
  supportsBluetooth,
  friendlyError,
  fitToPrinthead,
  rotateCanvas,
  type PrinterService,
  type ConnectionKind,
  type PrinterStatus,
} from 'libniimai'

export type PrinterConnection = 'usb' | 'bluetooth' | null

export interface PrintableSpec {
  widthMm: number
  heightMm: number
  direction?: 'landscape' | 'portrait'
  labelType?: string
}

/** B1 常用规格预设 */
export const NIIMBOT_B1_PRESETS: Record<string, PrintableSpec> = {
  '50x30': { widthMm: 50, heightMm: 30, direction: 'landscape', labelType: 'NIIMBOT_B1_LABEL_50x30' },
  '50x70': { widthMm: 50, heightMm: 70, direction: 'landscape', labelType: 'NIIMBOT_B1_LABEL_50x70' },
}

const printer = ref<PrinterService | null>(null)
const connection = ref<PrinterConnection>(null)
const connecting = ref(false)
const status = reactive({
  connected: false,
  message: '',
  deviceName: '',
})

function refreshStatus() {
  if (!printer.value) {
    status.connected = false
    status.deviceName = ''
    return
  }
  const s: PrinterStatus = printer.value.getStatus()
  status.connected = s.state !== 'disconnected'
  status.deviceName = s.deviceName || ''
}

/** 返回当前浏览器可用的传输方式 */
export function availableKinds(): PrinterConnection[] {
  const arr: PrinterConnection[] = []
  if (typeof navigator !== 'undefined' && 'usb' in navigator) {
    try { if (supportsUsb()) arr.push('usb') } catch {}
  }
  if (typeof navigator !== 'undefined' && 'bluetooth' in navigator) {
    try { if (supportsBluetooth()) arr.push('bluetooth') } catch {}
  }
  return arr
}

/** 浏览器是否能调精臣 B1 */
export function canPrintWithNiimbot() {
  return availableKinds().length > 0
}

/** 连接打印机 */
export async function connectPrinter(kind: ConnectionKind): Promise<boolean> {
  if (connecting.value) return false
  connecting.value = true
  status.message = '正在连接打印机...'
  try {
    if (!printer.value) printer.value = createPrinter()
    await printer.value.connect(kind)
    connection.value = kind as PrinterConnection
    refreshStatus()
    status.message = `已连接：${status.deviceName || kind.toUpperCase()}`
    return true
  } catch (err: any) {
    status.connected = false
    status.deviceName = ''
    connection.value = null
    status.message = friendlyError(err) || ('连接失败：' + (err?.message || String(err)))
    return false
  } finally {
    connecting.value = false
  }
}

/** 断开连接 */
export async function disconnectPrinter() {
  try {
    if (printer.value) await printer.value.disconnect()
  } catch {}
  refreshStatus()
  if (!status.connected) {
    printer.value = null
    connection.value = null
    status.message = '已断开'
  }
}

/**
 * 把一个绘制好的 canvas 画布送到精臣 B1 打印
 */
export async function printCanvas(canvas: HTMLCanvasElement, spec: PrintableSpec): Promise<boolean> {
  if (!printer.value || !status.connected) {
    status.message = '请先连接打印机'
    return false
  }
  try {
    let src = fitToPrinthead(canvas, printer.value.getStatus().profile!)
    if (spec.direction === 'portrait') {
      src = rotateCanvas(src, 90)
    }
    status.message = '发送打印中...'
    await printer.value.print(src, { density: 1, copies: 1 })
    status.message = '打印任务已发送 ✓'
    return true
  } catch (err: any) {
    status.message = friendlyError(err) || ('打印失败：' + (err?.message || String(err)))
    return false
  }
}

/** 快捷打印：传入已选 preset 名称 + canvas */
export async function printWithPreset(canvas: HTMLCanvasElement, presetKey: keyof typeof NIIMBOT_B1_PRESETS) {
  const spec = NIIMBOT_B1_PRESETS[presetKey]
  return printCanvas(canvas, spec)
}

export function useNiimbotPrinter() {
  return {
    printer,
    connection,
    connecting,
    status,
    availableKinds: computed(() => availableKinds()),
    canPrintWithNiimbot,
    connectPrinter,
    disconnectPrinter,
    printCanvas,
    printWithPreset,
  }
}
