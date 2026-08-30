<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">计费账户总数</div>
            <div class="stat-value">{{ stats.billingAccounts }}</div>
            <el-icon class="stat-icon" color="#409EFF"><OfficeBuilding /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">铺位总数</div>
            <div class="stat-value">{{ stats.stalls }}</div>
            <el-icon class="stat-icon" color="#67C23A"><Grid /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">仓库数量</div>
            <div class="stat-value">{{ stats.warehouses }}</div>
            <el-icon class="stat-icon" color="#909399"><House /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">设备绑定</div>
            <div class="stat-value">{{ stats.devices }}</div>
            <el-icon class="stat-icon" color="#E6A23C"><Link /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

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
import { OfficeBuilding, Grid, Link, House } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { accountApi, stallApi, deviceApi } from '@/api/merchant'
import { enumLabel } from '@/utils/format'

const userStore = useUserStore()
const stats = ref({ billingAccounts: 0, stalls: 0, warehouses: 0, devices: 0 })
const expiresInMinutes = ref(720)

async function load() {
  try {
    const accounts: any = await accountApi.page({ showDisabled: false, pageNum: 1, pageSize: 1 })
    stats.value.billingAccounts = accounts.total || 0
  } catch {}
  try {
    const warehouses: any = await stallApi.page({ businessType: 'CK', pageNum: 1, pageSize: 1 })
    stats.value.warehouses = warehouses.total || 0
  } catch {}
  try {
    const s: any = await stallApi.page({ pageNum: 1, pageSize: 1 })
    stats.value.stalls = s.total || 0
  } catch {}
  try {
    const d: any = await deviceApi.search({ bound: true, pageNum: 1, pageSize: 1 })
    stats.value.devices = d.total || 0
  } catch {}
}

onMounted(load)
</script>

<style scoped lang="scss">
.stat { display: flex; flex-direction: column; position: relative; min-height: 80px; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { font-size: 28px; font-weight: 600; margin: 4px 0; }
.stat-value-sm { font-size: 14px; margin: 6px 0; }
.stat-icon { position: absolute; right: 0; top: 0; font-size: 36px; opacity: 0.7; }
</style>
