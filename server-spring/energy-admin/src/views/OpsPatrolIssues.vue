<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">营运问题</div>
        <div class="page-note">巡场不合格项自动生成问题单，各处室跟进整改，主管复核闭环。</div>
      </div>
      <div class="actions">
        <el-select v-model="query.deptId" placeholder="处室" clearable style="width:130px" @change="search">
          <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px" @change="search">
          <el-option label="待跟进" value="PENDING" />
          <el-option label="整改中" value="RECTIFYING" />
          <el-option label="待复核" value="REVIEWING" />
          <el-option label="已闭环" value="CLOSED" />
        </el-select>
        <el-select v-model="query.floor" placeholder="楼层" clearable filterable style="width:110px" @change="search">
          <el-option v-for="f in floors" :key="f.floorCode" :label="f.floorName" :value="f.floorName" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="商户/单号/描述" clearable style="width:180px" @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <el-table v-if="!isMobile" :data="rows" v-loading="loading" border @row-click="openDetail">
      <el-table-column prop="issueNo" label="单号" width="135" />
      <el-table-column prop="deptName" label="处室" width="95" />
      <el-table-column label="店铺" min-width="160">
        <template #default="{ row }">
          <div>{{ row.merchantName || '（未关联店铺）' }}</div>
          <div class="sub">{{ [row.stallCode, row.floorName].filter(Boolean).join(' · ') }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="90" />
      <el-table-column label="紧急" width="70">
        <template #default="{ row }"><el-tag v-if="row.urgency==='URGENT'" type="danger" size="small">紧急</el-tag><span v-else class="sub">一般</span></template>
      </el-table-column>
      <el-table-column prop="description" label="问题描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          <el-tag v-if="row.overdueFlag && row.status!=='CLOSED'" type="danger" size="small" effect="plain" style="margin-left:4px">逾期</el-tag>
          <el-tag v-if="row.repeatFlag" type="warning" size="small" effect="plain" style="margin-left:4px">复发</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="整改时限" width="150">
        <template #default="{ row }"><span :class="{ danger: row.overdueFlag && row.status!=='CLOSED' }">{{ fmt(row.dueAt) }}</span></template>
      </el-table-column>
      <el-table-column prop="ownerName" label="跟进人" width="90"><template #default="{row}">{{row.ownerName||'-'}}</template></el-table-column>
      <el-table-column label="上报" width="145">
        <template #default="{ row }">
          <div>{{ row.submitterName }}</div>
          <div class="sub">{{ fmt(row.submittedAt) }}</div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="isMobile" v-loading="loading" class="m-list">
      <div v-for="row in rows" :key="row.id" class="m-card" @click="openDetail(row)">
        <div class="m-card-hd">
          <span class="m-no">{{ row.issueNo }}</span>
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </div>
        <div class="m-store">
          {{ row.merchantName || '（未关联商户）' }}
          <span class="sub" v-if="row.stallCode || row.floorName">（{{ [row.stallCode, row.floorName].filter(Boolean).join(' · ') }}）</span>
        </div>
        <div class="m-desc">{{ row.description }}</div>
        <div class="m-meta">
          <span>{{ row.deptName }} · {{ row.category }}</span>
          <span :class="{ danger: row.overdueFlag && row.status !== 'CLOSED' }">{{ fmt(row.dueAt) }}</span>
        </div>
        <div class="m-tags" v-if="row.urgency === 'URGENT' || row.overdueFlag || row.repeatFlag">
          <el-tag v-if="row.urgency === 'URGENT'" type="danger" size="small" effect="plain">紧急</el-tag>
          <el-tag v-if="row.overdueFlag && row.status !== 'CLOSED'" type="danger" size="small" effect="plain">逾期</el-tag>
          <el-tag v-if="row.repeatFlag" type="warning" size="small" effect="plain">复发</el-tag>
        </div>
      </div>
      <el-empty v-if="!rows.length && !loading" description="暂无问题单" />
    </div>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="onPage" />

    <el-drawer v-model="drawer" :size="isMobile ? '100%' : '620px'" :title="detail ? `问题单 ${detail.issue_no}` : '问题详情'">
      <div v-loading="detailLoading">
        <div v-if="detail" class="detail">
          <div class="kv"><label>状态</label>
            <el-tag size="small" :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
            <el-tag v-if="detail.overdue_flag && detail.status!=='CLOSED'" type="danger" size="small" effect="plain">已逾期</el-tag>
            <el-tag v-if="detail.repeat_flag" type="warning" size="small" effect="plain">7日内复发</el-tag>
          </div>
          <div class="kv"><label>处室</label><span>{{ detail.deptName }}</span></div>
          <div class="kv"><label>店铺</label><span>{{ storeText(detail) }}</span></div>
          <div class="kv"><label>分类/紧急</label><span>{{ detail.category }} / {{ detail.urgency==='URGENT'?'紧急':'一般' }}</span></div>
          <div class="kv"><label>整改时限</label><span :class="{danger: detail.overdue_flag && detail.status!=='CLOSED'}">{{ fmt(detail.due_at) }}</span></div>
          <div class="kv"><label>上报人</label><span>{{ detail.submitter_name }} · {{ fmt(detail.submitted_at) }}</span></div>
          <div class="kv"><label>跟进人</label><span>{{ detail.owner_name || '未指定' }}</span></div>

          <div class="sec-title">问题描述</div>
          <p class="desc">{{ detail.description }}</p>
          <div class="photos" v-if="photoList.length">
            <el-image v-for="p in photoList" :key="p.id" :src="p.url" :preview-src-list="photoList.map((x:any)=>x.url)" fit="cover" class="photo" preview-teleported />
          </div>

          <template v-if="detail.rectified_note">
            <div class="sec-title">整改说明</div>
            <p class="desc">{{ detail.rectified_note }}</p>
            <div class="sub" style="margin-bottom:6px">{{ fmt(detail.rectified_at) }}</div>
          </template>
          <template v-if="detail.review_note">
            <div class="sec-title">复核意见</div>
            <p class="desc">{{ detail.review_note }}<span class="sub">（{{ detail.reviewed_by }} · {{ fmt(detail.reviewed_at) }}）</span></p>
          </template>

          <div class="sec-title">处理记录</div>
          <el-timeline>
            <el-timeline-item v-for="(l, i) in detail.logs" :key="i" :timestamp="fmt(l.createdAt)" placement="top">
              <b>{{ l.operator || '系统' }}</b> {{ actionLabel(l.action) }}<span v-if="l.note">：{{ l.note }}</span>
            </el-timeline-item>
          </el-timeline>

          <div v-if="canAssign && ['PENDING','RECTIFYING'].includes(detail.status)" class="ops">
            <div class="sec-title">指派跟进人</div>
            <div class="op-row">
              <el-input v-model="assignName" placeholder="跟进人姓名" style="width:200px" maxlength="20" />
              <el-button type="primary" plain :loading="acting" @click="assign">指派</el-button>
            </div>
            <div class="sec-title">提交整改</div>
            <el-input v-model="rectifyNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="整改措施与结果（必填）" />
            <div class="photos" style="margin:10px 0">
              <div v-for="(p, idx) in rectifyPhotos" :key="idx" class="photo">
                <img :src="p.preview" /><span class="remove" @click="removePhoto(idx)">×</span>
              </div>
              <label v-if="rectifyPhotos.length < 6" class="photo add">
                <input type="file" accept="image/*" multiple @change="onFileChange" /><span>+ 照片</span>
              </label>
            </div>
            <el-button type="primary" :loading="acting" @click="rectify">提交整改</el-button>
          </div>

          <div v-if="canReview && detail.status==='REVIEWING'" class="ops">
            <div class="sec-title">复核</div>
            <el-input v-model="reviewNote" type="textarea" :rows="2" maxlength="200" placeholder="复核意见（驳回时必填）" />
            <div class="op-row" style="margin-top:10px">
              <el-button type="success" :loading="acting" @click="review(true)">复核通过</el-button>
              <el-button type="danger" plain :loading="acting" @click="review(false)">驳回重整</el-button>
            </div>
          </div>

          <div v-if="isSuperAdmin" class="ops" style="margin-top:16px">
            <el-button type="danger" plain :loading="acting" @click="removeIssue">删除该问题单（测试记录）</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsPatrolApi } from '@/api/opsPatrol'
import http from '@/api/http'
import { useIsMobile } from '@/composables/useIsMobile'
import { useUserStore } from '@/stores/user'

const isMobile = useIsMobile()

const user = useUserStore()
const canAssign = computed(() => user.hasPermission('ops.patrol.assign'))
const canReview = computed(() => user.hasPermission('ops.patrol.review'))
const isSuperAdmin = computed(() => user.hasPermission('ops.patrol.assign'))

const depts = ref<any[]>([])
const floors = ref<any[]>([])
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<any>({ deptId: null, status: '', floor: '', keyword: '', page: 1, size: 20 })

const drawer = ref(false)
const detail = ref<any>(null)
const detailLoading = ref(false)
const photoList = ref<any[]>([])
const acting = ref(false)
const assignName = ref('')
const rectifyNote = ref('')
const rectifyPhotos = ref<{file: File, preview: string}[]>([])
const reviewNote = ref('')

async function load() {
  loading.value = true
  try {
    const data: any = await opsPatrolApi.issues({ ...query, deptId: query.deptId || undefined, status: query.status || undefined, floor: query.floor || undefined, keyword: query.keyword || undefined })
    rows.value = data?.rows || []
    total.value = data?.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; load() }
function onPage(p: number) { query.page = p; load() }

async function openDetail(row: any) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  photoList.value = []
  assignName.value = ''
  rectifyNote.value = ''
  reviewNote.value = ''
  clearPhotos()
  try {
    const data: any = await opsPatrolApi.issueDetail(row.id)
    detail.value = data
    assignName.value = data.owner_name || ''
    loadPhotos(data)
  } finally { detailLoading.value = false }
}
async function loadPhotos(d: any) {
  for (const a of d.attachments || []) {
    try {
      const blob: any = await http.get(`/api/v1/ops-patrol/issues/${d.id}/attachments/${a.id}`, { responseType: 'blob' })
      photoList.value.push({ id: a.id, url: URL.createObjectURL(blob) })
    } catch { /* 忽略单张失败 */ }
  }
}
function storeText(d: any) {
  if (!d.merchant_name) return '（未关联商户）'
  const code = d.merchant_code ? `（${d.merchant_code}）` : ''
  return [d.merchant_name + code, d.stall_code, d.floor_name].filter(Boolean).join(' · ')
}
function statusLabel(st: string) {
  return ({ PENDING: '待跟进', RECTIFYING: '整改中', REVIEWING: '待复核', CLOSED: '已闭环' } as Record<string, string>)[st] || st
}
function statusTagType(st: string) {
  return ({ PENDING: 'danger', RECTIFYING: 'warning', REVIEWING: 'primary', CLOSED: 'success' } as Record<string, any>)[st] || 'info'
}
function actionLabel(a: string) {
  return ({ CREATE: '创建问题', ASSIGN: '指派跟进', TRANSFER: '变更跟进人', RECTIFY: '提交整改', FEEDBACK_CLOSE: '整改闭环', REVIEW_PASS: '复核通过', REVIEW_REJECT: '复核驳回', OVERDUE_NOTICE: '逾期提醒' } as Record<string, string>)[a] || '状态变更'
}
function fmt(v: string) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-' }

async function assign() {
  if (!assignName.value.trim()) return ElMessage.warning('请填写跟进人姓名')
  acting.value = true
  try {
    const data: any = await opsPatrolApi.assignIssue(detail.value.id, { ownerName: assignName.value.trim() })
    detail.value = data
    ElMessage.success('已指派')
    await load()
  } finally { acting.value = false }
}

async function compress(file: File): Promise<File> {
  if (file.size <= 1.5 * 1024 * 1024) return file
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const blob: Blob | null = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.82))
  return blob ? new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' }) : file
}
async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files || [])) {
    if (rectifyPhotos.value.length >= 6) break
    if (!file.type.startsWith('image/')) continue
    if (file.size > 8 * 1024 * 1024) { ElMessage.warning(`${file.name} 超过 8MB`); continue }
    const compressed = await compress(file)
    rectifyPhotos.value.push({ file: compressed, preview: URL.createObjectURL(compressed) })
  }
  input.value = ''
}
function removePhoto(index: number) {
  URL.revokeObjectURL(rectifyPhotos.value[index].preview)
  rectifyPhotos.value.splice(index, 1)
}
function clearPhotos() {
  rectifyPhotos.value.forEach(p => URL.revokeObjectURL(p.preview))
  rectifyPhotos.value = []
}
async function rectify() {
  if (!rectifyNote.value.trim()) return ElMessage.warning('请填写整改说明')
  acting.value = true
  try {
    const data: any = await opsPatrolApi.rectifyIssue(detail.value.id, rectifyNote.value.trim(), rectifyPhotos.value.map(p => p.file))
    detail.value = data
    photoList.value = []
    loadPhotos(data)
    clearPhotos()
    rectifyNote.value = ''
    ElMessage.success('整改已提交，等待复核')
    await load()
  } finally { acting.value = false }
}
async function review(pass: boolean) {
  if (!pass && !reviewNote.value.trim()) return ElMessage.warning('驳回需填写原因')
  acting.value = true
  try {
    const data: any = await opsPatrolApi.reviewIssue(detail.value.id, { pass, note: reviewNote.value.trim() })
    detail.value = data
    reviewNote.value = ''
    ElMessage.success(pass ? '已闭环' : '已驳回')
    await load()
  } finally { acting.value = false }
}

async function removeIssue() {
  const no = detail.value?.issue_no
  try { await ElMessageBox.confirm(`确认彻底删除问题单 ${no}？照片与处理记录会一并删除，且不可恢复。`, '删除测试记录', { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' }) }
  catch { return }
  acting.value = true
  try {
    await opsPatrolApi.removeIssue(detail.value.id)
    ElMessage.success('已删除')
    drawer.value = false
    await load()
  } finally { acting.value = false }
}

onMounted(async () => {
  load()
  depts.value = (await opsPatrolApi.depts()) as any
  floors.value = (await opsPatrolApi.floorOptions()) as any
})
</script>

<style scoped>
.actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.sub{font-size:12px;color:#909399}
.danger{color:#f56c6c}
.pager{margin-top:14px;justify-content:flex-end}
.detail .kv{display:flex;gap:10px;padding:6px 0;font-size:13px;color:#606266;align-items:center}
.detail .kv label{width:70px;flex-shrink:0;color:#909399}
.sec-title{font-size:14px;font-weight:600;margin:16px 0 10px}
.desc{margin:0;font-size:14px;line-height:1.7;color:#303133;white-space:pre-wrap}
.photos{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
.photo{position:relative;width:80px;height:80px;border-radius:8px;overflow:hidden;background:#f5f7fa}
.photo img{width:100%;height:100%;object-fit:cover}
.photo .remove{position:absolute;right:0;top:0;width:20px;height:20px;line-height:20px;text-align:center;background:rgba(0,0,0,.5);color:#fff;cursor:pointer}
.photo.add{display:flex;align-items:center;justify-content:center;border:1px dashed #c0c4cc;color:#909399;font-size:12px;cursor:pointer}
.photo.add input{display:none}
.ops{border-top:1px dashed #ebeef5;margin-top:16px;padding-top:4px}
.op-row{display:flex;gap:10px;align-items:center}

/* 移动端卡片列表 */
.m-list{display:flex;flex-direction:column;gap:10px}
.m-card{background:#fff;border-radius:12px;padding:12px 14px;box-shadow:0 1px 3px rgba(0,0,0,.06);border:1px solid #ebeef5}
.m-card-hd{display:flex;justify-content:space-between;align-items:center;gap:8px}
.m-no{font-size:13px;color:#909399;font-family:ui-monospace,Menlo,monospace}
.m-store{font-size:16px;font-weight:600;color:#303133;margin:8px 0 4px;line-height:1.45}
.m-desc{font-size:14px;color:#606266;line-height:1.6;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
.m-meta{display:flex;justify-content:space-between;gap:8px;font-size:12px;color:#909399;margin-top:8px}
.m-tags{display:flex;gap:6px;margin-top:8px;flex-wrap:wrap}
@media (max-width:768px){
  .detail .kv{font-size:14px}
  .detail .kv label{width:64px}
  .photo{width:76px;height:76px}
  .op-row{flex-direction:column;align-items:stretch}
  .op-row > .el-input{width:100%!important}
}
</style>
