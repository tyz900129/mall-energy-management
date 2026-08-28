<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">同步日志</div>
      <el-button type="primary" @click="onTrigger" :loading="triggering">立即同步</el-button>
    </div>
    <el-card>
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column prop="startedAt" label="开始时间" width="180"><template #default="{row}">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
        <el-table-column prop="completedAt" label="完成时间" width="180"><template #default="{row}">{{ formatDateTime(row.completedAt) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : (row.status === 'RUNNING' ? 'warning' : 'danger')" size="small">
              {{ enumLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="accountCount" label="账户" width="80" />
        <el-table-column prop="deviceCount" label="设备" width="80" />
        <el-table-column prop="relationCount" label="关系" width="80" />
        <el-table-column prop="triggerType" label="触发" width="100"><template #default="{row}">{{ enumLabel(row.triggerType) }}</template></el-table-column>
        <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end; display: flex"
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '@/api/platform'
import { enumLabel, formatDateTime } from '@/utils/format'

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const triggering = ref(false)
const page = reactive({ current: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const r: any = await platformApi.listBatches({ pageNum: page.current, pageSize: page.size })
    rows.value = r.records || []
    total.value = Number(r.total) || 0
  } finally {
    loading.value = false
  }
}

async function onTrigger() {
  triggering.value = true
  try {
    const r: any = await platformApi.triggerSync()
    ElMessage.success(`同步已启动，批次号: ${r.batchNo}`)
    load()
  } finally {
    triggering.value = false
  }
}

onMounted(load)
</script>

