<template>
  <div class="m-page">
    <div class="m-head">
      <div class="m-title">{{ dept?.name || '营运巡场填报' }}</div>
      <div class="m-sub" v-if="dept">
        {{ date }} · {{ dept.formType === 'FOOD' ? '餐饮检查表单' : '零售检查表单' }}
        <template v-if="dept.floors?.length"> · 负责{{ dept.floors.map((f:any)=>f.floorName).join('、') }}</template>
      </div>
    </div>

    <div v-loading="loading" class="m-body">
      <template v-if="dept">
        <div class="card reporter">
          <span class="rp-label">填报人</span>
          <el-input v-model="reporter" placeholder="请输入您的姓名" maxlength="20" />
        </div>

        <div v-for="t in tasks" :key="t.id" class="card task">
          <div class="t-line1">
            <span class="t-name">{{ t.slotName }}</span>
            <el-tag size="small" :type="statusTagType(t.status)" effect="plain">{{ statusLabel(t.status) }}</el-tag>
          </div>
          <div class="t-line2">
            <span>{{ fmtTime(t.planStartAt) }} ~ {{ fmtTime(t.planEndAt) }}</span>
            <span v-if="t.isHandover">未闭环问题 {{ t.handover?.unclosedCount ?? 0 }} 项</span>
            <span v-else>已检 {{ t.doneCount }}/{{ t.itemCount }}</span>
          </div>
          <div class="t-line3" v-if="t.assigneeName || t.submitterName">
            <span v-if="t.assigneeName">值班：{{ t.assigneeName }}</span>
            <span v-if="t.submitterName">填报：{{ t.submitterName }}</span>
          </div>
          <el-progress v-if="!t.isHandover && t.itemCount" :percentage="Math.round(t.doneCount * 100 / t.itemCount)" :stroke-width="6" :show-text="false" class="t-progress" />
          <el-button class="t-btn" :type="btnType(t)" :plain="t.status==='DONE'||t.status==='EXEMPT'" @click="openTask(t)">{{ btnLabel(t) }}</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="链接无效或已过期" />
    </div>

    <!-- 填报抽屉 -->
    <el-drawer v-model="filling" direction="btt" size="94%" :with-header="false">
      <div v-if="current" class="fill-page">
        <div class="f-head">
          <div>
            <div class="f-title">{{ current.slotName }}</div>
            <div class="f-sub">{{ fmtTime(current.plan_start_at) }} ~ {{ fmtTime(current.plan_end_at) }} · {{ readonly ? '已提交，仅供查看' : '请逐项检查并标记结果' }}</div>
          </div>
          <span class="f-close" @click="filling=false">×</span>
        </div>
        <div class="f-body">
          <div v-for="g in groups" :key="g.name" class="f-group">
            <div class="f-gname">{{ g.name }}<span class="f-gcount">{{ g.done }}/{{ g.items.length }}</span></div>
            <div v-for="item in g.items" :key="item.itemCode" class="f-item" :class="{ fail: stateOf(item).result==='FAIL' }">
              <div class="f-content">
                <div class="f-text">
                  {{ item.itemContent }}
                  <el-tag v-if="item.photoRequired" size="small" type="warning" effect="plain" class="f-photo-tag">必拍</el-tag>
                </div>
                <div v-if="!readonly && stateOf(item).result==='FAIL'" class="f-fail-info">
                  <div v-for="(f, i) in stateOf(item).failures" :key="i" class="f-fail-row">
                    <div class="f-fail-store">{{ f.store?.displayName || f.store?.merchantName || '未关联商户' }}<template v-if="f.store?.disabled">（停用）</template></div>
                    <div>{{ f.description }}</div>
                    <div v-if="f.uploaded" class="f-fail-store">已上传 {{ f.uploaded }} 张照片</div>
                  </div>
                  <div v-if="!stateOf(item).failures.length" class="f-fail-store">点击「不合格」登记异常商户</div>
                </div>
                <div v-else-if="readonly && item.result==='FAIL'" class="f-fail-info">
                  <div v-for="f in (item.failures || [])" :key="f.id" class="f-fail-row">
                    <div class="f-fail-store">{{ f.merchantName || '未关联商户' }}<template v-if="f.stallCode">（{{ f.stallCode }}）</template></div>
                    <div>{{ f.description }}</div>
                  </div>
                  <div v-if="!(item.failures || []).length && item.issueDescription" class="f-fail-row">
                    <div>{{ item.issueDescription }}</div>
                  </div>
                </div>
              </div>
              <div v-if="!readonly" class="f-btns">
                <span class="f-btn ok" :class="{ on: stateOf(item).result==='PASS' }" @click="setResult(item,'PASS')">合格</span>
                <span class="f-btn bad" :class="{ on: stateOf(item).result==='FAIL' }" @click="openFail(item)">不合格</span>
                <span class="f-btn na" :class="{ on: stateOf(item).result==='NA' }" @click="setResult(item,'NA')">不适用</span>
              </div>
              <div v-else class="f-btns">
                <el-tag v-if="item.result" size="small" :type="resultTagType(item.result)">{{ resultLabel(item.result) }}</el-tag>
                <span v-else class="f-none">未检</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!readonly" class="f-foot">
          <el-button size="large" :loading="saving" @click="save(false)">{{ uploading ? uploadText : '保存进度' }}</el-button>
          <el-button size="large" type="primary" :loading="saving" @click="submit">{{ uploading ? uploadText : '提交结果' }}</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 不合格登记弹窗：一项检查可登记多家商户异常 -->
    <el-dialog v-model="failDialog" title="异常商户登记" width="100%" top="0" class="fail-dialog">
      <div class="fd-item">{{ failItem?.itemContent }}</div>
      <div class="fd-hint">一家商户有问题就加一条；同一检查项发现多家商户异常时，点下方「添加商户异常」继续登记。</div>

      <div v-for="(f, idx) in failForm.failures" :key="idx" class="fd-card">
        <div class="fd-card-hd">
          <span>异常 {{ idx + 1 }}</span>
          <span v-if="failForm.failures.length > 1" class="fd-del" @click="removeFailure(idx)">删除</span>
        </div>
        <el-select v-model="f.store" filterable remote clearable :remote-method="searchStores"
          placeholder="搜索商户名称 / 商户编码 / 铺位号" value-key="displayName" class="fd-store" no-match-text="未找到商户，可只填问题描述">
          <el-option v-for="s in storeOptions" :key="(s.accountNumber || '') + '-' + (s.stallCode || '')"
            :label="s.displayName + (s.disabled ? '（停用）' : '')" :value="s">
            <div class="fd-opt">
              <span>{{ s.displayName }}<span v-if="s.disabled" class="fd-opt-stop">（停用）</span></span>
              <span class="fd-opt-sub">{{ [s.merchantCode, s.floorName].filter(Boolean).join(' · ') }}</span>
            </div>
          </el-option>
        </el-select>
        <el-input v-model="f.description" type="textarea" :rows="2" maxlength="300" show-word-limit placeholder="问题描述（必填）" class="fd-desc" />
        <div class="photos">
          <div v-for="(p, pidx) in f.photos" :key="pidx" class="photo">
            <img :src="p.preview" /><span class="remove" @click="removeFailPhoto(f, pidx)">×</span>
          </div>
          <label v-if="f.photos.length < 6" class="photo add">
            <input type="file" accept="image/*" multiple @change="onFailFile($event, f)" /><span>+ 拍照</span>
          </label>
        </div>
      </div>

      <el-button class="fd-add" plain type="primary" @click="addFailure">+ 添加商户异常</el-button>
      <div v-if="failItem?.photoRequired" class="fd-tip">该检查项要求上传现场照片</div>
      <template #footer>
        <el-button size="large" @click="failDialog=false">取消</el-button>
        <el-button size="large" type="primary" @click="confirmFail">确定</el-button>
      </template>
    </el-dialog>

    <!-- 交接确认抽屉 -->
    <el-drawer v-model="handovering" direction="btt" size="94%" :with-header="false">
      <div v-if="current" class="fill-page">
        <div class="f-head">
          <div>
            <div class="f-title">{{ current.slotName }}</div>
            <div class="f-sub">确认截至 14:30 的未闭环问题，移交给下一班继续跟进</div>
          </div>
          <span class="f-close" @click="handovering=false">×</span>
        </div>
        <div class="f-body">
          <div class="h-stats">
            <div class="h-stat"><b>{{ handover?.pass_count || 0 }}</b><span>本班合格项</span></div>
            <div class="h-stat"><b>{{ handover?.fail_count || 0 }}</b><span>本班不合格项</span></div>
            <div class="h-stat"><b class="h-danger">{{ handover?.unclosed_count || 0 }}</b><span>未闭环问题</span></div>
          </div>

          <div class="f-gname">移交问题清单</div>
          <div v-if="!handover?.issues?.length" class="h-empty">无未闭环问题</div>
          <div v-for="iss in handover?.issues || []" :key="iss.id" class="h-issue">
            <div class="h-line1">
              <span class="h-no">{{ iss.issueNo }}</span>
              <el-tag size="small" :type="issueStatusTag(iss.status)" effect="plain">{{ issueStatusLabel(iss.status) }}</el-tag>
            </div>
            <div class="h-desc">{{ iss.description }}</div>
            <div class="h-meta">{{ [iss.merchantName, iss.category].filter(Boolean).join(' · ') }}<template v-if="iss.dueAt"> · 时限 {{ fmtTime(iss.dueAt) }}</template></div>
          </div>

          <template v-if="handover?.confirmed">
            <div class="h-done">已由 {{ handover.confirmed_by }} 于 {{ fmt(handover.confirmed_at) }} 确认</div>
            <div v-if="handover.remark" class="h-remark">备注：{{ handover.remark }}</div>
          </template>
          <template v-else>
            <el-input v-model="handoverRemark" type="textarea" :rows="2" maxlength="200" placeholder="交接备注（选填）" class="h-remark-input" />
          </template>
        </div>
        <div v-if="!handover?.confirmed" class="f-foot">
          <el-button size="large" type="primary" style="width:100%" :loading="confirming" @click="confirmHandover">确认交接</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsPatrolPublicApi } from '@/api/opsPatrol'

const route = useRoute()
const token = String(route.query.token || '')
const loading = ref(false)
const dept = ref<any>(null)
const date = ref('')
const tasks = ref<any[]>([])
const reporter = ref(localStorage.getItem('ops-patrol-reporter') || '')

const filling = ref(false)
const handovering = ref(false)
const current = ref<any>(null)
const saving = ref(false)
const uploading = ref(false)
const uploadText = ref('')
const confirming = ref(false)
const handoverRemark = ref('')
const opening = ref(false)

// 以 itemCode 为键的填报状态
const states = reactive<Record<string, any>>({})

const readonly = computed(() => current.value && ['DONE', 'EXEMPT'].includes(current.value.status))
const handover = computed(() => current.value?.handover || null)

const groups = computed(() => {
  if (!current.value?.items) return []
  const map = new Map<string, any[]>()
  for (const item of current.value.items) {
    const g = item.groupName || '其他'
    if (!map.has(g)) map.set(g, [])
    map.get(g)!.push(item)
  }
  return [...map.entries()].map(([name, items]) => ({
    name,
    items,
    done: items.filter(i => stateOf(i).result).length
  }))
})

type Photo = { file: File; preview: string }
type FailureDraft = { store: any; description: string; photos: Photo[]; uploaded: number }

function storeOf(f: any) {
  if (!(f.accountNumber || f.stallCode || f.merchantName)) return null
  return {
    accountId: f.accountId ?? null,
    accountNumber: f.accountNumber || '',
    merchantCode: f.merchantCode || '',
    accountNameRaw: f.accountNameRaw || '',
    merchantName: f.merchantName || '',
    stallId: f.stallId ?? null,
    stallCode: f.stallCode || '',
    floorName: f.floorName || '',
    disabled: !!f.disabled,
    displayName: f.merchantName
      ? `${f.merchantName}${f.stallCode ? '（' + f.stallCode + '）' : ''}`
      : (f.stallCode || '')
  }
}

function initFailures(item: any): FailureDraft[] {
  if (Array.isArray(item.failures) && item.failures.length) {
    return item.failures.map((f: any) => ({
      store: storeOf(f),
      description: f.description || '',
      photos: [],
      uploaded: 0
    }))
  }
  if (item.issueDescription || item.merchantName || item.stallCode) {
    return [{ store: storeOf(item), description: item.issueDescription || '', photos: [], uploaded: 0 }]
  }
  return []
}

function stateOf(item: any) {
  if (!states[item.itemCode]) {
    states[item.itemCode] = { result: item.result || null, failures: initFailures(item) }
  }
  return states[item.itemCode]
}

async function load() {
  loading.value = true
  try {
    const data: any = await opsPatrolPublicApi.entry(token)
    dept.value = data?.dept || null
    date.value = data?.date || ''
    tasks.value = data?.tasks || []
  } catch { dept.value = null }
  finally { loading.value = false }
}

function statusLabel(st: string) {
  return ({ PENDING: '待填报', IN_PROGRESS: '填报中', DONE: '已完成', OVERDUE: '逾期未报', EXEMPT: '已豁免' } as Record<string, string>)[st] || st
}
function statusTagType(st: string) {
  return ({ PENDING: 'info', IN_PROGRESS: 'warning', DONE: 'success', OVERDUE: 'danger', EXEMPT: 'info' } as Record<string, any>)[st] || 'info'
}
function resultLabel(r: string) { return ({ PASS: '合格', FAIL: '不合格', NA: '不适用' } as Record<string, string>)[r] || r }
function resultTagType(r: string) { return ({ PASS: 'success', FAIL: 'danger', NA: 'info' } as Record<string, any>)[r] || 'info' }
function issueStatusLabel(st: string) {
  return ({ PENDING: '待跟进', RECTIFYING: '整改中', REVIEWING: '待复核', CLOSED: '已闭环' } as Record<string, string>)[st] || st
}
function issueStatusTag(st: string) {
  return ({ PENDING: 'danger', RECTIFYING: 'warning', REVIEWING: 'primary', CLOSED: 'success' } as Record<string, any>)[st] || 'info'
}
function fmtTime(v: string) { return v ? String(v).slice(11, 16) : '-' }
function fmt(v: string) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-' }
function btnLabel(t: any) {
  if (t.status === 'DONE') return '查看结果'
  if (t.status === 'EXEMPT') return '已豁免'
  if (t.isHandover) return t.handover?.confirmed ? '查看交接' : '交接确认'
  if (t.status === 'IN_PROGRESS') return '继续填报'
  if (t.status === 'OVERDUE') return '补录'
  return '去填报'
}
function btnType(t: any) { return (t.status === 'DONE' || t.status === 'EXEMPT') ? 'default' : 'primary' }

async function openTask(t: any) {
  if (!reporter.value.trim() && !['DONE', 'EXEMPT'].includes(t.status)) {
    ElMessage.warning('请先填写填报人姓名')
    return
  }
  if (opening.value) return
  opening.value = true
  try {
    const data: any = await opsPatrolPublicApi.task(token, t.id)
    current.value = data
    Object.keys(states).forEach(k => delete states[k])
    handoverRemark.value = ''
    if (Number(data.is_handover) === 1) handovering.value = true
    else filling.value = true
  } finally { opening.value = false }
}

// ================================================================ 检查项填报
function setResult(item: any, result: string) {
  stateOf(item).result = result
}

const failDialog = ref(false)
const failItem = ref<any>(null)
const failForm = reactive<{ failures: FailureDraft[] }>({ failures: [] })
const storeOptions = ref<any[]>([])

function emptyFailure(): FailureDraft {
  return { store: null, description: '', photos: [], uploaded: 0 }
}

function openFail(item: any) {
  failItem.value = item
  const s = stateOf(item)
  failForm.failures = s.failures.length
    ? s.failures.map((f: FailureDraft) => ({ store: f.store, description: f.description, photos: [...f.photos], uploaded: f.uploaded }))
    : [emptyFailure()]
  storeOptions.value = []
  failDialog.value = true
}
function addFailure() {
  if (failForm.failures.length >= 10) return ElMessage.warning('一项检查最多登记 10 家商户')
  failForm.failures.push(emptyFailure())
}
function removeFailure(index: number) {
  const f = failForm.failures[index]
  f?.photos.forEach((p: Photo) => URL.revokeObjectURL(p.preview))
  failForm.failures.splice(index, 1)
}
async function searchStores(kw: string) {
  if (!kw) { storeOptions.value = []; return }
  storeOptions.value = ((await opsPatrolPublicApi.stores(token, kw)) as any) || []
}
function confirmFail() {
  const list = failForm.failures.filter((f: FailureDraft) => f.description.trim() || f.store)
  if (!list.length) return ElMessage.warning('请填写问题描述')
  if (list.some((f: FailureDraft) => !f.description.trim())) return ElMessage.warning('每个异常商户都需填写问题描述')
  const s = stateOf(failItem.value)
  s.result = 'FAIL'
  s.failures = list.map((f: FailureDraft) => ({
    store: f.store || null,
    description: f.description.trim(),
    photos: [...f.photos],
    uploaded: f.uploaded || 0
  }))
  failDialog.value = false
}
async function compress(file: File): Promise<File> {
  // 手机原图普遍 2-8MB，巡检缺陷在 960px JPEG 0.65 画质下完全可辨，压到 ≤150KB 弱网也能几秒内传完
  if (file.size <= 150 * 1024) return file
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 960 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const blob: Blob | null = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.65))
  return blob ? new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' }) : file
}
async function onFailFile(event: Event, target: FailureDraft) {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files || [])) {
    if (target.photos.length >= 6) break
    if (!file.type.startsWith('image/')) continue
    if (file.size > 8 * 1024 * 1024) { ElMessage.warning(`${file.name} 超过 8MB`); continue }
    const compressed = await compress(file)
    target.photos.push({ file: compressed, preview: URL.createObjectURL(compressed) })
  }
  input.value = ''
}
function removeFailPhoto(target: FailureDraft, index: number) {
  URL.revokeObjectURL(target.photos[index].preview)
  target.photos.splice(index, 1)
}

function buildPayload() {
  const items = Object.entries(states)
    .filter(([, s]) => s.result)
    .map(([itemCode, s]) => {
      const row: any = { itemCode, result: s.result }
      if (s.result === 'FAIL') {
        row.failures = s.failures.map((f: FailureDraft) => ({
          accountId: f.store?.accountId ?? null,
          accountNumber: f.store?.accountNumber || '',
          merchantCode: f.store?.merchantCode || '',
          accountNameRaw: f.store?.accountNameRaw || '',
          merchantName: f.store?.merchantName || '',
          stallId: f.store?.stallId ?? null,
          stallCode: f.store?.stallCode || '',
          floorName: f.store?.floorName || '',
          description: f.description
        }))
      }
      return row
    })
  return { submitterName: reporter.value.trim(), items }
}

async function uploadOne(issueId: number, file: File, tries = 3): Promise<boolean> {
  for (let i = 0; i < tries; i++) {
    try {
      await opsPatrolPublicApi.uploadPhoto(token, issueId, file)
      return true
    } catch {
      if (i === tries - 1) return false
      await new Promise(r => setTimeout(r, 800 * (i + 1)))
    }
  }
  return false
}

async function uploadPendingPhotos(taskData: any) {
  const byCode: Record<string, any> = {}
  for (const it of taskData?.items || []) byCode[it.itemCode] = it

  // 摊平成「单张照片」任务，逐张上传；并发 2 兼顾速度与弱网稳定性
  type Job = { draft: FailureDraft, photo: Photo, issueId: number, ok: boolean }
  const jobs: Job[] = []
  for (const [code, s] of Object.entries(states)) {
    if (s.result !== 'FAIL' || !s.failures?.length) continue
    const serverFailures = byCode[code]?.failures || []
    for (let i = 0; i < s.failures.length; i++) {
      const draft: FailureDraft = s.failures[i]
      if (!draft.photos.length) continue
      const matched = draft.store?.accountNumber
        ? serverFailures.find((f: any) => f.accountNumber === draft.store.accountNumber)
        : serverFailures[i]
      const issueId = matched?.id
      if (!issueId) continue
      for (const photo of draft.photos) jobs.push({ draft, photo, issueId, ok: false })
    }
  }
  if (!jobs.length) return

  let finished = 0
  uploading.value = true
  uploadText.value = `照片上传 0/${jobs.length}`
  const queue = [...jobs]
  const worker = async () => {
    while (queue.length) {
      const job = queue.shift()!
      job.ok = await uploadOne(job.issueId, job.photo.file)
      finished++
      uploadText.value = `照片上传 ${finished}/${jobs.length}`
    }
  }
  try {
    await Promise.all([worker(), worker()])
  } finally {
    uploading.value = false
    uploadText.value = ''
  }

  const drafts = new Set<FailureDraft>()
  const failedByDraft = new Map<FailureDraft, Photo[]>()
  for (const job of jobs) {
    drafts.add(job.draft)
    if (job.ok) {
      job.draft.uploaded = (job.draft.uploaded || 0) + 1
      URL.revokeObjectURL(job.photo.preview)
    } else {
      const arr = failedByDraft.get(job.draft) || []
      arr.push(job.photo)
      failedByDraft.set(job.draft, arr)
    }
  }
  drafts.forEach(draft => { draft.photos = failedByDraft.get(draft) || [] })
  const failed = jobs.filter(j => !j.ok).length
  if (failed) ElMessage.warning(`${failed} 张照片上传失败，可再次保存重试`)
}

async function save(silent: boolean): Promise<boolean> {
  if (!reporter.value.trim()) { ElMessage.warning('请先填写填报人姓名'); return false }
  const payload = buildPayload()
  if (!payload.items.length) { if (!silent) ElMessage.warning('请先标记检查项结果'); return false }
  saving.value = true
  try {
    localStorage.setItem('ops-patrol-reporter', reporter.value.trim())
    const data: any = await opsPatrolPublicApi.saveItems(token, current.value.id, payload)
    current.value = data
    await uploadPendingPhotos(data)
    if (!silent) ElMessage.success('已保存')
    await load()
    return true
  } finally { saving.value = false }
}

async function submit() {
  if (!reporter.value.trim()) return ElMessage.warning('请先填写填报人姓名')
  const remaining = (current.value.items || []).filter((i: any) => !stateOf(i).result).length
  if (remaining > 0) {
    await ElMessageBox.confirm(`还有 ${remaining} 项未检查，提交后将记为「漏检」并结束任务。确认提交？`, '提交确认', {
      type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '再检查一下'
    })
  }
  saving.value = true
  try {
    localStorage.setItem('ops-patrol-reporter', reporter.value.trim())
    const payload = buildPayload()
    if (payload.items.length) {
      const data: any = await opsPatrolPublicApi.saveItems(token, current.value.id, payload)
      current.value = data
      await uploadPendingPhotos(data)
    }
    await opsPatrolPublicApi.submit(token, current.value.id, { submitterName: reporter.value.trim(), force: remaining > 0 })
    ElMessage.success('已提交')
    filling.value = false
    await load()
  } finally { saving.value = false }
}

async function confirmHandover() {
  if (!reporter.value.trim()) return ElMessage.warning('请先填写填报人姓名')
  confirming.value = true
  try {
    localStorage.setItem('ops-patrol-reporter', reporter.value.trim())
    await opsPatrolPublicApi.confirmHandover(token, current.value.id, { submitterName: reporter.value.trim(), remark: handoverRemark.value.trim() })
    ElMessage.success('交接已确认')
    handovering.value = false
    await load()
  } finally { confirming.value = false }
}

onMounted(load)
</script>

<style scoped>
.m-page{min-height:100vh;background:#f4f6f8;padding-bottom:24px}
.m-head{background:linear-gradient(135deg,#409eff,#2f6fd6);color:#fff;padding:18px 16px}
.m-title{font-size:19px;font-weight:600}
.m-sub{margin-top:6px;font-size:13px;opacity:.9}
.m-body{padding:12px}
.card{background:#fff;border-radius:10px;padding:14px;margin-bottom:12px}
.reporter{display:flex;align-items:center;gap:10px}
.rp-label{font-size:14px;color:#303133;font-weight:600;flex-shrink:0}
.task .t-line1{display:flex;justify-content:space-between;align-items:center}
.t-name{font-size:15px;font-weight:600}
.t-line2{display:flex;justify-content:space-between;font-size:13px;color:#909399;margin-top:6px}
.t-line3{display:flex;gap:14px;font-size:12px;color:#909399;margin-top:4px}
.t-progress{margin-top:8px}
.t-btn{width:100%;margin-top:10px}

.fill-page{display:flex;flex-direction:column;height:100%}
.f-head{display:flex;justify-content:space-between;align-items:flex-start;padding:14px 16px;border-bottom:1px solid #ebeef5;flex-shrink:0}
.f-title{font-size:17px;font-weight:600}
.f-sub{font-size:12px;color:#909399;margin-top:4px}
.f-close{font-size:24px;color:#909399;line-height:1;padding:0 4px;cursor:pointer}
.f-body{flex:1;overflow-y:auto;padding:12px 16px}
.f-group{margin-bottom:16px}
.f-gname{font-size:13px;font-weight:600;color:#409eff;margin-bottom:8px;display:flex;justify-content:space-between}
.f-gcount{color:#909399;font-weight:400}
.f-item{background:#fff;border:1px solid #ebeef5;border-radius:10px;padding:12px;margin-bottom:8px}
.f-item.fail{border-color:#f56c6c}
.f-content{font-size:14px;line-height:1.6;color:#303133}
.f-photo-tag{margin-left:6px}
.f-fail-info{margin-top:8px;background:#fef0f0;border-radius:6px;padding:8px 10px;font-size:13px;color:#c45656}
.f-fail-store{color:#909399;font-size:12px;margin-top:4px}
.f-btns{display:flex;gap:8px;margin-top:10px}
.f-btn{flex:1;text-align:center;padding:8px 0;border-radius:8px;font-size:13px;border:1px solid #dcdfe6;color:#606266;cursor:pointer;user-select:none}
.f-btn.ok.on{background:#f0f9eb;border-color:#67c23a;color:#529b2e;font-weight:600}
.f-btn.bad.on{background:#fef0f0;border-color:#f56c6c;color:#c45656;font-weight:600}
.f-btn.na.on{background:#f4f4f5;border-color:#909399;color:#606266;font-weight:600}
.f-none{font-size:12px;color:#c0c4cc}
.f-foot{display:flex;gap:10px;padding:12px 16px;border-top:1px solid #ebeef5;flex-shrink:0}
.f-foot .el-button{flex:1}

.fd-item{font-size:14px;font-weight:600;margin-bottom:10px;line-height:1.5}
.fd-store{width:100%;margin-top:10px}
.fd-opt-sub{color:#909399;font-size:12px;margin-left:8px}
.fd-tip{font-size:12px;color:#e6a23c;margin-top:8px}
.photos{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
.photo{position:relative;width:76px;height:76px;border-radius:8px;overflow:hidden;background:#f5f7fa}
.photo img{width:100%;height:100%;object-fit:cover}
.photo .remove{position:absolute;right:0;top:0;width:20px;height:20px;line-height:20px;text-align:center;background:rgba(0,0,0,.5);color:#fff;cursor:pointer}
.photo.add{display:flex;align-items:center;justify-content:center;border:1px dashed #c0c4cc;color:#909399;font-size:12px;cursor:pointer}
.photo.add input{display:none}

.h-stats{display:flex;gap:10px;margin-bottom:16px}
.h-stat{flex:1;background:#f5f7fa;border-radius:10px;padding:12px;text-align:center}
.h-stat b{display:block;font-size:20px}
.h-stat span{font-size:12px;color:#909399}
.h-danger{color:#f56c6c}
.h-empty{font-size:13px;color:#909399;padding:12px 0}
.h-issue{background:#fff;border:1px solid #ebeef5;border-radius:10px;padding:12px;margin-bottom:8px}
.h-line1{display:flex;justify-content:space-between;align-items:center}
.h-no{font-size:13px;font-weight:600}
.h-desc{font-size:14px;margin-top:6px;line-height:1.6}
.h-meta{font-size:12px;color:#909399;margin-top:6px}
.h-done{margin-top:14px;font-size:13px;color:#67c23a;text-align:center}
.h-remark{margin-top:8px;font-size:13px;color:#909399;text-align:center}
.h-remark-input{margin-top:14px}

/* ================= 移动端体验优化 ================= */
.f-content{font-size:15px}
.f-btns{gap:10px}
.f-btn{min-height:44px;display:flex;align-items:center;justify-content:center;font-size:15px;border-radius:10px}
.t-btn{height:40px}
.f-fail-row + .f-fail-row{margin-top:8px;padding-top:8px;border-top:1px dashed rgba(196,86,86,.3)}
.fd-hint{font-size:12px;color:#909399;line-height:1.6;margin-bottom:12px}
.fd-card{border:1px solid #ebeef5;border-radius:10px;padding:12px;margin-bottom:10px;background:#fafcff}
.fd-card-hd{display:flex;justify-content:space-between;align-items:center;font-size:14px;font-weight:600;margin-bottom:8px}
.fd-del{color:#f56c6c;font-weight:400;font-size:13px;cursor:pointer;padding:4px 6px}
.fd-desc{margin-top:8px}
.fd-add{width:100%;height:44px;margin-top:2px}
.fd-opt{display:flex;justify-content:space-between;gap:10px;align-items:center}
.fd-opt-stop{color:#f56c6c;margin-left:4px}
.fd-opt-sub{color:#909399;font-size:12px;flex-shrink:0}
.photos{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:10px}
.photo{width:100%;height:auto;aspect-ratio:1;border-radius:8px;min-height:0}
.photo.add{font-size:13px}
:deep(.el-input__inner),:deep(.el-textarea__inner){font-size:16px}
:deep(.fail-dialog){max-width:100%}
:deep(.fail-dialog .el-dialog__body){padding:12px 16px;max-height:calc(100vh - 150px);overflow-y:auto}
:deep(.fail-dialog .el-dialog__footer){padding:10px 16px}
@media (max-width:420px){
  .m-title{font-size:18px}
  .f-body{padding:10px 12px}
  .f-item{padding:10px}
}
</style>
