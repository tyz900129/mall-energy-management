<template>
  <el-container class="layout">
    <div v-if="isMobile && mobileMenuOpen" class="mobile-mask" @click="mobileMenuOpen = false" />
    <el-aside :width="isMobile ? '220px' : (collapsed ? '64px' : '220px')" class="aside" :class="{ 'mobile-aside': isMobile, 'mobile-open': mobileMenuOpen }">
      <div class="logo">
        <el-icon><Lightning /></el-icon>
        <span v-if="!collapsed">万悦城后勤管理系统</span>
      </div>
      <el-menu class="side-menu" :default-active="route.path" router :collapse="!isMobile && collapsed" background-color="#001529" text-color="#c9d1d9" active-text-color="#409EFF" @select="onMenuSelect">
        <el-menu-item v-if="can('dashboard.view')" index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-sub-menu v-if="hasAny('workflow.view','workflow.create','construction.view','construction.create','construction.inspection.config.view','merchant.query')" index="business-workflow">
          <template #title><el-icon><Tickets /></el-icon><span>业务流程</span></template>
          <el-menu-item v-if="can('workflow.view')" index="/workflow">全部流程</el-menu-item>
          <el-menu-item v-if="can('workflow.create')" index="/workflow/new-merchant">水电费变更</el-menu-item>
          <el-menu-item v-if="can('construction.view')" index="/construction">商户施工流程</el-menu-item>
          <el-menu-item v-if="can('construction.create')" index="/construction/new">发起施工申请</el-menu-item>
          <el-menu-item v-if="can('construction.inspection.config.view')" index="/construction/inspection-config">施工巡检配置</el-menu-item>
          <el-menu-item v-if="can('merchant.query')" index="/workflow/merchant-query">商户账户查询</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('merchant.menu','stall.menu','metering.menu')" index="business-master-data">
          <template #title><el-icon><OfficeBuilding /></el-icon><span>经营基础资料</span></template>
          <el-menu-item v-if="can('merchant.menu')" index="/merchant">商户账户管理</el-menu-item>
          <el-menu-item v-if="can('stall.menu')" index="/stall">铺位管理</el-menu-item>
          <el-menu-item v-if="can('metering.menu')" index="/stall-device">计量设备绑定</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('energy.dashboard.view','energy.analysis.view','energy.dimension.view','energy.sync.view','energy.bill.view','energy.arrears.view','energy.anomaly.view','energy.balance-warning.view')" index="energy-management">
          <template #title><el-icon><DataAnalysis /></el-icon><span>能源管理</span></template>
          <el-menu-item v-if="can('energy.dashboard.view')" index="/energy/dashboard">数据大屏</el-menu-item>
          <el-menu-item v-if="can('energy.analysis.view')" index="/energy/analysis">用量分析</el-menu-item>
          <el-menu-item v-if="can('energy.dimension.view')" index="/energy/devices">设备分析维度</el-menu-item>
          <el-menu-item v-if="can('energy.sync.view')" index="/energy/sync">历史用量同步</el-menu-item>
          <el-menu-item v-if="can('energy.bill.view')" index="/energy/bills">水电账单录入</el-menu-item>
          <el-menu-item v-if="can('energy.arrears.view')" index="/energy/arrears">欠费通知</el-menu-item>
          <el-menu-item v-if="can('energy.anomaly.view')" index="/energy/account-anomalies">账户异动监控</el-menu-item>
          <el-menu-item v-if="can('energy.balance-warning.view')" index="/energy/balance-warnings">余额预警通知</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('facility.power.view','facility.water.view','facility.drainage.view','facility.exhaust.view','facility.fresh_air.view')" index="facility-equipment">
          <template #title><el-icon><Share /></el-icon><span>设施设备</span></template>
          <el-menu-item v-if="can('facility.power.view')" index="/power/topology">配电拓扑管理</el-menu-item>
          <el-menu-item v-if="can('facility.water.view')" index="/facility/water-supply">供水拓扑管理</el-menu-item>
          <el-menu-item v-if="can('facility.drainage.view')" index="/facility/drainage">排水拓扑管理</el-menu-item>
          <el-menu-item v-if="can('facility.exhaust.view')" index="/facility/oil-exhaust">排油烟拓扑管理</el-menu-item>
          <el-menu-item v-if="can('facility.fresh_air.view')" index="/facility/fresh-air">补风机拓扑管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('exception.view','inspection.dashboard.view','inspection.task.view','inspection.issue.manage','inspection.plan.manage','inspection.equipment.view','inspection.category.manage')" index="operations-management">
          <template #title><el-icon><Warning /></el-icon><span>运维管理</span></template>
          <el-menu-item v-if="can('inspection.dashboard.view')" index="/inspection/dashboard">巡检数据大屏</el-menu-item>
          <el-menu-item v-if="can('inspection.task.view')" index="/inspection/tasks">巡检任务</el-menu-item>
          <el-menu-item v-if="can('inspection.equipment.view')" index="/inspection/equipment">设施设备台账</el-menu-item>
          <el-menu-item v-if="can('inspection.category.manage')" index="/inspection/categories">设施设备分类</el-menu-item>
          <el-menu-item v-if="can('inspection.issue.manage')" index="/inspection/issues">问题与整改</el-menu-item>
          <el-menu-item v-if="can('inspection.plan.manage')" index="/inspection/config">巡检配置</el-menu-item>
          <el-menu-item v-if="can('exception.view')" index="/exception">异常中心</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('access.dashboard.view','access.self.view','access.device.view','access.person.view','visitor.view.own','visitor.view.all','visitor.create','visitor.approve','access.event.view.self','access.event.view.all','access.person.review')" index="access-management">
          <template #title><el-icon><Key /></el-icon><span>门禁与访客</span></template>
          <el-menu-item v-if="can('access.dashboard.view')" index="/access/dashboard">数据概览</el-menu-item>
          <el-menu-item v-if="can('access.self.view')" index="/access/self">我的门禁</el-menu-item>
          <el-menu-item v-if="can('access.device.view')" index="/access/devices">门禁点与设备</el-menu-item>
          <el-menu-item v-if="can('access.person.view')" index="/access/persons">长期人员</el-menu-item>
          <el-menu-item v-if="hasAny('visitor.view.own','visitor.view.all','visitor.create','visitor.approve')" index="/access/visitors">访客预约</el-menu-item>
          <el-menu-item v-if="hasAny('access.event.view.self','access.event.view.all')" index="/access/events">通行记录</el-menu-item>
          <el-menu-item v-if="can('access.person.review')" index="/access/reviews">人员复核</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('operation.merchant.view','operation.dashboard.view','operation.clearance.submit','operation.clearance.review','operation.property.view','operation.accountability.view')" index="operation-business-management">
          <template #title><el-icon><Management /></el-icon><span>运营管理</span></template>
          <el-menu-item v-if="can('operation.dashboard.view')" index="/operation/dashboard">运营日清总览</el-menu-item>
          <el-menu-item v-if="can('operation.merchant.view')" index="/operation/merchant-responsibilities">责任商管管理</el-menu-item>
          <el-menu-item v-if="can('operation.clearance.view')" index="/operation/clearances">商户日清记录</el-menu-item>
          <el-menu-item v-if="can('operation.clearance.template.view')" index="/operation/clearance-templates">日清表单模板</el-menu-item>
          <el-menu-item v-if="can('operation.property.view')" index="/operation/property-inspections">物业清场检查</el-menu-item>
          <el-menu-item v-if="can('operation.property.template.view')" index="/operation/property-inspection-templates">物业检查模板</el-menu-item>
          <el-menu-item v-if="can('operation.accountability.view')" index="/operation/accountability">追责管理</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('integration.config.view','integration.sync.view')" index="data-integration">
          <template #title><el-icon><Connection /></el-icon><span>数据与集成</span></template>
          <el-menu-item v-if="can('integration.config.view')" index="/platform/config">源平台配置</el-menu-item>
          <el-menu-item v-if="can('integration.sync.view')" index="/platform/sync">数据同步与日志</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="hasAny('system.user.view','system.dictionary.view','system.webhook.view','system.audit.view','system.migration.manage')" index="system-management">
          <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
          <el-menu-item v-if="can('system.user.view')" index="/system/user">用户与权限</el-menu-item>
          <el-menu-item v-if="can('system.dictionary.view')" index="/system/dictionary">基础字典</el-menu-item>
          <el-menu-item v-if="can('system.webhook.view')" index="/system/webhook">Webhook 通知</el-menu-item>
          <el-menu-item v-if="can('system.audit.view')" index="/system/audit">操作审计</el-menu-item>
          <el-menu-item v-if="can('system.migration.manage')" index="/system/migration">数据迁移</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="content-container">
      <el-header class="header">
        <el-icon class="collapse-btn" @click="toggleMenu">
          <component :is="isMobile ? 'Menu' : (collapsed ? 'Expand' : 'Fold')" />
        </el-icon>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Lightning, Odometer, Connection, OfficeBuilding, UserFilled, Expand, Fold, Warning, Tickets, DataAnalysis, Share, Setting, Key, Management
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { enumLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const isMobile = ref(window.innerWidth <= 768)
const mobileMenuOpen = ref(false)
const can=(code:string)=>userStore.hasPermission(code)
const hasAny=(...codes:string[])=>codes.some(can)

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
