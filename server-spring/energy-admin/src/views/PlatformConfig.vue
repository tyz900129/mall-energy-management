<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">源平台配置</div>
      <el-button type="primary" @click="onSave" :loading="saving">保存</el-button>
    </div>
    <el-card>
      <el-form :model="form" label-width="140px" style="max-width: 720px">
        <el-form-item label="平台名称">
          <el-input v-model="form.platformName" />
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input v-model="form.baseUrl" placeholder="https://app.leotechnology.cn" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.accountUsername" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="不修改请留空" />
        </el-form-item>
        <el-form-item label="空间 ID">
          <el-input v-model="form.spaceId" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="自动同步">
          <el-switch v-model="form.autoSync" />
        </el-form-item>
        <el-form-item label="同步 Cron">
          <el-input v-model="form.syncCron" placeholder="0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="连接超时(ms)">
          <el-input-number v-model="form.connectTimeoutMs" :min="1000" :step="1000" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi, type PlatformConfig } from '@/api/platform'

const form = reactive<any>({
  platformName: 'LeoEnergy', baseUrl: '', accountUsername: '', password: '',
  spaceId: '', enabled: false, autoSync: false, syncCron: '0 0 2 * * ?',
  connectTimeoutMs: 10000, remark: ''
})
const saving = ref(false)

async function load() {
  try {
    const c: any = await platformApi.getConfig()
    Object.assign(form, c, { password: '' })
  } catch (e) { /* ignore */ }
}

async function onSave() {
  saving.value = true
  try {
    await platformApi.updateConfig(form)
    ElMessage.success('保存成功')
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
