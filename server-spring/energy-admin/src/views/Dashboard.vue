<template>
  <div class="page">
    <div class="page-header"><div class="page-title">工作台</div></div>
    <div class="stat-grid">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">计费账户总数</div>
            <div class="stat-value">{{ stats.billingAccounts }}</div>
            <el-icon class="stat-icon" color="#409EFF"><OfficeBuilding /></el-icon>
          </div>
        </el-card>
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">铺位总数</div>
            <div class="stat-value">{{ stats.stalls }}</div>
            <el-icon class="stat-icon" color="#67C23A"><Grid /></el-icon>
          </div>
        </el-card>
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">仓库数量</div>
            <div class="stat-value">{{ stats.warehouses }}</div>
            <el-icon class="stat-icon" color="#909399"><House /></el-icon>
          </div>
        </el-card>
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">已绑定设备铺位</div>
            <div class="stat-value">{{ stats.boundStalls }}</div>
            <el-icon class="stat-icon" color="#E6A23C"><Link /></el-icon>
          </div>
        </el-card>
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">本月提交流程</div>
            <div class="stat-value">{{ stats.monthlySubmittedWorkflows }}</div>
            <el-icon class="stat-icon" color="#F56C6C"><DocumentChecked /></el-icon>
          </div>
        </el-card>
    </div>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card>
          <template #header>欢迎使用</template>
          <p>当前用户：<b>{{ userStore.user?.displayName || userStore.user?.username }}</b></p>
          <p>角色：<el-tag v-for="r in userStore.user?.roles" :key="r" type="warning" effect="dark" size="small" style="margin-right: 4px">{{ enumLabel(r) }}</el-tag></p>
          <p>Token 剩余有效期：<span class="text-muted">{{ expiresInMinutes }} 分钟</span></p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { OfficeBuilding, Grid, Link, House, DocumentChecked } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { dashboardApi } from '@/api/dashboard'
import { enumLabel } from '@/utils/format'

const userStore = useUserStore()
const stats = ref({ billingAccounts: 0, stalls: 0, warehouses: 0, boundStalls: 0, monthlySubmittedWorkflows: 0 })
const expiresInMinutes = ref(720)

async function load() {
  try { stats.value = await dashboardApi.stats() as any } catch {}
}

onMounted(load)
</script>

<style scoped lang="scss">
.stat { display: flex; flex-direction: column; position: relative; min-height: 80px; }
.stat-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 600; margin: 4px 0; }
.stat-value-sm { font-size: 14px; margin: 6px 0; }
.stat-icon { position: absolute; right: 0; top: 0; font-size: 36px; opacity: 0.7; }
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } }
</style>
