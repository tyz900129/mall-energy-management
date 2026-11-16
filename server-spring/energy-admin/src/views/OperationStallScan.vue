<template>
  <div class="scan-page" v-if="stall">
    <div class="card stall-card">
      <div class="stall-title">{{ stall.stallCode }} {{ stall.stallName }}</div>
      <div class="stall-meta">
        <span>业态：{{ stall.businessTypeLabel || stall.businessType }}</span>
        <span v-if="stall.floorCode">· {{ stall.floorCode }}</span>
      </div>
    </div>

    <!-- 仓库：显示周期巡检 + 联合巡检入口 -->
    <template v-if="isWarehouse">
      <div v-for="s in qrScenes" :key="s.scene" class="card action-card" @click="openQrInspection(s)">
        <div class="action-icon">{{ s.scene === 'JOINT' ? '🔍' : '📋' }}</div>
        <div class="action-body">
          <div class="action-title">{{ sceneLabel(s) }}</div>
          <div class="action-sub" v-if="s.scene === 'JOINT'">任务：{{ s.name || s.taskId }}（截止 {{ s.endAt }}）</div>
          <div class="action-sub" v-else-if="s.templateName">{{ s.templateName }}</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <div v-if="qrScenes.length === 0" class="card empty-card">当前登录角色无可用巡检表单</div>
    </template>

    <!-- 商户：联合巡检（如有） + 商户日清 / 物业清场 / 责任资料 -->
    <template v-else>
      <div v-if="!loggedIn" class="card action-card" @click="login">
        <div class="action-icon">🔐</div>
        <div class="action-body">
          <div class="action-title">登录后办理</div>
          <div class="action-sub">商户日清、物业清场检查等需登录后按岗位权限使用</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <template v-else>
        <div v-if="jointScene" class="card action-card joint" @click="openQrInspection(jointScene)">
          <div class="action-icon">🔍</div>
          <div class="action-body">
            <div class="action-title">参与联合巡检</div>
            <div class="action-sub">任务：{{ jointScene.name || jointScene.taskId }}（截止 {{ jointScene.endAt }}）</div>
          </div>
          <div class="action-arrow">›</div>
        </div>
        <div v-if="canClearance" class="card action-card" @click="openClearance">
          <div class="action-icon">🧹</div>
          <div class="action-body">
            <div class="action-title">商户日清</div>
            <div class="action-sub">商户每日营业结束后提交</div>
          </div>
          <div class="action-arrow">›</div>
        </div>
        <div v-if="canProperty" class="card action-card" @click="openProperty">
          <div class="action-icon">🏢</div>
          <div class="action-body">
            <div class="action-title">物业清场检查</div>
            <div class="action-sub">物业部对商户进行清场复核</div>
          </div>
          <div class="action-arrow">›</div>
        </div>
        <div v-if="canMaterial" class="card action-card" @click="openMerchant">
          <div class="action-icon">📄</div>
          <div class="action-body">
            <div class="action-title">责任资料</div>
            <div class="action-sub">商户资质/经营证照</div>
          </div>
          <div class="action-arrow">›</div>
        </div>
        <div v-if="canToday" class="card action-card" @click="openTodayClearance">
          <div class="action-icon">📊</div>
          <div class="action-body">
            <div class="action-title">查看当日清场记录</div>
            <div class="action-sub">商户当日日清提交情况</div>
          </div>
          <div class="action-arrow">›</div>
        </div>
        <div v-if="permReady && !anyAction" class="card empty-card">当前账号在此铺位暂无可办理事项</div>
      </template>
    </template>

    <div v-if="!stall" class="card empty-card">二维码无效或已过期</div>

    <el-dialog v-model="clearanceVisible" title="商户三关一闭日清" width="520px">
      <el-alert v-if="periodMessage" :title="periodMessage" :type="periodOpen ? 'info' : 'warning'" :closable="false"/>
      <operation-dynamic-form v-if="periodOpen" :fields="clearanceFields" v-model="clearanceAnswers" :qr-token="token" form-type="CLEARANCE"/>
      <template #footer>
        <el-button @click="clearanceVisible = false">取消</el-button>
        <el-button v-if="periodOpen" type="primary" :loading="saving" @click="submitClearance">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="propertyVisible" title="物业清场检查" width="620px" class="property-dialog">
      <el-descriptions :column="2" border class="property-info">
        <el-descriptions-item label="检查日期">{{ propertyForm.periodDate }}</el-descriptions-item>
        <el-descriptions-item label="铺位">{{ propertyForm.floorName || '' }} {{ propertyForm.stallCode }}</el-descriptions-item>
        <el-descriptions-item label="检查人">{{ propertyForm.inspectorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检查人电话">{{ propertyForm.inspectorPhone || '未维护' }}</el-descriptions-item>
        <el-descriptions-item label="商户代码">{{ propertyForm.merchantCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商户名称">{{ propertyForm.merchantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商户联系人">{{ propertyForm.merchantContactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商户电话">{{ propertyForm.merchantContactPhone || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top" class="fixed-form">
        <el-form-item label="清场检查" required>
          <el-radio-group v-model="propertyStatus">
            <el-radio-button value="NORMAL">正常</el-radio-button>
            <el-radio-button value="ABNORMAL">异常</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="propertyStatus === 'ABNORMAL'">
          <el-form-item label="违规问题" required>
            <el-select v-model="selectedViolationIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="请选择违规问题" style="width:100%">
              <el-option v-for="item in propertyForm.violationItems || []" :key="item.id" :value="item.id" :label="`${item.violationCategory}｜${item.violationContent}`"/>
            </el-select>
            <div v-for="item in selectedViolations" :key="item.id" class="violation-tip">
              <b>{{ item.violationCategory }}：</b>{{ item.violationContent }}<span v-if="item.incentiveMeasure">；激励措施：{{ item.incentiveMeasure }}</span>
            </div>
          </el-form-item>
          <el-form-item label="现场照片" required>
            <div class="photo-list">
              <div v-for="(photo, index) in propertyPhotos" :key="photo.id" class="photo-item">
                <img :src="photo.preview" alt="违规现场照片"/>
                <button type="button" @click="removePhoto(index)">×</button>
              </div>
              <label v-if="propertyPhotos.length < 9" class="photo-picker" :class="{ disabled: photoUploading }">
                <input type="file" accept="image/*" capture="environment" multiple @change="uploadPropertyPhotos"/>
                <span>{{ photoUploading ? `${photoProgress}%` : '拍照/上传' }}</span>
              </label>
            </div>
            <div class="photo-help">异常检查至少上传1张，最多9张；图片将自动压缩上传。</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="propertyVisible = false">取消</el-button>
        <el-button type="primary" :loading="propertySaving" @click="submitProperty">提交检查</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="merchantVisible" title="铺位责任资料" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="铺位">{{ merchantDetail.floorName || '' }} {{ merchantDetail.stallCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前商户">{{ merchantDetail.merchantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业态">{{ merchantDetail.businessTypeLabel || merchantDetail.businessType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任商管">{{ merchantDetail.managerName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ merchantDetail.managerPhone || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="merchantVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="todayVisible" title="商户当日清场记录" width="620px">
      <el-descriptions :column="2" border class="today-info">
        <el-descriptions-item label="铺位">{{ todayDetail.floorName || '' }} {{ todayDetail.stallCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商户">{{ todayDetail.merchantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日清日期">{{ todayDetail.periodDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时段">{{ todayDetail.scheduleText || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="todayDetail.record">
        <el-descriptions :column="2" border class="today-info today-record">
          <el-descriptions-item label="提交人">{{ todayDetail.record.submitterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(todayDetail.record.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="检查状态">
            <el-tag :type="todayDetail.record.status === 'NORMAL' ? 'success' : 'danger'">{{ todayDetail.record.status === 'NORMAL' ? '正常' : '异常' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="使用模板">{{ todayDetail.record.templateName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="异常说明" :span="2">{{ todayDetail.record.exceptionDescription || '无' }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="todayAnswerRows.length" :data="todayAnswerRows" border size="small" class="answer-table">
          <el-table-column prop="label" label="检查项目" min-width="180"/>
          <el-table-column prop="value" label="提交内容" min-width="160"/>
        </el-table>
      </template>
      <el-empty v-else :description="`${todayDetail.periodDate || '当日'}暂无商户日清记录`"/>
      <template #footer><el-button type="primary" @click="todayVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { operationMerchantApi, operationClearanceApi, operationTemplateApi, operationPropertyApi, operationFormImageApi } from '@/api/operation'
import { useUserStore } from '@/stores/user'
import OperationDynamicForm from '@/components/OperationDynamicForm.vue'
import { compressImage } from '@/utils/image'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const token = (route.params.token as string) || ''
const stall = ref<any>(null)
const scenes = ref<any[]>([])
const loading = ref(false)
const permReady = ref(false)

const loggedIn = computed(() => !!userStore.token)
const canClearance = computed(() => loggedIn.value && userStore.hasPermission('operation.clearance.submit'))
const canProperty = computed(() => loggedIn.value && userStore.hasPermission('operation.clearance.review'))
const canMaterial = computed(() => loggedIn.value && userStore.hasPermission('operation.merchant.view'))
const canToday = computed(() => loggedIn.value && userStore.hasPermission('operation.clearance.view'))
const anyAction = computed(() => canClearance.value || canProperty.value || canMaterial.value || canToday.value || !!jointScene.value)

const isWarehouse = computed(() => stall.value && stall.value.businessType === 'CK')
const qrScenes = computed(() => scenes.value.filter((s: any) => s.scene.startsWith('WAREHOUSE_') || s.scene === 'JOINT'))
const jointScene = computed(() => scenes.value.find((s: any) => s.scene === 'JOINT'))

const clearanceVisible = ref(false)
const periodOpen = ref(false)
const periodMessage = ref('')
const saving = ref(false)
const clearanceFields = ref<any[]>([])
const clearanceAnswers = ref<any>({})

const propertyVisible = ref(false)
const propertySaving = ref(false)
const propertyForm = ref<any>({})
const propertyStatus = ref<'NORMAL' | 'ABNORMAL'>('NORMAL')
const selectedViolationIds = ref<number[]>([])
const propertyPhotos = ref<any[]>([])
const photoUploading = ref(false)
const photoProgress = ref(0)

const merchantVisible = ref(false)
const merchantDetail = ref<any>({})

const todayVisible = ref(false)
const todayDetail = ref<any>({})

const selectedViolations = computed(() => (propertyForm.value.violationItems || []).filter((x: any) => selectedViolationIds.value.includes(x.id)))
const todayAnswerRows = computed(() => {
  const record = todayDetail.value.record
  if (!record) return []
  const answers = parseJson(record.answersJson, {})
  const fields = parseJson(record.templateFieldsJson, [])
  return fields.map((field: any) => ({ label: field.label || field.code, value: answerText(answers[field.code], field.type) }))
})

function sceneLabel(s: any) {
  if (!s) return ''
  return s.scene === 'WAREHOUSE_COMMERCIAL' ? '商管巡检'
    : s.scene === 'WAREHOUSE_PROPERTY' ? '物业巡检'
    : s.scene === 'JOINT' ? '联合巡检'
    : s.scene
}

function login() { router.push({ path: '/login', query: { redirect: route.fullPath } }) }

async function openClearance() {
  const [p, t]: any[] = await Promise.all([operationClearanceApi.period(), operationTemplateApi.stall(token)])
  periodOpen.value = !!p.open
  periodMessage.value = p.open ? `日清日期：${p.periodDate}；模板：${t.name}` : p.message
  clearanceFields.value = t.fields || []
  clearanceAnswers.value = defaults(clearanceFields.value)
  clearanceVisible.value = true
}

async function submitClearance() {
  saving.value = true
  try {
    const r: any = await operationClearanceApi.submit(token, { answers: clearanceAnswers.value })
    ElMessage.success(r.status === 'NORMAL' ? '日清提交成功' : '异常日清已提交')
    clearanceVisible.value = false
  } finally {
    saving.value = false
  }
}

async function openProperty() {
  propertyForm.value = await operationPropertyApi.form(token)
  propertyStatus.value = 'NORMAL'
  selectedViolationIds.value = []
  propertyPhotos.value.forEach(p => p.preview && URL.revokeObjectURL(p.preview))
  propertyPhotos.value = []
  propertyVisible.value = true
}

async function uploadPropertyPhotos(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  if (propertyPhotos.value.length + files.length > 9) return ElMessage.warning('每次检查最多上传9张照片')
  photoUploading.value = true
  try {
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      if (!file.type.startsWith('image/')) continue
      photoProgress.value = Math.round(i / files.length * 100)
      const compressed = await compressImage(file)
      const result: any = await operationFormImageApi.upload(token, 'PROPERTY', 'violationPhoto', compressed, (p: number) => photoProgress.value = Math.round((i + p / 100) / files.length * 100))
      propertyPhotos.value.push({ ...result, preview: URL.createObjectURL(compressed) })
    }
    ElMessage.success('照片上传成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '照片上传失败')
  } finally {
    photoUploading.value = false
    photoProgress.value = 0
  }
}

function removePhoto(index: number) {
  const [photo] = propertyPhotos.value.splice(index, 1)
  if (photo?.preview) URL.revokeObjectURL(photo.preview)
}

async function submitProperty() {
  if (propertyStatus.value === 'ABNORMAL' && !selectedViolationIds.value.length) return ElMessage.warning('请选择违规问题')
  if (propertyStatus.value === 'ABNORMAL' && !propertyPhotos.value.length) return ElMessage.warning('请上传现场照片')
  propertySaving.value = true
  try {
    const data = {
      status: propertyStatus.value,
      violationItemIds: propertyStatus.value === 'ABNORMAL' ? selectedViolationIds.value : [],
      photos: propertyStatus.value === 'ABNORMAL' ? propertyPhotos.value.map(({ preview, ...photo }) => photo) : []
    }
    const r: any = await operationPropertyApi.submit(token, data)
    ElMessage.success(r.status === 'NORMAL' ? '物业检查正常' : '物业检查异常已登记')
    propertyVisible.value = false
  } finally {
    propertySaving.value = false
  }
}

async function openMerchant() {
  merchantDetail.value = await operationMerchantApi.detail(token)
  merchantVisible.value = true
}

async function openTodayClearance() {
  todayDetail.value = await operationClearanceApi.today(token)
  todayVisible.value = true
}

function defaults(fs: any[]) {
  return Object.fromEntries(fs.map(f => [f.code, f.type === 'BOOLEAN' ? (f.expected ?? true) : f.type === 'NUMBER' ? null : '']))
}

function parseJson(value: any, fallback: any) {
  try { return typeof value === 'string' ? JSON.parse(value) : value ?? fallback } catch { return fallback }
}

function answerText(value: any, type: string) {
  if (value === null || value === undefined || value === '') return '未填写'
  if (type === 'BOOLEAN' || typeof value === 'boolean') return value ? '是' : '否'
  if (type === 'IMAGE') return Array.isArray(value) ? `${value.length}张图片` : '已上传图片'
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function openQrInspection(s: any) {
  // 跳转扫码表单 H5（同页面渲染，通过参数传递 template + scene）
  window.location.href = `/warehouse/inspect?token=${token}&scene=${s.scene}&planId=${s.planId || ''}&taskId=${s.taskId || ''}`
}

onMounted(async () => {
  if (!token) return
  try {
    loading.value = true
    if (userStore.token) {
      try { await userStore.restoreSession() } catch { /* 登录态失效按未登录处理 */ }
    }
    permReady.value = true
    const res: any = await http.get(`/api/v1/qr-inspection/stalls/${token}/entry`)
    stall.value = res
    scenes.value = res?.scenes || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.scan-page { padding: 16px; max-width: 480px; margin: 0 auto; }
.card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.stall-title { font-size: 17px; font-weight: 600; color: #1f2937; }
.stall-meta  { font-size: 13px; color: #6b7280; margin-top: 4px; }
.action-card { display: flex; align-items: center; gap: 12px; cursor: pointer; transition: background .15s; }
.action-card:active { background: #f3f4f6; }
.action-icon { font-size: 22px; width: 36px; text-align: center; }
.action-body { flex: 1; }
.action-title { font-size: 15px; font-weight: 600; color: #111827; }
.action-sub { font-size: 12px; color: #9ca3af; margin-top: 2px; }
.action-arrow { color: #d1d5db; font-size: 20px; }
.empty-card { text-align: center; color: #9ca3af; font-size: 14px; }
.fixed-form { margin-top: 18px; }
.violation-tip { width: 100%; margin-top: 8px; padding: 8px 10px; background: #fff6f6; color: #606266; border-radius: 5px; font-size: 13px; line-height: 1.55; }
.photo-list { display: flex; flex-wrap: wrap; gap: 10px; }
.photo-item, .photo-picker { width: 92px; height: 92px; border-radius: 7px; overflow: hidden; position: relative; }
.photo-item img { width: 100%; height: 100%; object-fit: cover; }
.photo-item button { position: absolute; right: 3px; top: 3px; border: 0; border-radius: 50%; background: #0009; color: #fff; width: 24px; height: 24px; font-size: 18px; }
.photo-picker { display: flex; align-items: center; justify-content: center; border: 1px dashed #409eff; color: #409eff; background: #f3f8ff; cursor: pointer; }
.photo-picker input { display: none; }
.photo-picker.disabled { pointer-events: none; opacity: .65; }
.photo-help { margin-top: 8px; color: #909399; font-size: 12px; }
.today-record { margin-top: 14px; }
.answer-table { margin-top: 14px; }
@media (max-width: 480px) {
  :deep(.el-dialog) { width: calc(100% - 20px) !important; margin-top: 2vh !important; }
  :deep(.property-info .el-descriptions__body .el-descriptions__table),
  :deep(.today-info .el-descriptions__body .el-descriptions__table) { table-layout: fixed; }
  :deep(.property-info .el-descriptions__cell),
  :deep(.today-info .el-descriptions__cell) { display: block; width: 100% !important; }
  .photo-item, .photo-picker { width: 82px; height: 82px; }
}
</style>
