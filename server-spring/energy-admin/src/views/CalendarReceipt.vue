<template>
  <div class="receipt-page">
    <div class="head">
      <div class="head-title">{{ detail.planName || '任务回执' }}</div>
      <div class="head-sub">{{ detail.targetName || '' }}</div>
      <div class="head-meta">
        <span>{{ formatTime(detail.scheduledAt) }}</span>
        <span class="dot">·</span>
        <span>截止 {{ formatTime(detail.dueAt) }}</span>
        <el-tag v-if="detail.status" size="small" effect="dark" class="status-tag">{{ statusText }}</el-tag>
      </div>
      <div v-if="detail.expiresAt" class="head-expire">链接有效至 {{ formatTime(detail.expiresAt) }}</div>
    </div>

    <div v-loading="loading" class="body">
      <el-result v-if="error" icon="warning" :title="error" sub-title="请联系任务负责人重新获取回执链接" />

      <template v-else>
        <div v-if="!detail.submittable" class="card done-card">
          <el-icon class="done-icon"><CircleCheckFilled /></el-icon>
          <div>
            <div class="done-title">该任务已结束</div>
            <div class="done-sub">提交时间：{{ formatTime(detail.submittedAt) }}</div>
          </div>
        </div>

        <div v-if="fields.length" class="card">
          <div class="card-title">任务内容</div>
          <div v-for="field in fields" :key="field.key" class="form-row">
            <div class="label">
              {{ field.label }}
              <span v-if="field.required" class="required">*</span>
            </div>
            <div class="control">
              <el-select v-if="field.options.length" v-model="form[field.key]" :disabled="!detail.submittable" placeholder="请选择" style="width:100%">
                <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <el-radio-group v-else-if="field.boolean" v-model="form[field.key]" :disabled="!detail.submittable">
                <el-radio label="正常">正常</el-radio>
                <el-radio label="异常">异常</el-radio>
              </el-radio-group>
              <el-input v-else-if="field.multiline" v-model="form[field.key]" type="textarea" :rows="3" :disabled="!detail.submittable" placeholder="请填写" />
              <el-input v-else v-model="form[field.key]" :disabled="!detail.submittable" placeholder="请填写" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-title">
            现场照片
            <span class="counter">{{ photos.length }}/{{ detail.maxPhotos || 9 }}</span>
          </div>
          <div class="photo-grid">
            <div v-for="photo in photos" :key="photo.id" class="photo-item">
              <img :src="calendarPublicApi.photoUrl(token, photo.id)" alt="现场照片" />
            </div>
            <label v-if="detail.submittable && photos.length < (detail.maxPhotos || 9)" class="photo-add">
              <input type="file" accept="image/jpeg,image/png" multiple hidden @change="onPick" />
              <el-icon><Plus /></el-icon>
              <span>上传</span>
            </label>
          </div>
          <div class="hint">支持 JPEG/PNG，单张不超过 10MB，最多 {{ detail.maxPhotos || 9 }} 张</div>
        </div>

        <div class="card">
          <div class="card-title">问题登记（可选）</div>
          <div v-for="(issue, index) in issues" :key="index" class="issue-row">
            <el-input v-model="issue.description" type="textarea" :rows="2" placeholder="问题描述" :disabled="!detail.submittable" />
            <el-select v-model="issue.level" :disabled="!detail.submittable" style="width:110px">
              <el-option label="一般" value="WARNING" />
              <el-option label="严重" value="CRITICAL" />
            </el-select>
            <el-button text type="danger" :disabled="!detail.submittable" @click="issues.splice(index, 1)">删除</el-button>
          </div>
          <el-button v-if="detail.submittable" text type="primary" @click="issues.push({ description: '', level: 'WARNING' })">+ 添加问题</el-button>
        </div>

        <div class="card">
          <div class="card-title">回执人</div>
          <div class="form-row">
            <div class="label">姓名<span class="required">*</span></div>
            <div class="control"><el-input v-model="submitter.name" :disabled="!detail.submittable" placeholder="请填写真实姓名" maxlength="32" /></div>
          </div>
          <div class="form-row">
            <div class="label">手机号</div>
            <div class="control"><el-input v-model="submitter.phone" :disabled="!detail.submittable" placeholder="选填，便于联系" maxlength="11" /></div>
          </div>
          <div class="form-row">
            <div class="label">备注</div>
            <div class="control"><el-input v-model="submitter.summary" type="textarea" :rows="2" :disabled="!detail.submittable" placeholder="选填" maxlength="200" /></div>
          </div>
        </div>

        <div v-if="detail.submittable" class="footer">
          <el-button type="primary" size="large" :loading="submitting" class="submit-btn" @click="submit">提交回执</el-button>
          <div class="footer-tip">提交后不可重复修改，如需更正请联系任务负责人</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Plus } from '@element-plus/icons-vue'
import { calendarPublicApi } from '@/api/calendarPublic'

const route = useRoute()
const token = String(route.params.token || '')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const detail = ref<any>({})
const form = reactive<Record<string, string>>({})
const issues = ref<{ description: string; level: string }[]>([])
const submitter = reactive({ name: '', phone: '', summary: '' })

const photos = computed<any[]>(() => detail.value.photos || [])
const statusText = computed(() => ({
  PENDING: '待回执', IN_PROGRESS: '进行中', OVERDUE: '已超时',
  PENDING_RECTIFICATION: '待整改', COMPLETED: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
} as Record<string, string>)[detail.value.status] || detail.value.status)

const fields = computed(() => (detail.value.fields || []).map((item: any) => {
  const type = String(item.type || item.fieldType || '').toUpperCase()
  const options = Array.isArray(item.options) ? item.options.map((o: any) => typeof o === 'string' ? o : (o.label ?? o.value)).filter(Boolean) : []
  return {
    key: String(item.key || item.fieldKey || item.label || ''),
    label: String(item.label || item.name || item.key || '巡检项'),
    required: !!(item.required ?? item.isRequired),
    options,
    boolean: !options.length && ['BOOLEAN', 'CHECK', 'PASS_FAIL', 'RESULT'].includes(type),
    multiline: type.includes('TEXTAREA') || type === 'TEXT'
  }
}).filter((f: any) => f.key))

function formatTime(value: any) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  if (!token) { error.value = '回执链接无效'; return }
  loading.value = true
  try {
    detail.value = await calendarPublicApi.detail(token)
  } catch (e: any) {
    error.value = e?.message || '回执链接无效或已过期'
  } finally {
    loading.value = false
  }
}

async function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  const room = (detail.value.maxPhotos || 9) - photos.value.length
  if (files.length > room) { ElMessage.warning(`最多还能上传 ${room} 张`); return }
  loading.value = true
  try {
    for (const file of files) {
      if (file.size > 10 * 1024 * 1024) { ElMessage.warning(`${file.name} 超过 10MB，已跳过`); continue }
      detail.value = await calendarPublicApi.upload(token, file, submitter.name)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '照片上传失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!submitter.name.trim()) { ElMessage.warning('请填写回执人姓名'); return }
  if (submitter.phone && !/^1[3-9]\d{9}$/.test(submitter.phone.trim())) { ElMessage.warning('手机号格式不正确'); return }
  const payloadIssues = issues.value.filter(item => item.description.trim())
  submitting.value = true
  try {
    detail.value = await calendarPublicApi.submit(token, {
      name: submitter.name.trim(),
      phone: submitter.phone.trim(),
      summary: submitter.summary.trim(),
      result: form,
      issues: payloadIssues
    })
    ElMessage.success('回执已提交')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.receipt-page { min-height: 100vh; background: #f4f6f8; padding-bottom: 32px; }
.head { background: linear-gradient(135deg, #2f7cf6, #1c5fd0); color: #fff; padding: 20px 16px 18px; }
.head-title { font-size: 20px; font-weight: 600; line-height: 1.35; }
.head-sub { margin-top: 6px; font-size: 14px; opacity: .92; }
.head-meta { margin-top: 10px; font-size: 13px; opacity: .88; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.dot { opacity: .6; }
.status-tag { margin-left: auto; }
.head-expire { margin-top: 8px; font-size: 12px; opacity: .75; }
.body { padding: 12px; }
.card { background: #fff; border-radius: 12px; padding: 14px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(15, 40, 90, .06); }
.card-title { font-size: 15px; font-weight: 600; color: #1f2d3d; margin-bottom: 12px; display: flex; align-items: center; }
.counter { margin-left: auto; font-size: 12px; font-weight: 400; color: #8a94a6; }
.form-row { display: flex; gap: 10px; align-items: flex-start; padding: 8px 0; border-bottom: 1px solid #f1f3f6; }
.form-row:last-child { border-bottom: none; }
.label { width: 96px; flex: none; font-size: 14px; color: #4a5568; line-height: 32px; }
.control { flex: 1; min-width: 0; }
.required { color: #f56c6c; margin-left: 2px; }
.photo-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.photo-item, .photo-add { aspect-ratio: 1; border-radius: 8px; overflow: hidden; }
.photo-item img { width: 100%; height: 100%; object-fit: cover; }
.photo-add { border: 1px dashed #c8d1de; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #8a94a6; font-size: 12px; gap: 2px; background: #fafbfc; }
.hint { margin-top: 8px; font-size: 12px; color: #8a94a6; }
.issue-row { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 8px; }
.done-card { display: flex; align-items: center; gap: 12px; }
.done-icon { font-size: 30px; color: #67c23a; }
.done-title { font-size: 15px; font-weight: 600; color: #1f2d3d; }
.done-sub { margin-top: 4px; font-size: 12px; color: #8a94a6; }
.footer { margin-top: 4px; }
.submit-btn { width: 100%; }
.footer-tip { margin-top: 8px; text-align: center; font-size: 12px; color: #8a94a6; }
</style>
