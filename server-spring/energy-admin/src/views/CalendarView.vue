<template>
  <div class="page">
    <div class="head">
      <div>
        <h2>任务日历</h2>
        <p>按日查看个人、岗位与部门任务；空心点为按计划预计、尚未生成的任务。</p>
      </div>
      <el-radio-group v-model="view">
        <el-radio-button label="month">月视图</el-radio-button>
        <el-radio-button label="week">周视图</el-radio-button>
        <el-radio-button label="list">列表视图</el-radio-button>
      </el-radio-group>
    </div>
    <div class="filters">
      <el-tree-select v-model="query.deptId" :data="deptTree" :props="{label:'label',children:'children'}" node-key="value" check-strictly clearable placeholder="全部部门" :render-after-expand="false" @change="load"/>
      <el-select v-model="query.category" clearable placeholder="全部分类" @change="load">
        <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value"/>
      </el-select>
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="load">
        <el-option v-for="(v,k) in statusMap" :key="k" :label="v" :value="k"/>
      </el-select>
      <el-input v-model="query.keyword" clearable placeholder="任务、计划或责任人" @keyup.enter="load"/>
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <div class="legend">
      <span><i class="dot pending"/>待办</span><span><i class="dot doing"/>进行中</span><span><i class="dot done"/>已完成</span><span><i class="dot overdue"/>逾期</span><span><i class="dot empty"/>预计</span>
      <span class="legend-note">营运巡场任务为只读聚合，点击回到原模块处理</span>
    </div>
    <el-calendar v-if="view==='month'" v-model="selected">
      <template #date-cell="{ data }">
        <div class="cell">
          <div class="day">{{ Number(data.day.slice(8,10)) }}</div>
          <div v-for="x in (dayMap[data.day]||[]).slice(0,3)" :key="x.key" class="item" :class="itemClass(x)" @click.stop="openItem(x)">
            <i class="dot" :class="itemClass(x)"/>{{ x.cellTitle || x.title }}
          </div>
          <div v-if="(dayMap[data.day]||[]).length>3" class="more">+{{ dayMap[data.day].length-3 }} 项</div>
        </div>
      </template>
    </el-calendar>
    <div v-else-if="view==='week'" class="week-wrap">
      <div class="week-bar">
        <span class="week-range">{{ weekDays[0].key }} ~ {{ weekDays[6].key }}</span>
        <div>
          <el-button size="small" @click="shiftWeek(-1)">上一周</el-button>
          <el-button size="small" @click="selected = new Date()">本周</el-button>
          <el-button size="small" @click="shiftWeek(1)">下一周</el-button>
        </div>
      </div>
      <div class="week">
        <div class="week-head">
          <div class="lane-name">责任对象</div>
          <div v-for="d in weekDays" :key="d.key" class="week-day" :class="{ today: d.today }">
            <span class="wd">{{ d.label }}</span><span class="wn">{{ d.num }}</span>
          </div>
        </div>
        <div v-for="lane in lanes" :key="lane.name" class="lane">
          <div class="lane-name">{{ lane.name }}<small>{{ lane.total }} 项</small></div>
          <div v-for="d in weekDays" :key="d.key" class="lane-cell">
            <div v-for="x in (lane.map[d.key]||[])" :key="x.key" class="witem" :class="itemClass(x)" @click="openItem(x)">
              <i class="dot" :class="itemClass(x)"/><span class="wt">{{ fmtTime(x.scheduledAt) }}</span><span class="wt-title">{{ x.cellTitle || x.title }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!lanes.length" description="本周暂无任务"/>
      </div>
    </div>
    <div v-else class="list">
      <div v-for="g in listGroups" :key="g.date" class="group">
        <div class="group-date">{{ g.date }} <small>{{ weekday(g.date) }}</small></div>
        <div v-for="x in g.items" :key="x.key" class="row" @click="openItem(x)">
          <i class="dot" :class="itemClass(x)"/>
          <span class="time">{{ fmtFull(x.scheduledAt) }}</span>
          <span class="title">{{ x.title }}</span>
          <el-tag v-if="x.external" size="small" type="warning" effect="plain">营运巡场</el-tag>
          <el-tag size="small" :type="tagType(x.status)">{{ x.projected ? '预计' : (statusMap[x.status]||x.status) }}</el-tag>
          <span class="owner">{{ x.ownerName || x.assigneeName || '-' }}</span>
        </div>
      </div>
      <el-empty v-if="!listGroups.length" description="暂无任务"/>
    </div>
    <div v-if="view==='month'" class="day-panel">
      <div class="panel-title">{{ dayKey(selected) }} 任务</div>
      <div v-for="x in (dayMap[dayKey(selected)]||[])" :key="x.key" class="row" @click="openItem(x)">
        <i class="dot" :class="itemClass(x)"/>
        <span class="time">{{ fmtFull(x.scheduledAt) }}</span>
        <span class="title">{{ x.title }}</span>
        <el-tag v-if="x.external" size="small" type="warning" effect="plain">营运巡场</el-tag>
        <el-tag size="small" :type="tagType(x.status)">{{ x.projected ? '预计' : (statusMap[x.status]||x.status) }}</el-tag>
      </div>
      <el-empty v-if="!(dayMap[dayKey(selected)]||[]).length" description="当天暂无任务"/>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { calendarApi } from '@/api/calendar'
import { formatDateTime } from '@/utils/format'
import { systemDepartmentApi } from '@/api/system'

const router = useRouter()
const view = ref<'month' | 'week' | 'list'>('month')
const selected = ref(new Date())
const query = reactive<any>({ deptId: null, category: '', status: '', keyword: '' })
const instances = ref<any[]>([])
const projections = ref<any[]>([])
const externals = ref<any[]>([])
const deptTree = ref<any[]>([])
const categories = [
  { value: 'DAILY', label: '日常事务' }, { value: 'SAFETY', label: '安全消防' },
  { value: 'CLEANING', label: '保洁环境' }, { value: 'OPERATION', label: '运营管理' },
  { value: 'MEETING', label: '会议培训' }, { value: 'OTHER', label: '其他' }
]
const statusMap: any = { PENDING: '待办', IN_PROGRESS: '进行中', COMPLETED: '已完成', PENDING_RECTIFICATION: '待整改', OVERDUE: '已逾期', CLOSED: '已闭环' }
const unwrap = (r: any) => r?.data?.data ?? r?.data ?? r
const pad = (n: number) => String(n).padStart(2, '0')
const dayKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const parseDay = (s: any) => String(s || '').slice(0, 10)
const fmtTime = (s: any) => s ? String(s).replace('T', ' ').slice(11, 16) : '--:--'
const fmtFull = (s: any) => formatDateTime(s)
const weekday = (d: string) => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(d + 'T00:00:00').getDay()]

const monthRange = computed(() => {
  const d = selected.value, y = d.getFullYear(), m = d.getMonth()
  return { from: dayKey(new Date(y, m, 1)), to: dayKey(new Date(y, m + 1, 0)) }
})
const weekStart = computed(() => {
  const d = new Date(selected.value.getFullYear(), selected.value.getMonth(), selected.value.getDate())
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
})
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + i)
  return { key: dayKey(d), label: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][i], num: `${d.getMonth() + 1}/${d.getDate()}`, today: dayKey(d) === dayKey(new Date()) }
}))
const range = computed(() => view.value === 'week' ? { from: weekDays.value[0].key, to: weekDays.value[6].key } : monthRange.value)
function shiftWeek(n: number) {
  const d = new Date(selected.value)
  d.setDate(d.getDate() + n * 7)
  selected.value = d
}
const items = computed(() => [
  ...instances.value.map((x: any) => ({ ...x, key: `i-${x.id}`, title: x.targetName || x.planName, projected: false })),
  ...projections.value.map((x: any, i: number) => ({ ...x, key: `p-${x.planId}-${i}`, title: x.ownerDeptName || x.planName, projected: true })),
  ...externals.value.map((x: any) => ({ ...x, key: `e-${x.source}-${x.id}`, title: x.title, cellTitle: `巡场·${x.title}`, projected: false, external: true }))
])
const dayMap = computed(() => {
  const map: any = {}
  items.value.forEach((x: any) => { const k = parseDay(x.scheduledAt); (map[k] ||= []).push(x) })
  Object.values(map).forEach((l: any) => l.sort((a: any, b: any) => String(a.scheduledAt).localeCompare(String(b.scheduledAt))))
  return map
})
const listGroups = computed(() => Object.keys(dayMap.value).sort().map(date => ({ date, items: dayMap.value[date] })))
// 周视图资源泳道：按责任对象分组，每行展开 7 天
const lanes = computed<any[]>(() => {
  const map: any = {}
  items.value.forEach((x: any) => {
    const name = x.ownerName || x.ownerDeptName || x.assigneeName || '未指派'
    if (!map[name]) map[name] = { name, total: 0, map: {} }
    const k = parseDay(x.scheduledAt)
    ;(map[name].map[k] ||= []).push(x)
    map[name].total++
  })
  Object.values(map).forEach((lane: any) => Object.values(lane.map).forEach((arr: any) => arr.sort((a: any, b: any) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)))))
  return Object.values(map).sort((a: any, b: any) => String(a.name).localeCompare(String(b.name), 'zh'))
})
const itemClass = (x: any) => x.projected ? 'empty' : x.status === 'OVERDUE' ? 'overdue' : x.status === 'COMPLETED' || x.status === 'CLOSED' ? 'done' : x.status === 'IN_PROGRESS' ? 'doing' : 'pending'
const tagType = (s: string) => s === 'OVERDUE' ? 'danger' : ['COMPLETED', 'CLOSED'].includes(s) ? 'success' : s === 'IN_PROGRESS' ? 'primary' : 'info'

const loadedKey = ref('')
async function load() {
  const r = unwrap(await calendarApi.tasks({ ...range.value, ...query }))
  instances.value = r.instances || []
  projections.value = r.projections || []
  externals.value = r.externals || []
  loadedKey.value = `${range.value.from}~${range.value.to}`
}
function openItem(x: any) {
  if (x.projected) return ElMessage.info('该任务按计划预计发生，尚未生成实例')
  if (x.external) return router.push(x.link)
  router.push({ path: '/inspection/tasks', query: { taskId: x.id } })
}
function buildTree(list: any[]) {
  const nodes = list.map((d: any) => ({ value: d.id, label: d.name, parentId: d.parentId, children: [] as any[] }))
  const map = new Map(nodes.map((n: any) => [n.value, n]))
  const roots: any[] = []
  nodes.forEach((n: any) => { if (n.parentId && map.has(n.parentId)) (map.get(n.parentId) as any).children.push(n); else roots.push(n) })
  return roots
}
watch(range, (r) => { const k = `${r.from}~${r.to}`; if (k !== loadedKey.value) load() })
onMounted(async () => {
  const depts: any = unwrap(await systemDepartmentApi.tree())
  deptTree.value = buildTree(Array.isArray(depts) ? depts : [])
  await load()
})
</script>

<style scoped>
.page{padding:20px}.head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.head h2{margin:0}.head p{color:#777;margin:6px 0 0}
.filters{display:flex;gap:10px;background:#fff;padding:14px;border-radius:8px;margin:14px 0;flex-wrap:wrap}.filters .el-input{max-width:320px}
.legend{display:flex;gap:16px;margin-bottom:10px;color:#666;font-size:13px;flex-wrap:wrap}.legend i{margin-right:5px}.legend-note{margin-left:auto;color:#b88230}
.dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:#c0c4cc}.dot.pending{background:#909399}.dot.doing{background:#409eff}.dot.done{background:#67c23a}.dot.overdue{background:#f56c6c}.dot.empty{background:transparent;border:1px solid #909399;box-sizing:border-box}
.cell{height:100%;padding:2px 4px}.day{font-size:13px;color:#606266}.item{display:flex;align-items:center;gap:4px;font-size:11px;line-height:16px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;cursor:pointer}.item .dot{width:6px;height:6px;flex:none}.more{font-size:11px;color:#909399}
.list{background:#fff;border-radius:8px;padding:8px 16px}.group{padding:10px 0;border-bottom:1px solid #f0f0f0}.group-date{font-weight:600;margin-bottom:8px}.group-date small{color:#909399;font-weight:400;margin-left:6px}.row{display:flex;align-items:center;gap:10px;padding:8px 0;cursor:pointer}.row:hover{background:#f7f9fc}.time{color:#606266;font-variant-numeric:tabular-nums}.title{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.owner{color:#909399;font-size:13px}
.day-panel{margin-top:14px;background:#fff;border-radius:8px;padding:8px 16px}.panel-title{font-weight:600;padding:10px 0}
.week-wrap{margin-top:2px}.week-bar{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px;color:#606266;font-size:13px}
.week{background:#fff;border-radius:8px;border:1px solid #ebeef5;overflow-x:auto}
.week-head,.lane{display:grid;grid-template-columns:130px repeat(7,minmax(110px,1fr));min-width:860px}
.week-head{background:#f7f8fa;border-bottom:1px solid #ebeef5}
.week-day{display:flex;flex-direction:column;align-items:center;gap:2px;padding:8px 4px;border-left:1px solid #ebeef5;font-size:12px;color:#606266}
.week-day.today{color:#409eff;font-weight:600}
.wd{font-weight:600}.wn{font-size:11px;color:#909399}
.lane{border-bottom:1px solid #f2f4f7}
.lane-name{padding:10px 8px;font-size:13px;font-weight:600;color:#303133;background:#fafbfc;border-right:1px solid #ebeef5;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lane-name small{display:block;margin-top:2px;font-size:11px;font-weight:400;color:#909399}
.lane-cell{min-height:64px;padding:4px;border-left:1px solid #f2f4f7;display:flex;flex-direction:column;gap:3px}
.witem{display:flex;align-items:center;gap:3px;padding:3px 4px;border-radius:4px;background:#f5f7fa;font-size:11px;line-height:14px;cursor:pointer;overflow:hidden}
.witem:hover{background:#eef3fb}
.witem .dot{width:6px;height:6px;flex:none}
.wt{color:#606266;flex:none;font-variant-numeric:tabular-nums}
.wt-title{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
@media(max-width:768px){.page{padding:10px}.head{display:block}.filters{display:grid}.filters .el-input{max-width:none}}
</style>
