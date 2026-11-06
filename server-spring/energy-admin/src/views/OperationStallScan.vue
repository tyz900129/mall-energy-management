<template>
  <div class="scan-page" v-if="stall">
    <div class="card stall-card">
      <div class="stall-title">{{ stall.stallCode }} {{ stall.stallName }}</div>
      <div class="stall-meta">
        <span>业态：{{ stall.businessTypeLabel || stall.businessType }}</span>
        <span v-if="stall.floorCode">· {{ stall.floorCode }}</span>
      </div>
    </div>

    <!-- 仓库：显示周期巡检 + 联合巡检入口 -->
    <template v-if="isWarehouse">
      <div v-for="s in qrScenes" :key="s.scene" class="card action-card" @click="openQrInspection(s)">
        <div class="action-icon">{{ s.scene === 'JOINT' ? '🔍' : '📋' }}</div>
        <div class="action-body">
          <div class="action-title">{{ sceneLabel(s) }}</div>
          <div class="action-sub" v-if="s.scene === 'JOINT'">任务：{{ s.name || s.taskId }}（截止 {{ s.endAt }}）</div>
          <div class="action-sub" v-else-if="s.templateName">{{ s.templateName }}</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <div v-if="qrScenes.length === 0" class="card empty-card">当前登录角色无可用巡检表单</div>
    </template>

    <!-- 商户：联合巡检（如有） + 原有商户入口 -->
    <template v-else>
      <div v-if="jointScene" class="card action-card joint" @click="openQrInspection(jointScene)">
        <div class="action-icon">🔍</div>
        <div class="action-body">
          <div class="action-title">参与联合巡检</div>
          <div class="action-sub">任务：{{ jointScene.name || jointScene.taskId }}（截止 {{ jointScene.endAt }}）</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <div class="card action-card" @click="openClearance">
        <div class="action-icon">🧹</div>
        <div class="action-body">
          <div class="action-title">商户日清</div>
          <div class="action-sub">商户每日营业结束后提交</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <div class="card action-card" @click="openProperty">
        <div class="action-icon">🏢</div>
        <div class="action-body">
          <div class="action-title">物业清场检查</div>
          <div class="action-sub">物业部对商户进行清场复核</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
      <div class="card action-card" @click="openMaterial">
        <div class="action-icon">📄</div>
        <div class="action-body">
          <div class="action-title">责任资料</div>
          <div class="action-sub">商户资质/经营证照</div>
        </div>
        <div class="action-arrow">›</div>
      </div>
    </template>

    <div v-if="!stall" class="card empty-card">二维码无效或已过期</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const route = useRoute()
const token = (route.params.token as string) || ''
const stall = ref<any>(null)
const scenes = ref<any[]>([])
const loading = ref(false)

const isWarehouse = computed(() => stall.value && stall.value.businessType === 'CK')
const qrScenes = computed(() => scenes.value.filter((s: any) => s.scene.startsWith('WAREHOUSE_') || s.scene === 'JOINT'))
const jointScene = computed(() => scenes.value.find((s: any) => s.scene === 'JOINT'))

function sceneLabel(s: any) {
  if (!s) return ''
  return s.scene === 'WAREHOUSE_COMMERCIAL' ? '商管巡检'
    : s.scene === 'WAREHOUSE_PROPERTY'  ? '物业巡检'
    : s.scene === 'JOINT' ? '联合巡检'
    : s.scene
}

onMounted(async () => {
  if (!token) return
  try {
    loading.value = true
    const res: any = await http.get(`/api/v1/qr-inspection/stalls/${token}/entry`)
    stall.value = res
    scenes.value = res?.scenes || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
})

function openQrInspection(s: any) {
  // 跳转扫码表单 H5（同页面渲染，通过参数传递 template + scene）
  window.location.href = `/warehouse/inspect?token=${token}&scene=${s.scene}&planId=${s.planId || ''}&taskId=${s.taskId || ''}`
}

function openClearance() { window.location.href = `/operation/clearance?token=${token}` }
function openProperty()  { window.location.href = `/operation/property?token=${token}` }
function openMaterial()  { window.location.href = `/operation/merchant/material?token=${token}` }
</script>

<style scoped>
.scan-page { padding: 16px; max-width: 480px; margin: 0 auto; }
.card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.stall-title { font-size: 17px; font-weight: 600; color: #1f2937; }
.stall-meta  { font-size: 13px; color: #6b7280; margin-top: 4px; }
.action-card { display: flex; align-items: center; gap: 12px; cursor: pointer; transition: background .15s; }
.action-card:active { background: #f3f4f6; }
.action-icon { font-size: 22px; width: 36px; text-align: center; }
.action-body { flex: 1; }
.action-title { font-size: 15px; font-weight: 600; color: #111827; }
.action-sub { font-size: 12px; color: #9ca3af; margin-top: 2px; }
.action-arrow { color: #d1d5db; font-size: 20px; }
.empty-card { text-align: center; color: #9ca3af; font-size: 14px; }
</style>
