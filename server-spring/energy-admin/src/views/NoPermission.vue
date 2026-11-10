<template>
  <div class="no-permission">
    <el-result icon="warning" title="无访问权限" :sub-title="subtitle">
      <template #extra>
        <el-button type="primary" @click="goBack">返回可访问页面</el-button>
        <el-button @click="switchAccount">切换账户</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { firstAccessiblePath } from '@/router/menu'

const router = useRouter()
const userStore = useUserStore()

const subtitle = computed(() => {
  const user = userStore.user
  const who = user?.displayName || user?.username || ''
  const dept = user?.departmentName ? `（${user.departmentName}）` : ''
  return `${who}${dept} 当前没有该页面的访问权限。如需使用，请联系管理员调整角色或部门。`
})

function goBack() {
  const target = firstAccessiblePath(c => userStore.hasPermission(c))
  if (target && target !== '/no-permission') router.replace(target)
  else switchAccount()
}

function switchAccount() {
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.no-permission {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
