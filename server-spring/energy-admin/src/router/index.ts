import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/verify/:token', name: 'MobileVerification', component: () => import('@/views/MobileVerification.vue'), meta: { public: true, title: '现场设备核验' } },
  { path: '/construction-public/:token', name: 'ConstructionPublic', component: () => import('@/views/ConstructionPublic.vue'), meta: { public: true, title: '施工许可证与现场巡检' } },
  { path: '/access/visitor-apply', name: 'VisitorApply', component: () => import('@/views/VisitorApply.vue'), meta: { public: true, title: '访客门禁申请' } },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '工作台', permission:'dashboard.view' } },
      { path: 'workflow', name: 'WorkflowList', component: () => import('@/views/WorkflowList.vue'), meta: { title: '流程中心', permission:'workflow.view' } },
      { path: 'workflow/new-merchant', name: 'WorkflowNewMerchant', component: () => import('@/views/WorkflowNewMerchant.vue'), meta: { title: '水电费变更', permission:'workflow.create' } },
      { path: 'workflow/merchant-query', name: 'MerchantQuery', component: () => import('@/views/MerchantQuery.vue'), meta: { title: '商户账户查询', permission:'merchant.query' } },
      { path: 'workflow/:id', name: 'WorkflowDetail', component: () => import('@/views/WorkflowDetail.vue'), meta: { title: '流程详情', permission:'workflow.view' } },
      { path: 'construction', name: 'ConstructionWorkflowList', component: () => import('@/views/ConstructionWorkflowList.vue'), meta: { title: '商户施工流程', permission:'construction.view' } },
      { path: 'construction/new', name: 'ConstructionWorkflowForm', component: () => import('@/views/ConstructionWorkflowForm.vue'), meta: { title: '商户施工申请', permission:'construction.create' } },
      { path: 'construction/inspection-config', name: 'ConstructionInspectionConfig', component: () => import('@/views/ConstructionInspectionConfig.vue'), meta: { title: '施工巡检配置', permission:'construction.inspection.config.view' } },
      { path: 'construction/:id', name: 'ConstructionWorkflowDetail', component: () => import('@/views/ConstructionWorkflowDetail.vue'), meta: { title: '施工流程详情', permission:'construction.view' } },
      { path: 'construction/:id/permit', name: 'ConstructionPermit', component: () => import('@/views/ConstructionPermit.vue'), meta: { title: '商户施工许可证', permission:'construction.view' } },
      { path: 'platform/config', name: 'PlatformConfig', component: () => import('@/views/PlatformConfig.vue'), meta: { title: '源平台配置', permission:'integration.config.view' } },
      { path: 'platform/sync', name: 'PlatformSync', component: () => import('@/views/PlatformSync.vue'), meta: { title: '数据同步', permission:'integration.sync.view' } },
      { path: 'platform/sync/log', name: 'PlatformSyncLog', component: () => import('@/views/PlatformSyncLog.vue'), meta: { title: '同步日志详情', permission:'integration.sync.view' } },
      { path: 'energy/devices', name: 'DeviceGroup', component: () => import('@/views/DeviceGroup.vue'), meta: { title: '设备分析维度', permission:'energy.dimension.view' } },
      { path: 'energy/sync', name: 'UsageSync', component: () => import('@/views/UsageSync.vue'), meta: { title: '历史用量同步', permission:'energy.sync.view' } },
      { path: 'energy/bills', name: 'UtilityBill', component: () => import('@/views/UtilityBill.vue'), meta: { title: '水电账单录入', permission:'energy.bill.view' } },
      { path: 'energy/arrears', name: 'ArrearsNotification', component: () => import('@/views/ArrearsNotification.vue'), meta: { title: '欠费通知', permission:'energy.arrears.view' } },
      { path: 'energy/account-anomalies', name: 'AccountAnomalyMonitor', component: () => import('@/views/AccountAnomalyMonitor.vue'), meta: { title: '账户异动监控', permission:'energy.anomaly.view' } },
      { path: 'energy/dashboard', name: 'UsageDashboard', component: () => import('@/views/UsageDashboard.vue'), meta: { title: '用量数据大屏', permission:'energy.dashboard.view' } },
      { path: 'energy/analysis', name: 'UsageAnalysis', component: () => import('@/views/UsageAnalysis.vue'), meta: { title: '用量分析', permission:'energy.analysis.view' } },
      { path: 'power/topology', name: 'PowerTopology', component: () => import('@/views/PowerTopology.vue'), meta: { title: '配电拓扑管理', permission:'facility.power.view' } },
      { path: 'facility/water-supply', name: 'WaterSupplyTopology', component: () => import('@/views/FacilityTopology.vue'), props: { systemType: 'WATER_SUPPLY' }, meta: { title: '供水拓扑管理', permission:'facility.water.view' } },
      { path: 'facility/drainage', name: 'DrainageTopology', component: () => import('@/views/FacilityTopology.vue'), props: { systemType: 'DRAINAGE' }, meta: { title: '排水拓扑管理', permission:'facility.drainage.view' } },
      { path: 'facility/oil-exhaust', name: 'OilExhaustTopology', component: () => import('@/views/FacilityTopology.vue'), props: { systemType: 'OIL_EXHAUST' }, meta: { title: '排油烟拓扑管理', permission:'facility.exhaust.view' } },
      { path: 'facility/fresh-air', name: 'FreshAirTopology', component: () => import('@/views/FacilityTopology.vue'), props: { systemType: 'FRESH_AIR' }, meta: { title: '补风机拓扑管理', permission:'facility.fresh_air.view' } },
      { path: 'exception', name: 'DataException', component: () => import('@/views/DataException.vue'), meta: { title: '异常中心', permission:'exception.view' } },
      { path: 'inspection/dashboard', name: 'InspectionDashboard', component: () => import('@/views/InspectionDashboard.vue'), meta: { title: '巡检数据大屏', permission:'inspection.dashboard.view' } },
      { path: 'inspection/tasks', name: 'InspectionTasks', component: () => import('@/views/InspectionTasks.vue'), meta: { title: '巡检任务', permission:'inspection.task.view' } },
      { path: 'inspection/issues', name: 'InspectionIssues', component: () => import('@/views/InspectionIssues.vue'), meta: { title: '巡检问题整改', permission:'inspection.issue.manage' } },
      { path: 'inspection/config', name: 'InspectionManagement', component: () => import('@/views/InspectionManagement.vue'), meta: { title: '巡检配置', permission:'inspection.plan.manage' } },
      { path: 'inspection/equipment', name: 'InspectionEquipment', component: () => import('@/views/InspectionEquipment.vue'), meta: { title: '设施设备台账', permission:'inspection.equipment.view' } },
      { path: 'inspection/categories', name: 'InspectionCategories', component: () => import('@/views/InspectionCategories.vue'), meta: { title: '设施设备分类', permission:'inspection.category.manage' } },
      { path: 'inspection/equipment/:token', name: 'InspectionEquipmentScan', component: () => import('@/views/InspectionEquipmentScan.vue'), meta: { title: '扫码巡检', permission:'inspection.equipment.view' } },
      { path: 'access/dashboard', name: 'AccessDashboard', component: () => import('@/views/AccessControl.vue'), props: { section: 'dashboard' }, meta: { title: '门禁数据概览', permission:'access.dashboard.view' } },
      { path: 'access/self', name: 'AccessSelf', component: () => import('@/views/AccessControl.vue'), props: { section: 'self' }, meta: { title: '我的门禁', permission:'access.self.view' } },
      { path: 'access/devices', name: 'AccessDevices', component: () => import('@/views/AccessControl.vue'), props: { section: 'devices' }, meta: { title: '门禁设备', permission:'access.device.view' } },
      { path: 'access/persons', name: 'AccessPersons', component: () => import('@/views/AccessControl.vue'), props: { section: 'persons' }, meta: { title: '长期人员', permission:'access.person.view' } },
      { path: 'access/visitors', name: 'AccessVisitors', component: () => import('@/views/AccessControl.vue'), props: { section: 'visitors' }, meta: { title: '访客预约', permission:'visitor.view.own' } },
      { path: 'access/events', name: 'AccessEvents', component: () => import('@/views/AccessControl.vue'), props: { section: 'events' }, meta: { title: '通行记录', permission:'access.event.view.self' } },
      { path: 'access/reviews', name: 'AccessReviews', component: () => import('@/views/AccessControl.vue'), props: { section: 'reviews' }, meta: { title: '人员复核', permission:'access.person.review' } },
      { path: 'merchant', name: 'Merchant', component: () => import('@/views/Merchant.vue'), meta: { title: '商户账户管理', permission:'merchant.menu' } },
      { path: 'stall', name: 'Stall', component: () => import('@/views/Stall.vue'), meta: { title: '铺位管理', permission:'stall.menu' } },
      { path: 'stall/:id/engineering', name: 'StallEngineering', component: () => import('@/views/StallEngineering.vue'), meta: { title: '铺位工程资料', permission:'stall.engineering.view' } },
      { path: 'stall-device', name: 'StallDevice', component: () => import('@/views/StallDevice.vue'), meta: { title: '设备绑定', permission:'metering.menu' } },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/SystemUser.vue'), meta: { title: '用户与权限', permission:'system.user.view' } },
      { path: 'system/dictionary', name: 'BusinessDictionary', component: () => import('@/views/BusinessDictionary.vue'), meta: { title: '基础字典', permission:'system.dictionary.view' } },
      { path: 'system/webhook', name: 'WebhookConfig', component: () => import('@/views/WebhookConfig.vue'), meta: { title: 'Webhook 通知', permission:'system.webhook.view' } },
      { path: 'system/audit', name: 'AuditLog', component: () => import('@/views/AuditLog.vue'), meta: { title: '操作审计', permission:'system.audit.view' } },
      { path: 'system/migration', name: 'SystemMigration', component: () => import('@/views/SystemMigration.vue'), meta: { title: '数据迁移', permission:'system.migration.manage' } }
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

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) return next()
  if (!userStore.token) return next({ name: 'Login', query: { redirect: to.fullPath } })
  try { await userStore.fetchMe() } catch { userStore.logout(); return next({ name: 'Login' }) }
  const permission=to.meta.permission as string|undefined
  if(permission&&!userStore.hasPermission(permission)){
    const fallback=userStore.hasPermission('dashboard.view')?'/dashboard':userStore.hasPermission('access.self.view')?'/access/self':userStore.hasPermission('workflow.view')?'/workflow':userStore.hasPermission('construction.view')?'/construction':userStore.hasPermission('merchant.query')?'/workflow/merchant-query':'/login'
    return next(fallback)
  }
  next()
})

export default router
