<template><div class="page"><div class="page-header"><div class="page-title">操作审计日志</div></div><el-card><el-form inline><el-form-item label="操作人"><el-input v-model="query.operator" clearable/></el-form-item><el-form-item label="业务类型"><el-input v-model="query.businessType" clearable placeholder="流程管理"/></el-form-item><el-button type="primary" @click="load">查询</el-button></el-form><el-table v-loading="loading" :data="page.records" border><el-table-column prop="createdAt" label="时间" width="180"><template #default="{row}">{{formatDateTime(row.createdAt)}}</template></el-table-column><el-table-column prop="operator" label="操作人" width="130"/><el-table-column prop="operation" label="操作" min-width="260"/><el-table-column prop="businessType" label="业务类型" width="150"><template #default="{row}">{{businessLabel(row.businessType)}}</template></el-table-column><el-table-column prop="businessId" label="业务ID" width="110"/><el-table-column prop="ip" label="来源IP" width="140"/><el-table-column label="结果" width="100"><template #default="{row}"><el-tag :type="result(row).success?'success':'danger'">{{result(row).success?'成功':'失败'}}</el-tag></template></el-table-column><el-table-column label="耗时" width="100"><template #default="{row}">{{result(row).durationMs??'-'}} ms</template></el-table-column></el-table><el-pagination style="margin-top:16px;justify-content:flex-end" background layout="total,prev,pager,next" :total="page.total||0" :page-size="20" v-model:current-page="query.pageNum" @current-change="load"/></el-card></div></template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi } from '@/api/audit'
import { formatDateTime } from '@/utils/format'
const loading = ref(false)
const page = ref<any>({ records: [], total: 0 })
const query = reactive({ operator: '', businessType: '', pageNum: 1, pageSize: 20 })
function result(row: any) {
  try { return JSON.parse(row.afterData || '{}') } catch { return {} }
}
function businessLabel(v: string) {
  return ({ AUTH:'认证', USERS:'用户管理', ROLES:'角色管理', WORKFLOWS:'流程管理', MERCHANTS:'商户管理', STALLS:'铺位管理', STALL_DEVICES:'设备绑定', PLATFORM:'源平台', WEBHOOKS:'通知配置' } as any)[v] || v
}
async function load() {
  loading.value = true
  try { page.value = await auditApi.list(query) } finally { loading.value = false }
}
onMounted(load)
</script>
