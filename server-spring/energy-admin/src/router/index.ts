import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/verify/:token', name: 'MobileVerification', component: () => import('@/views/MobileVerification.vue'), meta: { public: true, title: '现场设备核验' } },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台', icon: 'Odometer',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'workflow', name: 'WorkflowList', component: () => import('@/views/WorkflowList.vue'), meta: { title: '流程中心', icon: 'Tickets', roles:['SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER'] } },
      { path: 'workflow/new-merchant', name: 'WorkflowNewMerchant', component: () => import('@/views/WorkflowNewMerchant.vue'), meta: { title: '水电费变更', icon: 'CirclePlus', roles:['SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT'] } },
      { path: 'workflow/merchant-query', name: 'MerchantQuery', component: () => import('@/views/MerchantQuery.vue'), meta: { title: '商户账户查询', icon: 'Search', roles:['SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT'] } },
      { path: 'workflow/:id', name: 'WorkflowDetail', component: () => import('@/views/WorkflowDetail.vue'), meta: { title: '流程详情', icon: 'Document',roles:['SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','VIEWER'] } },
      { path: 'platform/config', name: 'PlatformConfig', component: () => import('@/views/PlatformConfig.vue'), meta: { title: '源平台配置', icon: 'Connection',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'platform/sync', name: 'PlatformSync', component: () => import('@/views/PlatformSync.vue'), meta: { title: '同步日志', icon: 'Refresh',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'platform/sync/log', name: 'PlatformSyncLog', component: () => import('@/views/PlatformSyncLog.vue'), meta: { title: '同步日志详情', icon: 'Document',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'energy/devices', name: 'DeviceGroup', component: () => import('@/views/DeviceGroup.vue'), meta: { title: '设备分析维度', roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'energy/sync', name: 'UsageSync', component: () => import('@/views/UsageSync.vue'), meta: { title: '历史用量同步', roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'energy/dashboard', name: 'UsageDashboard', component: () => import('@/views/UsageDashboard.vue'), meta: { title: '用量数据大屏', roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'energy/analysis', name: 'UsageAnalysis', component: () => import('@/views/UsageAnalysis.vue'), meta: { title: '用量分析', roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'power/topology', name: 'PowerTopology', component: () => import('@/views/PowerTopology.vue'), meta: { title: '配电拓扑管理', roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'exception', name: 'DataException', component: () => import('@/views/DataException.vue'), meta: { title: '异常中心', icon: 'Warning',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'merchant', name: 'Merchant', component: () => import('@/views/Merchant.vue'), meta: { title: '商户账户管理', icon: 'OfficeBuilding',roles:['SUPER_ADMIN','SYSTEM_ADMIN','DEVICE_VERIFIER','VIEWER'] } },
      { path: 'stall', name: 'Stall', component: () => import('@/views/Stall.vue'), meta: { title: '铺位管理', icon: 'Grid',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'stall/:id/engineering', name: 'StallEngineering', component: () => import('@/views/StallEngineering.vue'), meta: { title: '铺位工程资料', icon: 'Tools',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } },
      { path: 'stall-device', name: 'StallDevice', component: () => import('@/views/StallDevice.vue'), meta: { title: '设备绑定', icon: 'Link',roles:['SUPER_ADMIN','SYSTEM_ADMIN'] } },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/SystemUser.vue'), meta: { title: '用户管理', icon: 'User',roles:['SUPER_ADMIN'] } },
      { path: 'system/dictionary', name: 'BusinessDictionary', component: () => import('@/views/BusinessDictionary.vue'), meta: { title: '基础字典', icon: 'List',roles:['SUPER_ADMIN','SYSTEM_ADMIN'] } },
      { path: 'system/webhook', name: 'WebhookConfig', component: () => import('@/views/WebhookConfig.vue'), meta: { title: 'Webhook 通知', icon: 'Bell',roles:['SUPER_ADMIN','SYSTEM_ADMIN'] } },
      { path: 'system/audit', name: 'AuditLog', component: () => import('@/views/AuditLog.vue'), meta: { title: '操作审计', icon: 'DocumentChecked',roles:['SUPER_ADMIN','SYSTEM_ADMIN','VIEWER'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  const pageTitle = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = pageTitle ? `${pageTitle} - 万悦城后勤管理系统` : '万悦城后勤管理系统'
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) return next()
  if (!userStore.token) return next({ name: 'Login', query: { redirect: to.fullPath } })
  const allowed=to.meta.roles as string[]|undefined
  if(allowed&&!userStore.user?.roles?.some(r=>allowed.includes(r))){const roles=userStore.user?.roles||[];return next(roles.some(r=>['APPLICANT','DEVICE_VERIFIER'].includes(r))?'/workflow':'/dashboard')}
  next()
})

export default router
