<template>
  <div class="stall-qr-card">
    <div class="info">
      <div class="code">{{ code }}</div>
      <div class="sub">{{ subtitle }}</div>
    </div>
    <div class="qr">
      <img :src="pngUrl" :alt="code" />
    </div>
    <div class="actions">
      <el-button size="small" @click="downloadSingle">下载 PNG</el-button>
      <el-button size="small" :disabled="!canNiimbot" @click="$emit('print', data)">
        🖨️ B1 打印
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

/**
 * 通用二维码卡片组件
 * - 自动拉取单张 QR PNG
 * - 支持下载 PNG
 * - 通过 @print 事件把数据交给父组件（父组件负责调 B1 打印）
 */
const props = defineProps<{
  /** 显示的编码（如 A-001） */
  code: string
  /** 副信息（如 "1F / 工程部"） */
  subtitle: string
  /** 渲染用 blob URL（由父组件通过 API 拿到 blob 后 createObjectURL 传入） */
  pngBlob: Blob | null
  /** 原始数据，@print 时回传 */
  data: any
}>()

defineEmits<{
  print: [data: any]
}>()

const pngUrl = ref<string>('')
const canNiimbot = ref(false)

function downloadSingle() {
  if (!props.pngBlob) return
  const url = URL.createObjectURL(props.pngBlob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${props.code}_qr.png`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  pngUrl.value = props.pngBlob ? URL.createObjectURL(props.pngBlob) : ''
  canNiimbot.value = typeof navigator !== 'undefined' && ('usb' in navigator || 'bluetooth' in navigator)
})

onBeforeUnmount(() => {
  if (pngUrl.value) URL.revokeObjectURL(pngUrl.value)
})
</script>

<style scoped>
.stall-qr-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  width: 180px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stall-qr-card .info .code { font-size: 14px; font-weight: 600; color: #303133; }
.stall-qr-card .info .sub { font-size: 12px; color: #909399; }
.stall-qr-card .qr img { width: 100px; height: 100px; margin: 0 auto; }
.stall-qr-card .actions { display: flex; gap: 4px; justify-content: center; }
</style>
