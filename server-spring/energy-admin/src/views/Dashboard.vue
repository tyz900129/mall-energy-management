<template>
  <div class="page">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">租户总数</div>
            <div class="stat-value">{{ stats.merchants }}</div>
            <el-icon class="stat-icon" color="#409EFF"><OfficeBuilding /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">铺位总数</div>
            <div class="stat-value">{{ stats.stalls }}</div>
            <el-icon class="stat-icon" color="#67C23A"><Grid /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">设备绑定</div>
            <div class="stat-value">{{ stats.devices }}</div>
            <el-icon class="stat-icon" color="#E6A23C"><Link /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-label">上次同步</div>
            <div class="stat-value-sm">{{ config.lastSyncAt ? formatDateTime(config.lastSyncAt) : '尚未同步' }}</div>
            <el-tag v-if="config.lastSyncStatus" :type="config.lastSyncStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ enumLabel(config.lastSyncStatus) }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card>
          <template #header>欢迎使用</template>
          <p>当前用户：<b>{{ userStore.user?.displayName || userStore.user?.username }}</b></p>
          <p>角色：<el-tag v-for="r in userStore.user?.roles" :key="r" type="warning" effect="dark" size="small" style="margin-right: 4px">{{ enumLabel(r) }}</el-tag></p>
          <p>Token 剩余有效期：<span class="text-muted">{{ expiresInMinutes }} 分钟</span></p>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>源平台状态</template>
          <p>平台名：<b>{{ config.platformName }}</b></p>
          <p>API 地址：<code>{{ config.baseUrl }}</code></p>
          <p>启用：<el-tag :type="config.enabled ? 'success' : 'info'" size="small">{{ config.enabled ? '是' : '否' }}</el-tag></p>
          <p>自动同步：<el-tag :type="config.autoSync ? 'success' : 'info'" size="small">{{ config.autoSync ? '是' : '否' }}</el-tag></p>
          <p>同步 Cron：<code>{{ config.syncCron }}</code></p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { OfficeBuilding, Grid, Link } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { platformApi, type PlatformConfig } from '@/api/platform'
import { merchantApi, stallApi, deviceApi } from '@/api/merchant'
import { enumLabel, formatDateTime } from '@/utils/format'

const userStore = useUserStore()
const config = ref<Partial<PlatformConfig>>({})
const stats = ref({ merchants: 0, stalls: 0, devices: 0 })
const expiresInMinutes = ref(720)

async function load() {
  try {
    config.value = await platformApi.getConfig()
  } catch (e) { /* ignore */ }
  try {
    const m: any = await merchantApi.page({ pageNum: 1, pageSize: 1 })
    stats.value.merchants = m.total || 0
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

