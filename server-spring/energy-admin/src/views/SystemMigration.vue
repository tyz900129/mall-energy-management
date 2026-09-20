<template>
  <div class="page migration-page">
    <div class="page-header">
      <div>
        <div class="page-title">数据迁移</div>
        <div class="text-muted subtitle">完整迁移数据库、现场核验照片及其他本地附件</div>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon class="warning">
      <template #title>仅超级管理员可操作。导入采用整库覆盖，不能与当前数据合并。</template>
      导入时系统会进入维护模式，并自动保留一份导入前安全备份。请妥善保存备份密码，系统不会记录，也无法找回。
    </el-alert>

    <div class="card-grid">
      <el-card shadow="never">
        <template #header><div class="card-title"><el-icon><Download /></el-icon>一键导出</div></template>
        <p class="description">生成加密的 <code>.wymbackup</code> 完整备份包，可迁移到相同或更高版本的系统。</p>
        <el-form label-position="top">
          <el-form-item label="设置备份密码">
            <el-input v-model="exportPassword" type="password" show-password maxlength="128" placeholder="至少 8 位，用于加密备份包" autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="再次输入备份密码">
            <el-input v-model="exportPasswordAgain" type="password" show-password maxlength="128" placeholder="请再次输入" autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" :loading="exportBusy" :disabled="exportBusy" @click="startExport">生成完整备份</el-button>
        </el-form>
        <JobProgress v-if="exportJob" :job="exportJob" class="job" />
        <el-button v-if="exportJob?.status === 'COMPLETED'" type="success" plain class="download" @click="downloadBackup">
          下载 {{ exportJob.fileName }}
        </el-button>
      </el-card>

      <el-card shadow="never">
        <template #header><div class="card-title"><el-icon><Upload /></el-icon>一键导入</div></template>
        <p class="description">备份包将分块上传，网络波动不会触发重复导入。上传后必须先通过完整性及版本校验。</p>
        <el-form label-position="top">
          <el-form-item label="选择备份包">
            <input ref="fileInput" class="file-input" type="file" accept=".wymbackup,application/octet-stream" :disabled="importBusy" @change="chooseFile" />
            <div v-if="selectedFile" class="file-meta">{{ selectedFile.name }} · {{ formatBytes(selectedFile.size) }}</div>
          </el-form-item>
          <el-form-item label="备份密码">
            <el-input v-model="importPassword" type="password" show-password maxlength="128" placeholder="创建备份时设置的密码" autocomplete="off" />
          </el-form-item>
          <el-button type="primary" :loading="uploading || validating" :disabled="importBusy" @click="uploadAndValidate">上传并校验</el-button>
        </el-form>
        <div v-if="uploading" class="upload-progress">
          <div>正在分块上传：{{ uploadPercent }}%</div>
          <el-progress :percentage="uploadPercent" :stroke-width="10" />
        </div>
        <JobProgress v-if="importJob" :job="importJob" class="job" />
      </el-card>
    </div>

    <el-card v-if="importJob?.status === 'READY' && importJob.preview" shadow="never" class="preview-card">
      <template #header><div class="card-title"><el-icon><DocumentChecked /></el-icon>导入预检结果</div></template>
      <el-descriptions :column="isMobile ? 1 : 3" border>
        <el-descriptions-item label="来源系统">{{ importJob.preview.systemName }}</el-descriptions-item>
        <el-descriptions-item label="数据库版本">V{{ importJob.preview.schemaVersion }}</el-descriptions-item>
        <el-descriptions-item label="备份时间">{{ formatTime(importJob.preview.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="数据库文件">{{ formatBytes(importJob.preview.databaseBytes) }}</el-descriptions-item>
        <el-descriptions-item label="附件数量">{{ importJob.preview.uploadFileCount }}</el-descriptions-item>
        <el-descriptions-item label="附件容量">{{ formatBytes(importJob.preview.uploadBytes) }}</el-descriptions-item>
      </el-descriptions>
      <div class="count-title">主要数据量</div>
      <div class="count-grid">
        <div v-for="item in countItems(importJob.preview.recordCounts)" :key="item.label" class="count-item">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
        </div>
      </div>
      <el-divider />
      <el-alert type="error" :closable="false" show-icon title="下一步会覆盖当前系统全部业务数据与附件。" />
      <div class="confirm-row">
        <el-input v-model="confirmation" placeholder="请输入：确认覆盖导入" :disabled="restoring" />
        <el-button type="danger" :loading="restoring" :disabled="confirmation !== '确认覆盖导入'" @click="restore">执行覆盖导入</el-button>
      </div>
    </el-card>

    <el-card v-if="restoring || ['RESTORING','ROLLING_BACK','COMPLETED','FAILED'].includes(importJob?.status || '')" shadow="never" class="restore-card">
      <template #header><div class="card-title"><el-icon><Refresh /></el-icon>恢复状态</div></template>
      <JobProgress v-if="importJob" :job="importJob" />
      <el-alert v-if="importJob?.status === 'COMPLETED'" type="success" :closable="false" show-icon
        title="数据导入完成。为确保权限和缓存全部刷新，请退出后重新登录。" />
      <el-button v-if="importJob?.status === 'COMPLETED'" type="primary" class="relogin" @click="relogin">退出并重新登录</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox, ElProgress, ElTag } from 'element-plus'
import { migrationApi, type MigrationJob, type MigrationManifest } from '@/api/migration'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const JobProgress = defineComponent({
  props: { job: { type: Object as () => MigrationJob, required: true } },
  setup(props) {
    const statusType = () => ['FAILED'].includes(props.job.status) ? 'danger' : props.job.status === 'COMPLETED' || props.job.status === 'READY' ? 'success' : 'warning'
    const statusText = () => ({ QUEUED:'排队中', RUNNING:'生成中', VALIDATING:'校验中', READY:'校验通过', QUEUED_RESTORE:'等待导入', RESTORING:'导入中', ROLLING_BACK:'自动回滚中', COMPLETED:'已完成', FAILED:'失败' } as Record<string,string>)[props.job.status] || props.job.status
    return () => h('div', { class: 'job-box' }, [
      h('div', { class: 'job-head' }, [h(ElTag, { type: statusType() }, () => statusText()), h('span', props.job.message)]),
      h(ElProgress, { percentage: props.job.progress || 0, status: props.job.status === 'FAILED' ? 'exception' : props.job.status === 'COMPLETED' || props.job.status === 'READY' ? 'success' : undefined }),
      props.job.error ? h('div', { class: 'job-error' }, props.job.error) : null
    ])
  }
})

const router = useRouter(), userStore = useUserStore()
const exportPassword = ref(''), exportPasswordAgain = ref(''), importPassword = ref(''), confirmation = ref('')
const exportJob = ref<MigrationJob>(), importJob = ref<MigrationJob>(), selectedFile = ref<File>()
const uploading = ref(false), validating = ref(false), restoring = ref(false), uploadPercent = ref(0)
const isMobile = ref(window.innerWidth <= 768)
const timers = new Set<number>()
const exportBusy = computed(() => !!exportJob.value && ['QUEUED','RUNNING'].includes(exportJob.value.status))
const importBusy = computed(() => uploading.value || validating.value || restoring.value)

function chooseFile(event: Event) { selectedFile.value = (event.target as HTMLInputElement).files?.[0] }
function formatBytes(value = 0) { if (!value) return '0 B'; const units=['B','KB','MB','GB','TB']; const i=Math.min(units.length-1,Math.floor(Math.log(value)/Math.log(1024))); return `${(value/Math.pow(1024,i)).toFixed(i?1:0)} ${units[i]}` }
function formatTime(value: string) { return value ? formatDateTime(value) : '-' }
function countItems(counts: Record<string,number>) {
  const labels:Record<string,string>={sys_user:'用户',platform_merchant_account:'商户账户',stall:'铺位',platform_device:'设备',stall_device_binding:'设备绑定',workflow:'流程',field_verification_task:'现场核验任务',attachment:'附件记录',energy_usage_monthly:'月用量记录',energy_usage_daily:'日用量记录'}
  return Object.entries(counts || {}).map(([key,value])=>({label:labels[key]||key,value}))
}

async function startExport() {
  if (exportPassword.value.length < 8) return ElMessage.warning('备份密码至少 8 位')
  if (exportPassword.value !== exportPasswordAgain.value) return ElMessage.warning('两次输入的备份密码不一致')
  await ElMessageBox.confirm('将生成包含数据库和全部附件的加密备份包。请牢记备份密码。', '确认完整导出', { type:'warning', confirmButtonText:'开始生成' })
  exportJob.value = await migrationApi.startExport(exportPassword.value)
  exportPassword.value='';exportPasswordAgain.value=''
  pollJob(exportJob.value.id, value => exportJob.value=value)
}

function downloadBackup() {
  if (!exportJob.value?.downloadToken) return
  const anchor=document.createElement('a');anchor.href=migrationApi.downloadUrl(exportJob.value);anchor.download=exportJob.value.fileName||'wanyuecity.wymbackup';document.body.appendChild(anchor);anchor.click();anchor.remove()
}

async function uploadAndValidate() {
  const file=selectedFile.value
  if (!file) return ElMessage.warning('请选择备份包')
  if (importPassword.value.length < 8) return ElMessage.warning('请输入至少 8 位的备份密码')
  uploading.value=true;uploadPercent.value=0;importJob.value=undefined
  try {
    const init=await migrationApi.initializeUpload(file)
    const chunks=Math.ceil(file.size/init.chunkBytes)
    for(let index=0;index<chunks;index++){
      const start=index*init.chunkBytes,end=Math.min(file.size,start+init.chunkBytes)
      await migrationApi.uploadChunk(init.uploadId,index,file.slice(start,end),part=>{uploadPercent.value=Math.min(99,Math.round(((index+part)/chunks)*100))})
    }
    uploadPercent.value=100;uploading.value=false;validating.value=true
    importJob.value=await migrationApi.validate(init.uploadId,importPassword.value)
    importPassword.value=''
    pollJob(importJob.value.id,value=>{importJob.value=value;if(['READY','FAILED'].includes(value.status))validating.value=false})
  } catch { uploading.value=false;validating.value=false }
}

async function restore() {
  if (!importJob.value) return
  await ElMessageBox.confirm('当前数据库和附件将被该备份包整体替换。系统会先自动创建安全备份，导入期间其他用户暂时无法操作。', '最后确认', { type:'error', confirmButtonText:'确认覆盖并导入', cancelButtonText:'取消' })
  restoring.value=true
  importJob.value=await migrationApi.restore(importJob.value.id,confirmation.value)
  pollJob(importJob.value.id,value=>{importJob.value=value;if(['COMPLETED','FAILED'].includes(value.status))restoring.value=false},3000)
}

function pollJob(id:string, assign:(job:MigrationJob)=>void, interval=1500) {
  const run=async()=>{try{const job=await migrationApi.job(id);assign(job);if(!['COMPLETED','FAILED','READY'].includes(job.status)){const timer=window.setTimeout(run,interval);timers.add(timer)}}catch{const timer=window.setTimeout(run,Math.max(interval,3000));timers.add(timer)}}
  run()
}
function relogin(){userStore.logout();router.replace({path:'/login',query:{switch:'1'}})}
onBeforeUnmount(()=>timers.forEach(timer=>clearTimeout(timer)))
</script>

<style scoped>
.migration-page{max-width:1180px;margin:0 auto}.subtitle{margin-top:5px}.warning{margin-bottom:16px}.card-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.card-title{display:flex;align-items:center;gap:8px;font-size:16px;font-weight:600}.description{min-height:48px;color:#606266;line-height:1.7;margin:0 0 16px}.file-input{display:block;width:100%;padding:9px;border:1px dashed #c0c4cc;border-radius:4px;background:#fafafa}.file-meta{margin-top:8px;color:#606266;word-break:break-all}.job,.upload-progress{margin-top:18px}.download,.relogin{margin-top:12px}.preview-card,.restore-card{margin-top:16px}.count-title{font-weight:600;margin:18px 0 10px}.count-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px}.count-item{display:flex;flex-direction:column;gap:5px;padding:12px;background:#f5f7fa;border-radius:6px}.count-item span{color:#606266;font-size:13px}.count-item strong{font-size:20px}.confirm-row{display:flex;gap:10px;max-width:600px;margin-top:14px}.confirm-row .el-button{flex:none}:deep(.job-box){margin-top:6px}:deep(.job-head){display:flex;align-items:center;gap:9px;margin-bottom:10px;color:#606266}:deep(.job-error){margin-top:8px;color:#f56c6c;font-size:13px}
@media(max-width:768px){.card-grid{grid-template-columns:1fr}.description{min-height:0}.count-grid{grid-template-columns:repeat(2,1fr)}.confirm-row{flex-direction:column}.confirm-row .el-button{width:100%;margin-left:0}.migration-page{padding-bottom:30px}}
</style>
