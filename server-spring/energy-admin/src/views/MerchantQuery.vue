<template>
  <div class="query-page">
    <div class="page-header">
      <div>
        <div class="page-title">商户账户查询</div>
        <div class="page-note">输入完整六位商户代码，查询商户账户基础信息和当前关联铺位。</div>
      </div>
    </div>

    <el-card shadow="never" class="search-card">
      <el-form @submit.prevent="search">
        <el-form-item label="商户代码" class="code-item">
          <el-input v-model="merchantCode" maxlength="6" inputmode="numeric" pattern="[0-9]*"
            clearable placeholder="请输入连续六位数字" @keyup.enter="search" />
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="searched && !loading && !accounts.length" description="未查询到该商户代码对应的账户" />
    <div v-else-if="accounts.length" class="results">
      <el-alert v-if="accounts.length > 1" type="warning" :closable="false"
        :title="`该商户代码对应 ${accounts.length} 个账户，请注意核对账户状态和关联铺位。`" />
      <el-card v-for="account in accounts" :key="account.accountNumber" shadow="never" class="account-card">
        <template #header>
          <div class="card-title">
            <span>{{ account.accountName || '未命名账户' }}</span>
            <el-tag :type="statusType(account.platformStatus)">{{ statusText(account.platformStatus) }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="isMobile ? 1 : 2" border>
          <el-descriptions-item label="商户代码">{{ account.merchantCode || merchantCode }}</el-descriptions-item>
          <el-descriptions-item label="账户编号">{{ account.accountNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="登录账号">{{ account.userName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ account.mobilePhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联铺位" :span="isMobile ? 1 : 2">
            <template v-if="account.stallCodes?.length">
              <el-tag v-for="code in account.stallCodes" :key="code" class="stall-tag">{{ code }}</el-tag>
            </template>
            <span v-else>暂无关联铺位</span>
          </el-descriptions-item>
          <el-descriptions-item label="账户余额">{{ money(account.balance) }}</el-descriptions-item>
          <el-descriptions-item label="欠费金额">{{ money(account.arrears) }}</el-descriptions-item>
          <el-descriptions-item label="最近同步" :span="isMobile ? 1 : 2">{{ formatDateTime(account.lastSyncedAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { accountApi } from '@/api/merchant'
import { formatDateTime } from '@/utils/format'

const merchantCode = ref('')
const accounts = ref<any[]>([])
const loading = ref(false)
const searched = ref(false)
const isMobile = ref(window.innerWidth <= 768)

async function search() {
  const code = merchantCode.value.trim()
  if (!/^\d{6}$/.test(code)) { ElMessage.warning('请输入连续六位商户代码'); return }
  loading.value = true
  try {
    accounts.value = (await accountApi.findByMerchantCode(code) as any) || []
    searched.value = true
  } finally { loading.value = false }
}

function statusText(status?: string) {
  const value = String(status ?? '').toUpperCase()
  return ['0', 'DISABLED', 'INACTIVE'].includes(value) ? '已停用' : '启用中'
}
function statusType(status?: string) { return statusText(status) === '启用中' ? 'success' : 'info' }
function money(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  const amount = Number(value)
  return Number.isFinite(amount) ? `¥${amount.toFixed(2)}` : String(value)
}
</script>

<style scoped lang="scss">
.query-page { padding: 20px; }
.search-card { margin-bottom: 16px; }
.code-item { margin-bottom: 0; }
.code-item :deep(.el-form-item__content) { flex-wrap: nowrap; gap: 10px; }
.code-item .el-input { max-width: 360px; }
.results { display: grid; gap: 14px; }
.card-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-weight: 600; }
.stall-tag { margin: 2px 6px 2px 0; }
@media (max-width: 768px) {
  .query-page { padding: 12px; }
  .page-header { margin-bottom: 12px; }
  .code-item :deep(.el-form-item__label) { width: 100%; justify-content: flex-start; }
  .code-item :deep(.el-form-item__content) { width: 100%; margin-left: 0 !important; }
  .code-item .el-input { min-width: 0; }
  .code-item .el-button { flex: 0 0 auto; }
  .account-card :deep(.el-card__body) { padding: 12px; }
}
</style>
