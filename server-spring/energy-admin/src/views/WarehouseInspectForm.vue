<template>
  <div class="inspect-page">
    <!-- 选择铺位 -->
    <div v-if="!stall" class="picker">
      <div class="page-head">
        <div class="title">{{ ctx?.sceneLabel || '巡检' }}</div>
        <div class="sub" v-if="ctx">
          部门：{{ ctx.deptName || ctx.deptCode || '-' }}
          <span v-if="ctx.planName"> · {{ ctx.planName }}</span>
          <span v-if="ctx.taskName"> · {{ ctx.taskName }}</span>
        </div>
      </div>

      <div class="search-card">
        <div class="search-row">
          <input v-model="keyword" placeholder="搜索铺位编号 / 名称 / 楼层" @input="onKeyword" />
          <button class="search-btn" @click="doSearch">搜索</button>
        </div>
        <div class="scan-row">
          <button class="scan-btn" @click="openScan">扫码巡检</button>
          <input ref="scanInput" type="file" accept="image/*" capture="environment" style="display:none" @change="onScanFile" />
          <span class="scan-hint">或直接用手机相机扫描铺位二维码</span>
        </div>
        <div class="tip">商管仅能搜索本处负责楼层的商户/仓库，物业可搜索全部。</div>
      </div>

      <div v-if="searching" class="list-empty">搜索中…</div>
      <div v-else-if="results.length" class="results">
        <div v-for="r in results" :key="r.id" class="result" @click="selectStall(r)">
          <div class="r-title">{{ r.stallCode }} {{ r.stallName }}</div>
          <div class="r-sub">{{ r.floorName || r.floorCode || '-' }} · {{ r.businessTypeLabel }}</div>
        </div>
      </div>
      <div v-else-if="keyword" class="list-empty">没有找到铺位</div>
      <div v-else class="list-empty">输入关键字搜索铺位，或扫描铺位二维码</div>
    </div>

    <!-- 填写表单 -->
    <div v-else>
      <div class="stall-card">
        <div class="stall-title">{{ stall.stallCode }} {{ stall.stallName }}</div>
        <div class="stall-meta">
          <span>{{ stall.businessTypeLabel || stall.businessType || '' }}</span>
          <span v-if="stall.floorName || stall.floorCode">· {{ stall.floorName || stall.floorCode }}</span>
          <span v-if="ctx?.sceneLabel">· {{ ctx.sceneLabel }}</span>
        </div>
        <div class="back-row">
          <button class="back-btn" @click="reset">重新选择铺位</button>
        </div>
      </div>

      <form @submit.prevent="submit" class="form">
        <div v-for="(f, idx) in fields" :key="f.code" class="field">
          <div class="field-head">
            <span class="idx">{{ idx + 1 }}</span>
            <span class="label">{{ f.label }}</span>
            <span v-if="f.required" class="req">*</span>
            <span v-if="f.category" class="cat">{{ f.category }}</span>
            <span v-if="f.responsibleDeptCode" class="dept">→ {{ deptLabel(f.responsibleDeptCode) }}</span>
          </div>

          <div v-if="f.controlType === 'NORMAL_ABNORMAL'" class="abnormal-row">
            <label>
              <input type="radio" :name="'q_' + f.code" :value="'NORMAL'" v-model="answers[f.code]" />
              <span class="rb ok">正常</span>
            </label>
            <label>
              <input type="radio" :name="'q_' + f.code" :value="'ABNORMAL'" v-model="answers[f.code]" />
              <span class="rb bad">异常</span>
            </label>
          </div>

          <div v-else-if="f.controlType === 'BOOLEAN'" class="abnormal-row">
            <label><input type="radio" :name="'q_' + f.code" :value="'true'" v-model="answers[f.code]" /><span class="rb ok">是</span></label>
            <label><input type="radio" :name="'q_' + f.code" :value="'false'" v-model="answers[f.code]" /><span class="rb bad">否</span></label>
          </div>

          <div v-else-if="f.controlType === 'SELECT'">
            <select v-model="answers[f.code]">
              <option value="">请选择</option>
              <option v-for="opt in parseOpts(f.options)" :key="opt" :value="opt">{{ opt }}</option>
            </select>
          </div>

          <div v-else-if="f.controlType === 'MULTISELECT'" class="multi-row">
            <label v-for="opt in parseOpts(f.options)" :key="opt" class="chk">
              <input type="checkbox" :value="opt" @change="toggleMulti(f.code, opt, $event)" />
              <span>{{ opt }}</span>
            </label>
          </div>

          <div v-else-if="f.controlType === 'NUMBER'">
            <input type="number" step="any" v-model="answers[f.code]" placeholder="请输入数字" />
          </div>

          <div v-else>
            <textarea v-model="answers[f.code]" rows="2" placeholder="请输入" />
          </div>

          <div v-if="shouldPhoto(f)" class="photo-row">
            <input type="file" accept="image/*" :data-code="f.code" @change="onPhoto" />
            <div class="photo-grid">
              <img v-for="u in photos[f.code] || []" :key="u" :src="u" />
            </div>
            <div v-if="(photos[f.code] || []).length === 0" class="photo-hint">请拍照</div>
          </div>
        </div>

        <button type="submit" class="submit" :disabled="submitting">
          {{ submitting ? '提交中…' : '提交巡检' }}
        </button>
      </form>
    </div>

    <div v-if="scanning" class="scan-overlay">
      <div class="scan-box">
        <video ref="scanVideo" class="scan-video" autoplay playsinline muted></video>
        <div class="scan-frame"></div>
        <div class="scan-tip">将铺位二维码放入框内</div>
        <button class="scan-cancel" @click="stopScan">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import jsQR from 'jsqr'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const token = (route.query.token as string) || ''
const queryScene = (route.query.scene as string) || ''
const planId = parseInt((route.query.planId as string) || '') || null
const taskId = parseInt((route.query.taskId as string) || '') || null

const ctx = ref<any>(null)
const stall = ref<any>(null)
const fields = ref<any[]>([])
const answers = ref<Record<string, any>>({})
const photos = ref<Record<string, string[]>>({})
const submitting = ref(false)
const keyword = ref('')
const results = ref<any[]>([])
const searching = ref(false)
const departments = ref<any[]>([])
const scanInput = ref<HTMLInputElement | null>(null)
const scanning = ref(false)
const scanVideo = ref<any>(null)
let scanStream: MediaStream | null = null
let scanRaf = 0
let scanCanvas: HTMLCanvasElement | null = null
let scanCtx: CanvasRenderingContext2D | null = null
let lastScanAt = 0
let searchTimer: number | undefined

const deptLabel = (code: string) => departments.value.find(d => d.code === code)?.name || code
const parseOpts = (o: any) => {
  if (Array.isArray(o)) return o
  try { const arr = JSON.parse(o || '[]'); return Array.isArray(arr) ? arr : [] }
  catch { return (o || '').split(',').map((s: string) => s.trim()).filter(Boolean) }
}

function toggleMulti(code: string, opt: string, e: any) {
  const list: string[] = answers.value[code] ? [...answers.value[code]] : []
  if (e.target.checked && !list.includes(opt)) list.push(opt)
  if (!e.target.checked) { const i = list.indexOf(opt); if (i >= 0) list.splice(i, 1) }
  answers.value[code] = list
}

function shouldPhoto(f: any) {
  const v = answers.value[f.code]
  if (v == null || v === '') return false
  const ct = f.controlType
  if (f.requirePhotoOnAbnormal && ct === 'NORMAL_ABNORMAL' && v === 'ABNORMAL') return true
  if (f.requirePhotoOnAbnormal && ct === 'BOOLEAN' && v !== String(f.normalValue)) return true
  if (f.requirePhotoOnAbnormal && ct !== 'NORMAL_ABNORMAL' && ct !== 'BOOLEAN') return true
  return false
}

async function onPhoto(e: any) {
  const f = e.target.files?.[0]; if (!f) return
  const code = e.target.getAttribute('data-code')
  const form = new FormData(); form.append('file', f)
  try {
    const res: any = await http.post('/api/v1/operation/forms/image', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    const arr = photos.value[code] ? [...photos.value[code]] : []
    arr.push(res)
    photos.value[code] = arr
  } catch { ElMessage.error('上传失败') }
}

function applyFields(fieldsJson: string) {
  try { fields.value = JSON.parse(fieldsJson || '[]') } catch { fields.value = [] }
  fields.value = fields.value
    .filter((f: any) => f.enabled !== false)
    .sort((a: any, b: any) => (a.sortOrder || 0) - (b.sortOrder || 0))
}

async function loadContext(scene?: string, pid?: number | null, tid?: number | null) {
  const res: any = await http.get('/api/v1/qr-inspection/context', {
    params: { scene: scene || undefined, planId: pid || undefined, taskId: tid || undefined }
  })
  ctx.value = res
  applyFields(res.fieldsJson)
  return res
}

async function loadDepartments() {
  try {
    const res: any = await http.get('/api/v1/qr-inspection/departments')
    departments.value = res || []
  } catch { departments.value = [] }
}

onMounted(async () => {
  await loadDepartments()
  try {
    if (token) {
      // 扫码进入：直接展示表单
      const entry: any = await http.get('/api/v1/qr-inspection/stalls/' + token + '/entry')
      const scenes: any[] = entry.scenes || []
      const chosen = scenes.find((s: any) => s.scene === queryScene) || scenes[0]
      if (!chosen) { ElMessage.error('当前账号在该铺位没有可用的巡检表单'); return }
      stall.value = { ...entry, id: entry.stallId }
      await loadContext(chosen.scene, chosen.planId || planId, chosen.taskId || taskId)
    } else {
      await loadContext(queryScene || undefined, planId, taskId)
      doSearch()
    }
  } catch (e: any) {
    if (e?.response?.status === 401) {
      ElMessage.warning('请先登录后再巡检')
      setTimeout(() => router.replace({ name: 'Login', query: { redirect: route.fullPath } }), 600)
      return
    }
    ElMessage.error(e?.response?.data?.message || '加载失败，请确认已登录且链接有效')
  }
})

function onKeyword() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = window.setTimeout(doSearch, 350)
}

async function doSearch() {
  if (!ctx.value) return
  searching.value = true
  try {
    const res: any = await http.get('/api/v1/qr-inspection/stalls/search', {
      params: { keyword: keyword.value.trim(), scene: ctx.value.scene }
    })
    results.value = res || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '搜索失败')
  } finally { searching.value = false }
}

function selectStall(r: any) {
  stall.value = { ...r, token: r.token }
  answers.value = {}
  photos.value = {}
}

function reset() {
  stall.value = null
  answers.value = {}
  photos.value = {}
  doSearch()
}

// ---- 扫码：优先摄像头实时识别（jsQR），不支持时回退拍照/相册识别 ----
async function openScan() {
  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.info('当前浏览器不支持实时扫码，请拍照上传二维码或直接用手机相机扫描')
    scanInput.value?.click()
    return
  }
  try {
    scanStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } },
      audio: false
    })
  } catch {
    ElMessage.info('无法打开摄像头（需 HTTPS 并授权），请拍照上传二维码或用手机相机扫描')
    scanInput.value?.click()
    return
  }
  scanning.value = true
  await nextTick()
  const video: HTMLVideoElement = scanVideo.value
  if (!video) { stopScan(); return }
  video.srcObject = scanStream
  try { await video.play() } catch {}
  scanCanvas = document.createElement('canvas')
  scanCtx = scanCanvas.getContext('2d') as CanvasRenderingContext2D | null
  lastScanAt = 0
  scanRaf = requestAnimationFrame(scanTick)
}

function scanTick() {
  if (!scanning.value) return
  const video: HTMLVideoElement = scanVideo.value
  if (!video || video.readyState < 2) { scanRaf = requestAnimationFrame(scanTick); return }
  const now = Date.now()
  if (now - lastScanAt > 150 && scanCanvas && scanCtx) {
    lastScanAt = now
    const w = video.videoWidth, h = video.videoHeight
    if (w && h) {
      if (scanCanvas.width !== w) { scanCanvas.width = w; scanCanvas.height = h }
      scanCtx.drawImage(video, 0, 0, w, h)
      const img = scanCtx.getImageData(0, 0, w, h)
      const code = jsQR(img.data, w, h, { inversionAttempts: 'attemptBoth' })
      if (code?.data) { handleDecoded(code.data); return }
    }
  }
  scanRaf = requestAnimationFrame(scanTick)
}

function stopScan() {
  scanning.value = false
  if (scanRaf) cancelAnimationFrame(scanRaf)
  scanRaf = 0
  scanStream?.getTracks().forEach(t => t.stop())
  scanStream = null
  if (scanVideo.value) scanVideo.value.srcObject = null
}

async function handleDecoded(raw: string) {
  stopScan()
  const tk = extractToken(raw)
  if (!tk) { ElMessage.error('二维码内容不是有效的铺位码'); return }
  await selectByToken(tk)
}

async function onScanFile(e: any) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const bitmap = await createImageBitmap(file)
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    const c = canvas.getContext('2d') as CanvasRenderingContext2D | null
    if (!c) { ElMessage.error('图片解析失败'); return }
    c.drawImage(bitmap, 0, 0)
    const img = c.getImageData(0, 0, bitmap.width, bitmap.height)
    const code = jsQR(img.data, bitmap.width, bitmap.height, { inversionAttempts: 'attemptBoth' })
    if (!code?.data) { ElMessage.error('未识别到二维码，请重新拍摄'); return }
    await handleDecoded(code.data)
  } catch {
    ElMessage.error('二维码识别失败，请重试')
  }
}

onBeforeUnmount(stopScan)

function extractToken(raw: string): string {
  const byPath = raw.match(/\/operation\/stall\/([A-Za-z0-9_-]+)/)
  if (byPath) return byPath[1]
  const byQuery = raw.match(/[?&]token=([A-Za-z0-9_-]+)/)
  if (byQuery) return byQuery[1]
  const plain = raw.trim()
  return /^[A-Za-z0-9_-]{16,}$/.test(plain) ? plain : ''
}

async function selectByToken(tk: string) {
  try {
    const entry: any = await http.get('/api/v1/qr-inspection/stalls/' + tk + '/entry')
    const scenes: any[] = entry.scenes || []
    if (!scenes.some((s: any) => s.scene === ctx.value?.scene)) {
      ElMessage.error('该铺位不在本次巡检范围内')
      return
    }
    stall.value = { ...entry, id: entry.stallId, token: tk }
    answers.value = {}
    photos.value = {}
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '二维码无效')
  }
}

async function submit() {
  if (!ctx.value || !stall.value) return
  for (const f of fields.value) {
    if (f.required && (answers.value[f.code] === null || answers.value[f.code] === '' ||
        (Array.isArray(answers.value[f.code]) && answers.value[f.code].length === 0))) {
      ElMessage.warning(`请填写：${f.label}`); return
    }
  }
  submitting.value = true
  try {
    let hasAbnormal = false
    for (const f of fields.value) {
      const v = answers.value[f.code]
      if (f.controlType === 'NORMAL_ABNORMAL' && v === 'ABNORMAL') hasAbnormal = true
      if (f.controlType === 'BOOLEAN' && f.normalValue != null && v !== String(f.normalValue)) hasAbnormal = true
    }
    const body: any = {
      stallId: stall.value.id,
      scene: ctx.value.scene,
      planId: ctx.value.planId || null,
      taskId: ctx.value.taskId || null,
      templateId: ctx.value.templateId,
      answersJson: JSON.stringify(answers.value),
      photosJson: JSON.stringify(photos.value),
      status: hasAbnormal ? 'ABNORMAL' : 'NORMAL'
    }
    await http.post('/api/v1/qr-inspection/submit', body)
    ElMessage.success('已提交，感谢！')
    setTimeout(() => router.replace('/'), 1200)
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '提交失败') }
  finally { submitting.value = false }
}
</script>

<style scoped>
.inspect-page { padding: 16px; max-width: 520px; margin: 0 auto; }
.page-head { margin-bottom: 12px; }
.page-head .title { font-size: 18px; font-weight: 700; color: #111827; }
.page-head .sub { margin-top: 4px; color: #6b7280; font-size: 13px; }
.search-card { background: #fff; border-radius: 12px; padding: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.search-row { display: flex; gap: 8px; }
.search-row input { flex: 1; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; font-size: 14px; }
.search-btn { padding: 0 16px; border: none; background: #111827; color: #fff; border-radius: 8px; font-size: 14px; }
.scan-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.scan-btn { padding: 8px 16px; border: 1px solid #111827; background: #fff; color: #111827; border-radius: 8px; font-size: 14px; }
.scan-hint { color: #9ca3af; font-size: 12px; }
.scan-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.75); z-index: 2000; display: flex; align-items: center; justify-content: center; }
.scan-box { position: relative; width: 92vw; max-width: 420px; }
.scan-video { width: 100%; border-radius: 12px; display: block; background: #000; }
.scan-frame { position: absolute; left: 50%; top: 50%; width: 220px; height: 220px; transform: translate(-50%,-50%); border: 2px solid #22c55e; border-radius: 12px; box-shadow: 0 0 0 9999px rgba(0,0,0,.35); pointer-events: none; }
.scan-tip { color: #fff; text-align: center; margin-top: 12px; font-size: 14px; }
.scan-cancel { display: block; margin: 12px auto 0; padding: 8px 24px; border-radius: 8px; border: 1px solid #fff; background: transparent; color: #fff; font-size: 14px; }
.tip { margin-top: 10px; color: #9ca3af; font-size: 12px; }
.results { margin-top: 12px; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.result { padding: 12px 14px; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
.result:last-child { border-bottom: none; }
.result:active { background: #f9fafb; }
.r-title { font-size: 14px; font-weight: 600; color: #111827; }
.r-sub { margin-top: 2px; font-size: 12px; color: #9ca3af; }
.list-empty { text-align: center; color: #9ca3af; padding: 32px 0; font-size: 14px; }
.stall-card { background:#fff; border-radius:12px; padding:14px; margin-bottom:12px; box-shadow:0 1px 3px rgba(0,0,0,.08); }
.stall-title { font-weight:600; font-size:16px; }
.stall-meta { color:#6b7280; font-size:13px; margin-top:4px; }
.stall-meta span { margin-right:8px; }
.back-row { margin-top: 8px; }
.back-btn { border: none; background: transparent; color: #2563eb; font-size: 13px; padding: 0; cursor: pointer; }
.form .field { background:#fff; border-radius:10px; padding:12px; margin-bottom:10px; box-shadow:0 1px 2px rgba(0,0,0,.04); }
.field-head { display:flex; align-items:center; gap:6px; margin-bottom:8px; }
.field-head .idx { background:#111827; color:#fff; font-size:12px; width:20px; height:20px; border-radius:50%; display:inline-flex; align-items:center; justify-content:center; }
.field-head .label { font-weight:600; flex:1; }
.field-head .req { color:#dc2626; }
.field-head .cat { color:#6b7280; font-size:12px; border:1px solid #e5e7eb; padding:1px 6px; border-radius:10px; }
.field-head .dept { color:#2563eb; font-size:12px; }
.abnormal-row label { margin-right:16px; }
.rb { padding:4px 10px; border-radius:14px; border:1px solid #d1d5db; font-size:13px; }
.rb.ok { color:#16a34a; border-color:#16a34a; }
.rb.bad { color:#dc2626; border-color:#dc2626; }
select, input[type=number], textarea { width:100%; border:1px solid #e5e7eb; border-radius:6px; padding:8px; font-size:14px; }
.multi-row { display:flex; flex-wrap:wrap; gap:8px; }
.chk label { display:inline-flex; gap:4px; }
.photo-row { margin-top:8px; }
.photo-grid { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; }
.photo-grid img { width:80px; height:80px; object-fit:cover; border-radius:6px; }
.photo-hint { color:#dc2626; font-size:12px; margin-top:4px; }
.submit { width:100%; padding:12px; background:#111827; color:#fff; border:none; border-radius:24px; font-size:15px; margin-top:16px; cursor:pointer; }
.submit:disabled { opacity:.6; }
</style>
