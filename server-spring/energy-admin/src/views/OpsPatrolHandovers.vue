<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">交接记录</div>
        <div class="page-note">每日 14:00 班次交接单，汇总当班检查与截至 14:30 未闭环问题，由接班干部在 H5 确认。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!rows.length" description="当日暂无交接记录" />
      <div v-for="h in rows" :key="h.id" class="ho-card">
        <div class="ho-head">
          <div class="ho-title">
            <b>{{ h.deptName }}</b>
            <el-tag size="small" :type="h.confirmed ? 'success' : 'warning'" effect="plain">{{ h.confirmed ? '已确认' : '待确认' }}</el-tag>
          </div>
          <div class="ho-meta">
            <span>交班：{{ h.fromUserName || '未排班' }}</span>
            <span>本班检查：合格 {{ h.passCount || 0 }} / 不合格 {{ h.failCount || 0 }}</span>
            <span v-if="h.confirmed">确认：{{ h.confirmedBy }} · {{ fmt(h.confirmedAt) }}</span>
          </div>
          <div v-if="h.remark" class="ho-remark">交接备注：{{ h.remark }}</div>
        </div>
        <el-table v-if="h.issues?.length && !isMobile" :data="h.issues" size="small" border>
          <el-table-column prop="issueNo" label="问题单号" width="135" />
          <el-table-column prop="merchantName" label="店铺" width="170"><template #default="{row}">{{row.merchantName||'-'}}</template></el-table-column>
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column prop="description" label="问题描述" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{row}"><el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
        </el-table>
        <div v-else-if="h.issues?.length" class="m-issues">
          <div v-for="it in h.issues" :key="it.id" class="m-issue">
            <div class="m-issue-hd">
              <span class="m-issue-no">{{ it.issueNo }}</span>
              <el-tag size="small" :type="statusTagType(it.status)">{{ statusLabel(it.status) }}</el-tag>
            </div>
            <div class="m-issue-store">{{ it.merchantName || '未关联商户' }}<template v-if="it.stallCode">（{{ it.stallCode }}）</template></div>
            <div class="m-issue-desc">{{ it.description }}</div>
          </div>
        </div>
        <div v-else class="ho-empty">无未闭环问题</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useIsMobile } from '@/composables/useIsMobile'

const isMobile = useIsMobile()
const date = ref(localDate())
const rows = ref<any[]>([])
const loading = ref(false)

function localDate() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
async function load() {
  loading.value = true
  try { rows.value = ((await opsPatrolApi.handovers(date.value)) as any) || [] }
  finally { loading.value = false }
}
function statusLabel(st: string) {
  return ({ PENDING: '待跟进', RECTIFYING: '整改中', REVIEWING: '待复核', CLOSED: '已闭环' } as Record<string, string>)[st] || st
}
function statusTagType(st: string) {
  return ({ PENDING: 'danger', RECTIFYING: 'warning', REVIEWING: 'primary', CLOSED: 'success' } as Record<string, any>)[st] || 'info'
}
function fmt(v: string) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-' }
onMounted(load)
</script>

<style scoped>
.actions{display:flex;align-items:center;gap:10px}
.ho-card{background:#fff;border:1px solid #ebeef5;border-radius:10px;padding:14px;margin-bottom:14px}
.ho-head{margin-bottom:10px}
.ho-title{display:flex;align-items:center;gap:10px;font-size:15px}
.ho-meta{display:flex;gap:18px;flex-wrap:wrap;font-size:13px;color:#606266;margin-top:6px}
.ho-remark{font-size:13px;color:#909399;margin-top:6px}
.ho-empty{font-size:13px;color:#909399;padding:8px 0}
.m-issues{display:flex;flex-direction:column;gap:8px}
.m-issue{border:1px solid #f0f2f5;border-radius:8px;padding:10px 12px;background:#fafcff}
.m-issue-hd{display:flex;justify-content:space-between;align-items:center;gap:8px}
.m-issue-no{font-size:12px;color:#909399;font-family:ui-monospace,Menlo,monospace}
.m-issue-store{font-size:15px;font-weight:600;color:#303133;margin:6px 0 3px}
.m-issue-desc{font-size:13px;color:#606266;line-height:1.55}
@media (max-width:768px){
  .ho-card{padding:12px}
  .ho-meta{gap:8px;flex-direction:column;font-size:12px}
  .ho-title{font-size:16px}
}
</style>
