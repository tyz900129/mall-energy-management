import type { Component } from 'vue'
import {
  Odometer, Tickets, OfficeBuilding, DataAnalysis, Share, Warning, Key,
  Management, Shop, Grid, ChatDotRound, Connection, Setting, Calendar
} from '@element-plus/icons-vue'

export interface MenuItem {
  path: string
  title: string
  permission?: string
  anyOf?: string[]
}

export interface MenuGroup {
  key: string
  title: string
  icon: Component
  /** 单页分组直接渲染为一级菜单项 */
  flat?: boolean
  anyOf?: string[]
  items: MenuItem[]
}

/** 菜单配置：可见性完全由权限码决定，MainLayout 不再逐条写 v-if */
export const MENU_GROUPS: MenuGroup[] = [
  {
    key: 'dashboard', title: '工作台', icon: Odometer, flat: true,
    items: [{ path: '/dashboard', title: '工作台', permission: 'dashboard.view' }]
  },
  {
    key: 'business-workflow', title: '业务流程', icon: Tickets,
    anyOf: ['workflow.view', 'workflow.create', 'construction.view', 'construction.create', 'construction.inspection.config.view', 'merchant.query'],
    items: [
      { path: '/workflow', title: '全部流程', permission: 'workflow.view' },
      { path: '/workflow/new-merchant', title: '水电费变更', permission: 'workflow.create' },
      { path: '/construction', title: '商户施工流程', permission: 'construction.view' },
      { path: '/construction/new', title: '发起施工申请', permission: 'construction.create' },
      { path: '/construction/inspection-config', title: '施工巡检配置', permission: 'construction.inspection.config.view' },
      { path: '/workflow/merchant-query', title: '商户账户查询', permission: 'merchant.query' }
    ]
  },
  {
    key: 'business-master-data', title: '经营基础资料', icon: OfficeBuilding,
    anyOf: ['merchant.menu', 'stall.menu', 'metering.menu'],
    items: [
      { path: '/merchant', title: '商户账户管理', permission: 'merchant.menu' },
      { path: '/stall', title: '铺位管理', permission: 'stall.menu' },
      { path: '/stall-device', title: '计量设备绑定', permission: 'metering.menu' }
    ]
  },
  {
    key: 'energy-management', title: '能源管理', icon: DataAnalysis,
    anyOf: ['energy.dashboard.view', 'energy.analysis.view', 'energy.dimension.view', 'energy.sync.view', 'energy.bill.view', 'energy.arrears.view', 'energy.anomaly.view', 'energy.balance-warning.view'],
    items: [
      { path: '/energy/dashboard', title: '数据大屏', permission: 'energy.dashboard.view' },
      { path: '/energy/analysis', title: '用量分析', permission: 'energy.analysis.view' },
      { path: '/energy/devices', title: '设备分析维度', permission: 'energy.dimension.view' },
      { path: '/energy/sync', title: '历史用量同步', permission: 'energy.sync.view' },
      { path: '/energy/bills', title: '水电账单录入', permission: 'energy.bill.view' },
      { path: '/energy/arrears', title: '欠费通知', permission: 'energy.arrears.view' },
      { path: '/energy/account-anomalies', title: '账户异动监控', permission: 'energy.anomaly.view' },
      { path: '/energy/balance-warnings', title: '余额预警通知', permission: 'energy.balance-warning.view' }
    ]
  },
  {
    key: 'facility-equipment', title: '设施设备', icon: Share,
    anyOf: ['facility.power.view', 'facility.water.view', 'facility.drainage.view', 'facility.exhaust.view', 'facility.fresh_air.view'],
    items: [
      { path: '/power/topology', title: '配电拓扑管理', permission: 'facility.power.view' },
      { path: '/facility/water-supply', title: '供水拓扑管理', permission: 'facility.water.view' },
      { path: '/facility/drainage', title: '排水拓扑管理', permission: 'facility.drainage.view' },
      { path: '/facility/oil-exhaust', title: '排油烟拓扑管理', permission: 'facility.exhaust.view' },
      { path: '/facility/fresh-air', title: '补风机拓扑管理', permission: 'facility.fresh_air.view' }
    ]
  },
  {
    key: 'operations-management', title: '运维管理', icon: Warning,
    anyOf: ['exception.view', 'inspection.dashboard.view', 'inspection.task.view', 'inspection.issue.manage', 'inspection.plan.manage', 'inspection.equipment.view', 'inspection.category.manage'],
    items: [
      { path: '/inspection/dashboard', title: '巡检数据大屏', permission: 'inspection.dashboard.view' },
      { path: '/inspection/tasks', title: '巡检任务', permission: 'inspection.task.view' },
      { path: '/inspection/equipment', title: '设施设备台账', permission: 'inspection.equipment.view' },
      { path: '/inspection/categories', title: '设施设备分类', permission: 'inspection.category.manage' },
      { path: '/inspection/issues', title: '问题与整改', permission: 'inspection.issue.manage' },
      { path: '/inspection/config', title: '巡检配置', permission: 'inspection.plan.manage' },
      { path: '/exception', title: '异常中心', permission: 'exception.view' }
    ]
  },
  {
    key: 'task-center', title: '任务中心', icon: Calendar,
    anyOf: ['calendar.view', 'calendar.manage', 'calendar.report'],
    items: [
      { path: '/calendar', title: '任务日历', permission: 'calendar.view' }
    ]
  },
  {
    key: 'access-management', title: '门禁与访客', icon: Key,
    anyOf: ['access.dashboard.view', 'access.self.view', 'access.device.view', 'access.person.view', 'visitor.view.own', 'visitor.view.all', 'visitor.create', 'visitor.approve', 'access.event.view.self', 'access.event.view.all', 'access.person.review'],
    items: [
      { path: '/access/dashboard', title: '数据概览', permission: 'access.dashboard.view' },
      { path: '/access/self', title: '我的门禁', permission: 'access.self.view' },
      { path: '/access/devices', title: '门禁点与设备', permission: 'access.device.view' },
      { path: '/access/persons', title: '长期人员', permission: 'access.person.view' },
      { path: '/access/visitors', title: '访客预约', anyOf: ['visitor.view.own', 'visitor.view.all', 'visitor.create', 'visitor.approve'] },
      { path: '/access/events', title: '通行记录', anyOf: ['access.event.view.self', 'access.event.view.all'] },
      { path: '/access/reviews', title: '人员复核', permission: 'access.person.review' }
    ]
  },
  {
    key: 'operation-business-management', title: '运营管理', icon: Management,
    anyOf: ['operation.merchant.view', 'operation.dashboard.view', 'operation.clearance.submit', 'operation.clearance.review', 'operation.property.view', 'operation.violation.view', 'operation.accountability.view'],
    items: [
      { path: '/operation/dashboard', title: '运营日清总览', permission: 'operation.dashboard.view' },
      { path: '/operation/merchant-responsibilities', title: '责任商管管理', permission: 'operation.merchant.view' },
      { path: '/operation/clearances', title: '商户日清记录', permission: 'operation.clearance.view' },
      { path: '/operation/clearance-templates', title: '日清表单模板', permission: 'operation.clearance.template.view' },
      { path: '/operation/property-inspections', title: '物业清场检查', permission: 'operation.property.view' },
      { path: '/operation/violation-items', title: '违规项目管理', permission: 'operation.violation.view' },
      { path: '/operation/accountability', title: '追责管理', permission: 'operation.accountability.view' }
    ]
  },
  {
    key: 'qr-inspection-management', title: '二维码巡检', icon: Grid,
    anyOf: ['qr.inspection.config', 'qr.inspection.plan.manage', 'qr.inspection.task.create', 'qr.inspection.view'],
    items: [
      { path: '/qr-inspection/templates', title: '巡检模板配置', permission: 'qr.inspection.config' },
      { path: '/qr-inspection/plans', title: '巡检计划管理', permission: 'qr.inspection.plan.manage' },
      { path: '/qr-inspection/joint-tasks', title: '联合巡检', anyOf: ['qr.inspection.task.create', 'qr.inspection.view'] },
      { path: '/qr-inspection/report', title: '巡检汇总报告', permission: 'qr.inspection.view' },
      { path: '/qr-inspection/records', title: '巡检记录管理', permission: 'qr.inspection.view' }
    ]
  },
  {
    key: 'patrol-management', title: '巡场管理', icon: Tickets,
    anyOf: ['patrol.point.view', 'patrol.issue.view'],
    items: [
      { path: '/patrol/entry', title: '今日巡场', permission: 'patrol.submit' },
      { path: '/patrol/issues', title: '巡场问题', permission: 'patrol.issue.view' },
      { path: '/patrol/submissions', title: '巡场记录', permission: 'patrol.issue.view' },
      { path: '/patrol/points', title: '巡场点位', permission: 'patrol.config' },
      { path: '/patrol/departments', title: '整改部门', permission: 'patrol.config' },
      { path: '/patrol/duties', title: '值班排班', permission: 'patrol.config' },
      { path: '/patrol/categories', title: '分类字典', permission: 'patrol.config' }
    ]
  },
  {
    key: 'service-management', title: '服务管理', icon: ChatDotRound,
    anyOf: ['complaint.view', 'complaint.create', 'complaint.stats.view'],
    items: [
      { path: '/complaints', title: '客诉管理', permission: 'complaint.view' },
      { path: '/complaints/dashboard', title: '客诉数据报表', permission: 'complaint.stats.view' },
      { path: '/complaints/staff', title: '客诉人员名单', permission: 'complaint.staff.manage' }
    ]
  },
  {
    key: 'ops-patrol-management', title: '营运巡场管理', icon: Shop,
    anyOf: ['ops.patrol.report'],
    items: [
      { path: '/ops-patrol/tasks', title: '任务看板', permission: 'ops.patrol.report' },
      { path: '/ops-patrol/issues', title: '营运问题', permission: 'ops.patrol.report' },
      { path: '/ops-patrol/handovers', title: '交接记录', permission: 'ops.patrol.report' },
      { path: '/ops-patrol/reports', title: '履职报表', permission: 'ops.patrol.report' },
      { path: '/ops-patrol/items', title: '检查项库', permission: 'ops.patrol.config' },
      { path: '/ops-patrol/duties', title: '干部排班', permission: 'ops.patrol.config' },
      { path: '/ops-patrol/slots', title: '时段设置', permission: 'ops.patrol.config' },
      { path: '/ops-patrol/depts', title: '处室与群配置', permission: 'ops.patrol.dept' }
    ]
  },
  {
    key: 'data-integration', title: '数据与集成', icon: Connection,
    anyOf: ['integration.config.view', 'integration.sync.view'],
    items: [
      { path: '/platform/config', title: '源平台配置', permission: 'integration.config.view' },
      { path: '/platform/sync', title: '数据同步与日志', permission: 'integration.sync.view' }
    ]
  },
  {
    key: 'system-management', title: '系统管理', icon: Setting,
    anyOf: ['system.user.view', 'system.dept.view', 'system.dictionary.view', 'system.webhook.view', 'system.audit.view', 'system.migration.manage'],
    items: [
      { path: '/system/user', title: '用户与权限', permission: 'system.user.view' },
      { path: '/system/organization', title: '组织架构', permission: 'system.dept.view' },
      { path: '/system/dictionary', title: '基础字典', permission: 'system.dictionary.view' },
      { path: '/system/webhook', title: 'Webhook 通知', permission: 'system.webhook.view' },
      { path: '/system/audit', title: '操作审计', permission: 'system.audit.view' },
      { path: '/system/migration', title: '数据迁移', permission: 'system.migration.manage' }
    ]
  }
]

export function itemAllowed(item: MenuItem, has: (code: string) => boolean): boolean {
  if (item.permission) return has(item.permission)
  if (item.anyOf) return item.anyOf.some(has)
  return true
}

export function visibleGroups(has: (code: string) => boolean): MenuGroup[] {
  return MENU_GROUPS
    .filter(g => !g.anyOf || g.anyOf.some(has))
    .map(g => ({ ...g, items: g.items.filter(i => itemAllowed(i, has)) }))
    .filter(g => g.items.length > 0)
}

/** 落地页：按菜单顺序取第一个有权限的页面 */
export function firstAccessiblePath(has: (code: string) => boolean): string {
  for (const group of MENU_GROUPS) {
    for (const item of group.items) {
      if (itemAllowed(item, has)) return item.path
    }
  }
  return '/no-permission'
}
