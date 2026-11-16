<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">账户异常通知</div>
        <div class="page-note">定时从源平台拉取状态为「异常」的账户，向已配置的异常通知 Webhook 推送；账户恢复后自动标记，已加入不推送清单的账户不再通知。</div>
      </div>
      <el-button type="primary" :loading="syncing" @click="syncNow">立即同步并通知</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="异常账户" name="accounts">
        <el-card shadow="never">
          <el-form inline>
            <el-form-item label="账户搜索"><el-input v-model="query.keyword" clearable placeholder="账户编号、名称或手机号" style="width:260px" @keyup.enter="search" /></el-form-item>
            <el-form-item label="推送设置">
              <el-select v-model="query.excluded" clearable placeholder="全部" style="width:140px">
                <el-option label="正常推送" :value="false" />
                <el-option label="不推送" :value="true" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button @click="search">查询</el-button></el-form-item>
          </el-form>
          <el-table :data="rows" v-loading="loading" border empty-text="暂无异常账户，请先执行同步">
            <el-table-column prop="accountNumber" label="账户编号" min-width="155" />
            <el-table-column prop="accountName" label="账户名称" min-width="190"><template #default="{row}">{{row.accountName||'-'}}</template></el-table-column>
            <el-table-column label="商户/用户" min-width="170"><template #default="{row}">{{row.userName||'-'}}</template></el-table-column>
            <el-table-column prop="mobilePhone" label="手机号" width="135"><template #default="{row}">{{row.mobilePhone||'-'}}</template></el-table-column>
            <el-table-column label="余额" width="110"><template #default="{row}">{{money(row.balance)}}</template></el-table-column>
            <el-table-column label="异常类型" min-width="150"><template #default="{row}">{{row.exceptionType||'账户异常'}}</template></el-table-column>
            <el-table-column label="通知状态" width="115"><template #default="{row}"><el-tag :type="Number(row.excluded)?'info':'danger'">{{Number(row.excluded)?'不推送':'正常推送'}}</el-tag></template></el-table-column>
            <el-table-column prop="exclusionRemark" label="不推送原因" min-width="150"><template #default="{row}">{{row.exclusionRemark||'-'}}</template></el-table-column>
            <el-table-column label="首次发现" width="170"><template #default="{row}">{{formatDateTime(row.firstSeenAt)}}</template></el-table-column>
            <el-table-column label="最后发现" width="170"><template #default="{row}">{{formatDateTime(row.lastSeenAt)}}</template></el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{row}"><el-button link :type="Number(row.excluded)?'primary':'warning'" @click="toggle(row)">{{Number(row.excluded)?'恢复推送':'设为不推送'}}</el-button></template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="运行记录" name="runs">
        <el-card shadow="never">
          <el-table :data="runs" border empty-text="暂无同步记录">
            <el-table-column label="触发方式" width="110"><template #default="{row}">{{row.triggerType==='MANUAL'?'手动':'自动'}}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':row.status==='RUNNING'?'warning':'danger'">{{statusLabel(row.status)}}</el-tag></template></el-table-column>
            <el-table-column prop="fetchedCount" label="获取" width="80" />
            <el-table-column prop="excludedCount" label="不推送" width="90" />
            <el-table-column prop="notifiedCount" label="已通知" width="90" />
            <el-table-column label="开始时间" width="170"><template #default="{row}">{{formatDateTime(row.startedAt)}}</template></el-table-column>
            <el-table-column label="结束时间" width="170"><template #default="{row}">{{formatDateTime(row.completedAt)}}</template></el-table-column>
            <el-table-column prop="errorMessage" label="结果" min-width="200"><template #default="{row}">{{row.errorMessage||'完成'}}</template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="通知设置" name="settings">
        <el-card shadow="never">
          <el-form label-width="150px" style="max-width:560px">
            <el-form-item label="启用异常巡检"><el-switch v-model="form.enabled" /></el-form-item>
            <el-form-item label="同步间隔"><el-input-number v-model="form.checkIntervalMinutes" :min="5" :max="1440" /><span class="unit">分钟</span></el-form-item>
            <el-form-item label="静默时段"><el-time-picker v-model="form.quietStart" value-format="HH:mm:ss" placeholder="开始" style="width:150px" /><span class="unit">至</span><el-time-picker v-model="form.quietEnd" value-format="HH:mm:ss" placeholder="结束" style="width:150px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="saveSettings">保存设置</el-button><el-button @click="loadSettings">重置</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exceptionAccountApi } from '@/api/exceptionAccount'
import { formatDateTime } from '@/utils/format'

const tab = ref('accounts')
const rows = ref<any[]>([])
const runs = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const syncing = ref(false)
const page = reactive({ pageNum: 1, pageSize: 20 })
const query = reactive<{ keyword: string; excluded?: boolean }>({ keyword: '' })
const form = reactive<any>({ enabled: true, checkIntervalMinutes: 120, quietStart: null, quietEnd: null })

const money = (v: any) => (v == null ? '-' : `${Number(v).toFixed(2)} 元`)
const statusLabel = (v: string) => ({ RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败' } as any)[v] || v

async function load() {
  loading.value = true
  try {
    const r: any = await exceptionAccountApi.accounts({ ...page, keyword: query.keyword || undefined, excluded: query.excluded })
    rows.value = r.records || []
    total.value = Number(r.total || 0)
  } finally {
    loading.value = false
  }
}
function search() {
  page.pageNum = 1
  load()
}

async function loadRuns() {
  runs.value = (await exceptionAccountApi.runs()) as any
}

async function loadSettings() {
  const r: any = await exceptionAccountApi.settings()
  Object.assign(form, {
    enabled: !!Number(r.enabled),
    checkIntervalMinutes: Number(r.checkIntervalMinutes || 120),
    quietStart: r.quietStart || null,
    quietEnd: r.quietEnd || null
  })
}

async function saveSettings() {
  await exceptionAccountApi.saveSettings({ ...form })
  ElMessage.success('通知设置已保存')
  await loadSettings()
}

async function syncNow() {
  await ElMessageBox.confirm('将立即从源平台获取异常账户，并向已配置的异常通知 Webhook 发送消息，是否继续？', '立即同步')
  syncing.value = true
  try {
    await exceptionAccountApi.sync()
    ElMessage.success('异常账户同步和通知已完成')
    await Promise.all([load(), loadRuns()])
  } finally {
    syncing.value = false
  }
}

async function toggle(row: any) {
  if (Number(row.excluded)) {
    await exceptionAccountApi.exclusion(row.accountNumber, false)
    ElMessage.success('已恢复该账户推送')
  } else {
    const p = await ElMessageBox.prompt('请输入不推送原因，后续可随时恢复', '设置不推送账户', { inputValidator: (v) => !!v?.trim() || '请填写原因' })
    await exceptionAccountApi.exclusion(row.accountNumber, true, p.value.trim())
    ElMessage.success('已设为不推送账户')
  }
  await load()
}

watch(tab, (v) => {
  if (v === 'accounts') load()
  if (v === 'runs') loadRuns()
  if (v === 'settings') loadSettings()
})

onMounted(() => Promise.all([load(), loadRuns()]))
</script>

<style scoped>
.page-note { margin-top: 6px; color: #909399; font-size: 13px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.unit { margin-left: 8px; color: #909399; }
@media (max-width: 768px) {
  .page-header { align-items: flex-start; gap: 10px; }
  .el-form { display: flex; flex-direction: column; }
  .el-form-item { margin-right: 0; }
  .pager { overflow-x: auto; justify-content: flex-start; }
}
</style>
