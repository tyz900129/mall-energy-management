<template>
  <el-container class="layout">
    <div v-if="isMobile && mobileMenuOpen" class="mobile-mask" @click="mobileMenuOpen = false" />
    <el-aside :width="isMobile ? '220px' : (collapsed ? '64px' : '220px')" class="aside" :class="{ 'mobile-aside': isMobile, 'mobile-open': mobileMenuOpen }">
      <div class="logo">
        <el-icon><Lightning /></el-icon>
        <span v-if="!collapsed">万悦城后勤管理系统</span>
      </div>
      <el-menu class="side-menu" :default-active="route.path" router :collapse="!isMobile && collapsed" background-color="#001529" text-color="#c9d1d9" active-text-color="#409EFF" @select="onMenuSelect">
        <template v-for="g in menuGroups" :key="g.key">
          <el-menu-item v-if="g.flat" :index="g.items[0].path">
            <el-icon><component :is="g.icon" /></el-icon>
            <template #title>{{ g.items[0].title }}</template>
          </el-menu-item>
          <el-sub-menu v-else :index="g.key">
            <template #title><el-icon><component :is="g.icon" /></el-icon><span>{{ g.title }}</span></template>
            <el-menu-item v-for="i in g.items" :key="i.path" :index="i.path">{{ i.title }}</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container class="content-container">
      <el-header class="header">
        <el-icon class="collapse-btn" @click="toggleMenu">
          <component :is="isMobile ? 'Menu' : (collapsed ? 'Expand' : 'Fold')" />
        </el-icon>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: homePath }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta?.title || route.name }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="spacer" />
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.user?.displayName || userStore.user?.username || '未登录' }}
            <el-tag v-if="userStore.user?.roles?.length" class="user-role" size="small" type="warning" effect="dark" style="margin-left: 8px">
              {{ enumLabel(userStore.user.roles[0]) }}
            </el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出并切换账户</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lightning, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { visibleGroups, firstAccessiblePath } from '@/router/menu'
import { enumLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const isMobile = ref(window.innerWidth <= 768)
const mobileMenuOpen = ref(false)
const can = (code: string) => userStore.hasPermission(code)
const menuGroups = computed(() => visibleGroups(can))
const homePath = computed(() => firstAccessiblePath(can))

onMounted(async () => {
  window.addEventListener('resize', onResize)
  if (userStore.token && !userStore.user) {
    try { await userStore.fetchMe() } catch { /* ignore */ }
  }
})
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

function onResize() {
  const mobile = window.innerWidth <= 768
  if (mobile !== isMobile.value) mobileMenuOpen.value = false
  isMobile.value = mobile
}
function toggleMenu() { if (isMobile.value) mobileMenuOpen.value = !mobileMenuOpen.value; else collapsed.value = !collapsed.value }
function onMenuSelect() { if (isMobile.value) mobileMenuOpen.value = false }

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    ElMessage.success('已退出，可使用其他账户登录')
    router.push({path:'/login',query:{switch:'1'}})
  }
}
</script>

<style scoped lang="scss">
.layout { height: 100vh; min-width: 0; }
.aside {
  height: 100%; min-height: 0; background: #001529; transition: width 0.2s;
  overflow: hidden; display: flex; flex-direction: column;
}
.content-container { min-width: 0; height: 100%; overflow: hidden; }
.logo {
  height: 56px; flex: 0 0 56px; display: flex; align-items: center; justify-content: center; gap: 8px;
  color: #fff; font-weight: 600; font-size: 16px; border-bottom: 1px solid #1f3a5f;
  i { color: #409EFF; font-size: 20px; }
}
.side-menu {
  flex: 1 1 auto; min-height: 0; border-right: 0; overflow-x: hidden; overflow-y: auto;
  overscroll-behavior: contain; scrollbar-width: thin; scrollbar-color: #36566b #001529;
}
.side-menu::-webkit-scrollbar { width: 6px; }
.side-menu::-webkit-scrollbar-track { background: #001529; }
.side-menu::-webkit-scrollbar-thumb { background: #36566b; border-radius: 6px; }
.header {
  background: #fff; display: flex; align-items: center; gap: 16px;
  border-bottom: 1px solid #e6e6e6; padding: 0 16px;
}
.collapse-btn { font-size: 20px; cursor: pointer; }
.spacer { flex: 1; }
.user { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.el-main { padding: 0; background: #f5f7fa; overflow-y: auto; -webkit-overflow-scrolling: touch; }
.mobile-mask { display: none; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 768px) {
  .layout { height: 100dvh; min-height: 100vh; }
  .mobile-aside {
    position: fixed; z-index: 2001; left: 0; top: 0; bottom: 0; height: 100vh; height: 100dvh;
    transform: translateX(-100%); transition: transform .22s ease; box-shadow: 6px 0 20px #0003;
  }
  .mobile-aside.mobile-open { transform: translateX(0); }
  .mobile-mask { display: block; position: fixed; z-index: 2000; inset: 0; background: #0006; }
  .header { height: 52px; padding: 0 12px; gap: 10px; }
  .header :deep(.el-breadcrumb) { display: none; }
  .collapse-btn { font-size: 23px; }
  .user { max-width: 190px; white-space: nowrap; }
  .user-role { display: none; }
}
</style>
