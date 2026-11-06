<template>
  <div class="page">
    <h2>巡检整改（H5）</h2>
    <div v-if="!issue" class="loading">加载中…</div>
    <div v-else class="rectify-card">
      <div class="row"><b>责任部门</b>{{ issue.deptName || issue.deptCode }}</div>
      <div class="row"><b>截止</b>{{ issue.deadlineAt }}</div>
      <div class="row"><b>状态</b>{{ issue.status }}</div>
      <h4>异常项</h4>
      <ul>
        <li v-for="(it,i) in items" :key="i">
          <b>{{ it.category }}</b>｜{{ it.fieldLabel }}（值：{{ it.answer }}）
        </li>
      </ul>
      <el-divider />
      <div v-if="issue.status === 'CLOSED'">
        <div class="row"><b>整改说明</b>{{ issue.feedbackText }}</div>
        <div v-if="feedbackPhotos.length" class="photo-grid">
          <img v-for="(u,i) in feedbackPhotos" :key="i" :src="u" />
        </div>
      </div>
      <div v-else>
        <el-form label-position="top">
          <el-input v-model="feedbackText" type="textarea" :rows="4" placeholder="整改说明（必填）" />
          <div class="photo-row">
            <input type="file" accept="image/*" @change="uploadPhoto" />
            <div class="photo-grid">
              <img v-for="(u,i) in photos" :key="i" :src="u" />
            </div>
          </div>
          <el-button type="primary" @click="submit" :loading="submitting" style="margin-top:12px">提交整改</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const route = useRoute()
const token = (route.params.token as string) || ''
const issue = ref<any>(null)
const feedbackText = ref('')
const photos = ref<string[]>([])
const submitting = ref(false)

const items = computed(() => { try { return JSON.parse(issue.value?.itemsJson || '[]') } catch { return [] } })
const feedbackPhotos = computed(() => { try { return JSON.parse(issue.value?.feedbackPhotosJson || '[]') } catch { return [] } })

onMounted(async () => {
  try {
    issue.value = await http.get('/api/v1/qr-inspection/rectify/' + token)
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '链接无效') }
})

async function uploadPhoto(e: any) {
  const f = e.target.files?.[0]
  if (!f) return
  const form = new FormData(); form.append('file', f)
  try {
    const res: any = await http.post('/api/v1/operation/forms/image', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    photos.value.push(res)
  } catch (err) { ElMessage.error('上传失败') }
}

async function submit() {
  if (!feedbackText.value.trim()) { ElMessage.warning('请填写整改说明'); return }
  submitting.value = true
  try {
    await http.post('/api/v1/qr-inspection/rectify/' + token, { feedbackText: feedbackText.value, feedbackPhotos: photos.value })
    ElMessage.success('已提交，感谢')
    setTimeout(() => location.reload(), 800)
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '提交失败') }
  finally { submitting.value = false }
}
</script>

<style scoped>
.page { max-width:520px; margin:0 auto; padding:16px; }
.rectify-card { background:#fff; border-radius:12px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,.08); }
.row { padding:6px 0; border-bottom:1px dashed #f3f4f6; }
.photo-grid { display:flex; flex-wrap:wrap; gap:8px; margin-top:8px; }
.photo-grid img { width:90px; height:90px; object-fit:cover; border-radius:6px; }
</style>
