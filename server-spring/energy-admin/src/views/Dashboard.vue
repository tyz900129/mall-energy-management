<template>
  <div class="workbench" v-loading="loading">
    <section class="welcome-panel">
      <div>
        <div class="eyebrow">万悦城后勤管理系统</div>
        <h1>{{ greeting }}，{{ userStore.user?.displayName || userStore.user?.username || '欢迎回来' }}</h1>
        <p>从这里快速查看关键数据，进入您有权限使用的业务模块。</p>
      </div>
      <div class="welcome-actions">
        <div class="today">{{ todayText }}</div>
        <el-button :icon="Refresh" circle aria-label="刷新工作台" @click="load" />
      </div>
    </section>

    <section v-if="canReadBaseStats" class="section-block">
      <div class="section-heading">
        <div><h2>经营概览</h2><span>点击指标可直接进入对应管理页面</span></div>
        <span class="updated" v-if="lastUpdated">更新于 {{ lastUpdated }}</span>
      </div>
      <div class="metric-grid">
        <button v-for="item in metrics" :key="item.key" class="metric-card" :class="item.tone" :disabled="!item.path" @click="navigate(item.path)">
          <div class="metric-top"><span>{{ item.label }}</span><el-icon><component :is="item.icon" /></el-icon></div>
          <strong>{{ formatNumber(item.value) }}</strong>
          <div class="metric-foot"><span>{{ item.hint }}</span><el-icon v-if="item.path"><ArrowRight /></el-icon></div>
        </button>
      </div>
    </section>

    <section v-if="hasModuleIndicators" class="section-block">
      <div class="section-heading"><div><h2>待办与运行情况</h2><span>只显示您有权限查看的模块摘要</span></div></div>
      <div class="indicator-grid">
        <button v-if="can('workflow.view')" class="indicator-card" @click="navigate('/workflow')">
          <div class="indicator-title"><el-icon><Tickets /></el-icon><b>水电费变更流程</b><el-icon class="indicator-arrow"><ArrowRight /></el-icon></div>
          <div class="indicator-values">
            <span><strong>{{ workflowPending }}</strong><small>待跟进</small></span>
            <span><strong>{{ stats.monthlySubmittedWorkflows }}</strong><small>本月提交</small></span>
          </div>
        </button>
        <button v-if="can('inspection.dashboard.view')" class="indicator-card" @click="navigate('/inspection/dashboard')">
          <div class="indicator-title"><el-icon><Tools /></el-icon><b>设备巡检</b><el-icon class="indicator-arrow"><ArrowRight /></el-icon></div>
          <div class="indicator-values">
            <span><strong>{{ inspectionSummary.pending ?? 0 }}</strong><small>待巡检</small></span>
            <span class="warning"><strong>{{ inspectionSummary.overdue ?? 0 }}</strong><small>已逾期</small></span>
            <span class="success"><strong>{{ inspectionSummary.completed ?? 0 }}</strong><small>本月完成</small></span>
          </div>
        </button>
        <button v-if="can('access.dashboard.view')" class="indicator-card" @click="navigate('/access/dashboard')">
          <div class="indicator-title"><el-icon><Key /></el-icon><b>门禁与访客</b><el-icon class="indicator-arrow"><ArrowRight /></el-icon></div>
          <div class="indicator-values">
            <span><strong>{{ accessSummary.pendingApproval ?? 0 }}</strong><small>待审批</small></span>
            <span class="warning"><strong>{{ accessSummary.expiring30Days ?? 0 }}</strong><small>30天内到期</small></span>
            <span><strong>{{ accessSummary.todayEvents ?? 0 }}</strong><small>今日通行</small></span>
          </div>
        </button>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><h2>功能入口</h2><span>按业务分类整理，点击即可打开对应模块</span></div></div>
      <div class="module-grid">
        <section v-for="group in visibleGroups" :key="group.title" class="module-group">
          <div class="group-heading"><span class="group-icon" :class="group.tone"><el-icon><component :is="group.icon" /></el-icon></span><div><b>{{ group.title }}</b><small>{{ group.description }}</small></div></div>
          <button v-for="entry in group.entries" :key="entry.path" class="module-link" @click="navigate(entry.path)">
            <span><b>{{ entry.title }}</b><small>{{ entry.description }}</small></span><el-icon><ArrowRight /></el-icon>
          </button>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, Refresh, OfficeBuilding, Grid, House, Link, DocumentChecked,
  Tickets, Tools, Key, DataAnalysis, Share, Management, Warning, Setting, Connection
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { dashboardApi, type DashboardStats } from '@/api/dashboard'
import { workflowApi } from '@/api/workflow'
import { inspectionApi } from '@/api/inspection'
import { accessControlApi } from '@/api/accessControl'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const stats = ref<DashboardStats>({ billingAccounts: 0, stalls: 0, warehouses: 0, boundStalls: 0, monthlySubmittedWorkflows: 0 })
const workflowPending = ref(0)
const inspectionSummary = ref<Record<string, number>>({})
const accessSummary = ref<Record<string, number>>({})
const lastUpdated = ref('')
const can = (permission: string) => userStore.hasPermission(permission)
const roles = computed(() => userStore.user?.roles || [])
const canReadBaseStats = computed(() => roles.value.some(role => ['SUPER_ADMIN', 'SYSTEM_ADMIN'].includes(role)))
const hasModuleIndicators = computed(() => can('workflow.view') || can('inspection.dashboard.view') || can('access.dashboard.view'))
const todayText = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const greeting = (() => { const hour = new Date().getHours(); return hour < 11 ? '早上好' : hour < 13 ? '中午好' : hour < 18 ? '下午好' : '晚上好' })()

const metrics = computed(() => [
  { key: 'billingAccounts', label: '启用计费账户', value: stats.value.billingAccounts, hint: '查看商户账户', path: can('merchant.menu') ? '/merchant' : '', icon: OfficeBuilding, tone: 'blue' },
  { key: 'stalls', label: '铺位总数', value: stats.value.stalls, hint: '维护铺位资料', path: can('stall.menu') ? '/stall' : '', icon: Grid, tone: 'green' },
  { key: 'warehouses', label: '仓库数量', value: stats.value.warehouses, hint: '查看仓库铺位', path: can('stall.menu') ? '/stall' : '', icon: House, tone: 'violet' },
  { key: 'boundStalls', label: '已绑定设备铺位', value: stats.value.boundStalls, hint: '管理计量设备绑定', path: can('metering.menu') ? '/stall-device' : '', icon: Link, tone: 'orange' },
  { key: 'monthlySubmittedWorkflows', label: '本月提交流程', value: stats.value.monthlySubmittedWorkflows, hint: '进入流程中心', path: can('workflow.view') ? '/workflow' : '', icon: DocumentChecked, tone: 'rose' }
])

const groups = [
  { title: '业务流程', description: '开户变更、施工与商户查询', icon: Tickets, tone: 'blue', entries: [
    { permission: 'workflow.view', title: '水电费变更流程', description: '开户、增铺、撤柜与状态跟进', path: '/workflow' },
    { permission: 'workflow.create', title: '发起水电费变更', description: '提交新商户开户或铺位变更', path: '/workflow/new-merchant' },
    { permission: 'construction.view', title: '商户施工流程', description: '施工申请、现场巡检与验收', path: '/construction' },
    { permission: 'construction.create', title: '发起施工申请', description: '为铺位办理入场或撤场施工', path: '/construction/new' },
    { permission: 'construction.inspection.config.view', title: '施工巡检配置', description: '维护施工现场巡检项目', path: '/construction/inspection-config' },
    { permission: 'merchant.query', title: '商户账户查询', description: '通过商户代码查询基础信息', path: '/workflow/merchant-query' }
  ] },
  { title: '经营基础资料', description: '商户、铺位和计量设备', icon: OfficeBuilding, tone: 'green', entries: [
    { permission: 'merchant.menu', title: '商户账户管理', description: '账户资料与商户代码维护', path: '/merchant' },
    { permission: 'stall.menu', title: '铺位管理', description: '铺位状态、业态和工程资料', path: '/stall' },
    { permission: 'metering.menu', title: '计量设备绑定', description: '维护铺位与水电表绑定关系', path: '/stall-device' }
  ] },
  { title: '能源管理', description: '用量、账单与费用预警', icon: DataAnalysis, tone: 'orange', entries: [
    { permission: 'energy.dashboard.view', title: '用量数据大屏', description: '用量趋势、同比和维度占比', path: '/energy/dashboard' },
    { permission: 'energy.analysis.view', title: '用量分析', description: '按设备和业务维度分析用量', path: '/energy/analysis' },
    { permission: 'energy.dimension.view', title: '设备分析维度', description: '维护用量统计分类与设备归属', path: '/energy/devices' },
    { permission: 'energy.sync.view', title: '历史用量同步', description: '查看月度和每日数据采集记录', path: '/energy/sync' },
    { permission: 'energy.bill.view', title: '水电账单录入', description: '维护账单金额和用量', path: '/energy/bills' },
    { permission: 'energy.arrears.view', title: '欠费通知', description: '管理欠费账户及推送排除项', path: '/energy/arrears' },
    { permission: 'energy.anomaly.view', title: '账户异动监控', description: '查看变化率走势和异常排行', path: '/energy/account-anomalies' },
    { permission: 'energy.balance-warning.view', title: '余额预警通知', description: '关注账户余额可用天数', path: '/energy/balance-warnings' }
  ] },
  { title: '设施与运维', description: '拓扑、设备台账和巡检整改', icon: Share, tone: 'violet', entries: [
    { permission: 'facility.power.view', title: '配电拓扑管理', description: '查看配电层级与节点关系', path: '/power/topology' },
    { permission: 'facility.water.view', title: '供水拓扑管理', description: '查看供水系统拓扑', path: '/facility/water-supply' },
    { permission: 'facility.drainage.view', title: '排水拓扑管理', description: '查看排水系统拓扑', path: '/facility/drainage' },
    { permission: 'facility.exhaust.view', title: '排油烟拓扑管理', description: '查看排油烟系统拓扑', path: '/facility/oil-exhaust' },
    { permission: 'facility.fresh_air.view', title: '补风机拓扑管理', description: '查看补风系统拓扑', path: '/facility/fresh-air' },
    { permission: 'inspection.dashboard.view', title: '巡检数据大屏', description: '掌握巡检任务、逾期和整改情况', path: '/inspection/dashboard' },
    { permission: 'inspection.task.view', title: '巡检任务', description: '查看设备巡检任务与结果', path: '/inspection/tasks' },
    { permission: 'inspection.equipment.view', title: '设施设备台账', description: '维护设备资料并生成扫码入口', path: '/inspection/equipment' },
    { permission: 'inspection.category.manage', title: '设施设备分类', description: '维护设备类别和关联表单', path: '/inspection/categories' },
    { permission: 'inspection.plan.manage', title: '巡检配置', description: '维护巡检类型、表单和计划', path: '/inspection/config' },
    { permission: 'inspection.issue.manage', title: '问题与整改', description: '跟进巡检发现的问题', path: '/inspection/issues' },
    { permission: 'exception.view', title: '异常中心', description: '查看系统同步及业务告警', path: '/exception' }
  ] },
  { title: '门禁与访客', description: '人员、预约审批和通行记录', icon: Key, tone: 'rose', entries: [
    { permission: 'access.dashboard.view', title: '门禁数据概览', description: '查看设备、人员和通行数据', path: '/access/dashboard' },
    { permission: 'access.self.view', title: '我的门禁', description: '认领个人档案并查看预约记录', path: '/access/self' },
    { permission: 'access.device.view', title: '门禁点与设备', description: '查看入口、出口设备状态', path: '/access/devices' },
    { permission: 'access.person.view', title: '长期人员', description: '维护长期员工门禁资料', path: '/access/persons' },
    { permission: 'visitor.view.own', title: '访客预约', description: '提交和跟进访客预约申请', path: '/access/visitors' },
    { permission: 'access.event.view.self', title: '通行记录', description: '查询授权范围内的通行记录', path: '/access/events' },
    { permission: 'access.person.review', title: '人员复核', description: '处理到期或长期未通行人员', path: '/access/reviews' }
  ] },
  { title: '运营管理', description: '商户日清、物业检查和追责', icon: Management, tone: 'teal', entries: [
    { permission: 'operation.dashboard.view', title: '运营日清总览', description: '查看日清完成率和异常情况', path: '/operation/dashboard' },
    { permission: 'operation.merchant.view', title: '责任商管管理', description: '维护商户与责任商管关系', path: '/operation/merchant-responsibilities' },
    { permission: 'operation.clearance.view', title: '商户日清记录', description: '查看铺位日清提交和记录', path: '/operation/clearances' },
    { permission: 'operation.clearance.template.view', title: '日清表单模板', description: '配置不同业态的商户日清表单', path: '/operation/clearance-templates' },
    { permission: 'operation.property.view', title: '物业清场检查', description: '查看物业现场检查记录', path: '/operation/property-inspections' },
    { permission: 'operation.violation.view', title: '违规项目管理', description: '维护检查问题和处理措施', path: '/operation/violation-items' },
    { permission: 'operation.accountability.view', title: '追责管理', description: '处理违规事项并生成追责单', path: '/operation/accountability' }
  ] },
  { title: '数据与系统', description: '数据同步与系统配置', icon: Connection, tone: 'slate', entries: [
    { permission: 'integration.sync.view', title: '数据同步与日志', description: '查看同步批次及运行记录', path: '/platform/sync' },
    { permission: 'system.user.view', title: '用户与权限', description: '管理账号和人员级权限', path: '/system/user' },
    { permission: 'system.dictionary.view', title: '基础字典', description: '维护楼层、业态及配置选项', path: '/system/dictionary' },
    { permission: 'system.webhook.view', title: 'Webhook 通知', description: '配置各类消息目标与事件', path: '/system/webhook' },
    { permission: 'system.audit.view', title: '操作审计', description: '查看系统关键操作记录', path: '/system/audit' },
    { permission: 'system.migration.manage', title: '数据迁移', description: '导出或导入系统资料', path: '/system/migration' }
  ] }
]
const visibleGroups = computed(() => groups.map(group => ({ ...group, entries: group.entries.filter(entry => can(entry.permission)) })).filter(group => group.entries.length))

function navigate(path: string) { if (path) router.push(path) }
function formatNumber(value?: number) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
async function load() {
  loading.value = true
  const requests: Promise<unknown>[] = []
  if (canReadBaseStats.value) requests.push(dashboardApi.stats().then((result: any) => { stats.value = result }))
  if (can('workflow.view')) {
    const pendingStatuses = ['FIELD_CHECK_PENDING', 'WAITING_OA_SUBMISSION', 'WAITING_PLATFORM_CHANGE', 'EXCEPTION']
    requests.push(Promise.all(pendingStatuses.map(status => workflowApi.list({ pageNum: 1, pageSize: 1, status }))).then((pages: any[]) => {
      workflowPending.value = pages.reduce((sum, page) => sum + Number(page?.total || 0), 0)
    }))
  }
  if (can('inspection.dashboard.view')) requests.push(inspectionApi.dashboard().then((result: any) => { inspectionSummary.value = result?.summary || {} }))
  if (can('access.dashboard.view')) requests.push(accessControlApi.dashboard().then((result: any) => { accessSummary.value = result || {} }))
  await Promise.allSettled(requests)
  lastUpdated.value = formatDateTime(new Date().toISOString())
  loading.value = false
}

onMounted(load)
</script>

<style scoped lang="scss">
.workbench{max-width:1600px;margin:0 auto;padding:24px;min-height:100%;color:#1f2937}.welcome-panel{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:28px 32px;margin-bottom:24px;border-radius:16px;color:#fff;background:linear-gradient(118deg,#123c73 0%,#1769aa 58%,#2787c8 100%);box-shadow:0 12px 28px rgba(27,87,143,.16)}.eyebrow{font-size:12px;letter-spacing:1.5px;color:#c5e4ff}.welcome-panel h1{margin:8px 0;font-size:26px;line-height:1.3}.welcome-panel p{margin:0;color:#dbeafe;font-size:14px}.welcome-actions{display:flex;align-items:center;gap:14px;flex-shrink:0}.today{color:#e5f2ff;font-size:14px}.welcome-actions :deep(.el-button){color:#fff;background:#ffffff1c;border-color:#ffffff5c}.section-block{margin:0 0 25px}.section-heading{display:flex;justify-content:space-between;align-items:end;margin-bottom:13px}.section-heading h2{margin:0 0 4px;font-size:18px}.section-heading span{color:#8a95a5;font-size:12px}.section-heading .updated{white-space:nowrap}.metric-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px}.metric-card{display:flex;flex-direction:column;gap:12px;min-width:0;padding:17px 18px;text-align:left;border:1px solid #e8edf3;border-radius:12px;background:#fff;box-shadow:0 3px 12px rgba(25,42,70,.035);cursor:pointer;transition:transform .15s,box-shadow .15s,border-color .15s}.metric-card:hover{transform:translateY(-2px);box-shadow:0 8px 20px rgba(25,42,70,.09);border-color:#c9d9ec}.metric-card:disabled{cursor:default;opacity:.78}.metric-top,.metric-foot{display:flex;align-items:center;justify-content:space-between;gap:8px}.metric-top{color:#64748b;font-size:13px}.metric-top .el-icon{font-size:19px}.metric-card strong{font-size:29px;line-height:1.1;color:#172b4d}.metric-foot{color:#98a3b3;font-size:12px}.blue .metric-top .el-icon{color:#3478d4}.green .metric-top .el-icon{color:#16a36a}.violet .metric-top .el-icon{color:#8458d8}.orange .metric-top .el-icon{color:#e49a21}.rose .metric-top .el-icon{color:#e05d77}.indicator-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.indicator-card{min-width:0;padding:16px 18px;text-align:left;border:1px solid #e8edf3;border-radius:12px;background:#fff;cursor:pointer;transition:box-shadow .15s,border-color .15s}.indicator-card:hover{border-color:#b8d2ee;box-shadow:0 6px 18px rgba(25,42,70,.07)}.indicator-title{display:flex;align-items:center;gap:9px;font-size:14px;color:#334155}.indicator-title>.el-icon:first-child{font-size:18px;color:#4384cb}.indicator-arrow{margin-left:auto;color:#94a3b8}.indicator-values{display:flex;gap:26px;margin-top:16px}.indicator-values span{display:flex;flex-direction:column;gap:4px}.indicator-values strong{font-size:23px;line-height:1;color:#1d3557}.indicator-values small{font-size:11px;color:#8995a5}.indicator-values .warning strong{color:#db8a1e}.indicator-values .success strong{color:#15976a}.module-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.module-group{min-width:0;padding:17px 18px 8px;border:1px solid #e8edf3;border-radius:12px;background:#fff}.group-heading{display:flex;align-items:center;gap:11px;padding-bottom:12px;border-bottom:1px solid #f0f2f5}.group-heading>div{display:flex;flex-direction:column;gap:4px}.group-heading b{font-size:14px}.group-heading small{font-size:11px;color:#97a1af}.group-icon{width:34px;height:34px;display:grid;place-items:center;border-radius:10px;background:#eef5ff;color:#3478d4;font-size:17px}.group-icon.green{background:#eaf8f1;color:#169663}.group-icon.orange{background:#fff5e7;color:#d98c17}.group-icon.violet{background:#f3efff;color:#8054d1}.group-icon.rose{background:#fff0f2;color:#d8506d}.group-icon.teal{background:#e8f8f6;color:#12877b}.group-icon.slate{background:#f0f3f7;color:#64748b}.module-link{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;padding:11px 1px;text-align:left;border:0;border-bottom:1px solid #f3f5f7;background:transparent;cursor:pointer}.module-link:last-child{border-bottom:0}.module-link>span{display:flex;flex-direction:column;gap:4px;min-width:0}.module-link b{font-size:13px;font-weight:550;color:#334155}.module-link small{font-size:11px;color:#98a2b1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.module-link>.el-icon{flex-shrink:0;color:#a4afbd}.module-link:hover b,.module-link:hover>.el-icon{color:#3478d4}
@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.module-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:768px){.workbench{padding:14px}.welcome-panel{align-items:flex-start;padding:21px 18px;margin:0 -14px 19px;border-radius:0}.welcome-panel h1{font-size:21px}.welcome-panel p{max-width:250px;font-size:12px;line-height:1.6}.welcome-actions{flex-direction:column-reverse;align-items:flex-end;gap:9px}.today{font-size:10px;white-space:nowrap}.section-heading{align-items:flex-start;gap:6px}.section-heading h2{font-size:16px}.section-heading span{font-size:11px}.section-heading .updated{max-width:110px;text-align:right}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.metric-card{padding:14px 13px;gap:10px}.metric-top{font-size:11px}.metric-card strong{font-size:24px}.metric-foot{font-size:10px}.indicator-grid,.module-grid{grid-template-columns:1fr;gap:10px}.indicator-card{padding:14px}.indicator-values{justify-content:space-between;gap:8px}.indicator-values strong{font-size:21px}.module-group{padding:14px 15px 6px}.module-link{padding:10px 1px}}
</style>
