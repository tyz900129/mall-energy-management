<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">任务看板</div>
        <div class="page-note">按「处室 × 时段」查看当日任务，点击色块查看明细；可补发推送或豁免任务。格内数字：合格/不合格/不适用。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="board-wrap" v-if="!isMobile">
      <table class="board">
        <thead>
          <tr>
            <th class="dept-col">处室＼时段</th>
            <th v-for="s in slots" :key="s.id">
              <div>{{ s.name }}</div>
              <div class="slot-time">{{ slotTime(s) }}</div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in depts" :key="d.id">
            <td class="dept-col">
              <div class="dept-name">{{ d.name }}</div>
              <el-tag size="small" :type="d.formType==='FOOD' ? 'warning' : 'primary'" effect="plain">{{ d.formType==='FOOD' ? '餐饮' : '零售' }}</el-tag>
            </td>
            <td v-for="s in slots" :key="s.id">
              <div v-if="taskOf(d.id, s.id)" class="cell" :class="'st-' + taskOf(d.id, s.id).status.toLowerCase()" @click="openDetail(taskOf(d.id, s.id))">
                <div class="cell-top">
                  <span class="cell-status">{{ statusLabel(taskOf(d.id, s.id).status) }}</span>
                  <span v-if="taskOf(d.id, s.id).lateMinutes > 0" class="late">迟{{ taskOf(d.id, s.id).lateMinutes }}′</span>
                </div>
                <div class="cell-sub">{{ taskOf(d.id, s.id).submitterName || taskOf(d.id, s.id).assigneeName || '未指定' }}</div>
                <div v-if="taskOf(d.id, s.id).isHandover" class="cell-count">交接</div>
                <div v-else class="cell-count">
                  <i class="ok">{{ taskOf(d.id, s.id).passCount || 0 }}</i>/<i class="bad">{{ taskOf(d.id, s.id).failCount || 0 }}</i>/<i>{{ taskOf(d.id, s.id).skipCount || 0 }}</i>
                </div>
              </div>
              <div v-else class="cell st-none">未生成</div>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="legend">
        <span><i class="dot st-pending" />待填报</span>
        <span><i class="dot st-in_progress" />填报中</span>
        <span><i class="dot st-done" />已完成</span>
        <span><i class="dot st-overdue" />逾期未报</span>
        <span><i class="dot st-exempt" />已豁免</span>
      </div>
    </div>

    <!-- 移动端：按处室分组的时段任务列表 -->
    <div v-if="isMobile" v-loading="loading" class="m-board">
      <div v-for="d in depts" :key="d.id" class="m-dept">
        <div class="m-dept-hd">
          <span class="m-dept-name">{{ d.name }}</span>
          <el-tag size="small" :type="d.formType==='FOOD'?'warning':'primary'" effect="plain">{{ d.formType==='FOOD' ? '餐饮' : '零售' }}</el-tag>
        </div>
        <div v-for="s in slots" :key="s.id" class="m-slot"
          :class="taskOf(d.id, s.id) ? 'st-' + taskOf(d.id, s.id).status.toLowerCase() : 'st-none'"
          @click="taskOf(d.id, s.id) && openDetail(taskOf(d.id, s.id))">
          <div class="m-slot-l">
            <div class="m-slot-name">{{ s.name }}</div>
            <div class="m-slot-time">{{ slotTime(s) }}</div>
          </div>
          <div class="m-slot-r">
            <div class="m-slot-st">{{ taskOf(d.id, s.id) ? statusLabel(taskOf(d.id, s.id).status) : '未生成' }}</div>
            <div v-if="taskOf(d.id, s.id) && !taskOf(d.id, s.id).isHandover" class="m-slot-cnt">
              合格 {{ taskOf(d.id, s.id).passCount || 0 }} · 不合格 {{ taskOf(d.id, s.id).failCount || 0 }}
              <span v-if="taskOf(d.id, s.id).lateMinutes > 0" class="late">迟{{ taskOf(d.id, s.id).lateMinutes }}′</span>
            </div>
            <div v-else-if="taskOf(d.id, s.id)" class="m-slot-cnt">交接确认</div>
            <div class="m-slot-who">{{ taskOf(d.id, s.id)?.submitterName || taskOf(d.id, s.id)?.assigneeName || '' }}</div>
          </div>
        </div>
      </div>
      <div class="legend">
        <span><i class="dot st-pending" />待填报</span>
        <span><i class="dot st-done" />已完成</span>
        <span><i class="dot st-overdue" />逾期未报</span>
        <span><i class="dot st-exempt" />已豁免</span>
      </div>
    </div>

    <el-drawer v-model="drawer" :size="isMobile ? '100%' : '660px'" :title="detail ? `${detail.deptName} · ${detail.slotName}` : '任务明细'">
      <div v-loading="detailLoading">
        <div v-if="detail" class="detail">
          <div class="kv"><label>状态</label><el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></div>
          <div class="kv"><label>计划时间</label><span>{{ fmt(detail.plan_start_at) }} ~ {{ fmt(detail.plan_end_at) }}</span></div>
          <div class="kv"><label>值班干部</label><span>{{ detail.assignee_name || '未排班（当班干部填报）' }}</span></div>
          <div class="kv"><label>填报人</label><span>{{ detail.submitter_name || '-' }}</span></div>
          <div class="kv"><label>推送时间</label><span>{{ fmt(detail.pushed_at) }}</span></div>
          <div class="kv"><label>完成时间</label><span>{{ fmt(detail.finished_at) }}<template v-if="detail.late_minutes > 0">（迟 {{ detail.late_minutes }} 分钟）</template></span></div>
          <div class="kv" v-if="detail.exempt_reason"><label>豁免原因</label><span>{{ detail.exempt_reason }}</span></div>
          <div class="kv"><label>填报链接</label>
            <el-link type="primary" :href="entryLink" target="_blank">打开 H5 填报页</el-link>
            <el-button link type="primary" @click="copyLink">复制链接</el-button>
          </div>

          <template v-if="!detail.is_handover">
            <div class="sec-title">检查项结果（合格 {{ detail.pass_count || 0 }} / 不合格 {{ detail.fail_count || 0 }} / 不适用 {{ detail.skip_count || 0 }}<template v-if="issueCount"> · 问题单 {{ issueCount }} 条</template>）</div>
            <el-table :data="detail.items" size="small" border>
              <el-table-column prop="item_code" label="编码" width="60" />
              <el-table-column prop="group_name" label="分组" width="90" show-overflow-tooltip />
              <el-table-column prop="item_content" label="检查内容" min-width="200" show-overflow-tooltip />
              <el-table-column label="结果" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.result" size="small" :type="resultTagType(row.result)" effect="plain">{{ resultLabel(row.result) }}</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="关联问题" width="200">
                <template #default="{ row }">
                  <template v-if="(row.failures || []).length">
                    <div v-for="f in row.failures" :key="f.id" class="issue-line">
                      <div>{{ f.issueNo }}</div>
                      <div class="issue-sub">{{ f.merchantName || '未关联商户' }}<template v-if="f.stallCode">（{{ f.stallCode }}）</template> · {{ issueStatusLabel(f.status) }}</div>
                    </div>
                  </template>
                  <template v-else-if="row.issueNo">
                    <div>{{ row.issueNo }}</div>
                    <div class="issue-sub">{{ row.merchantName || '' }} {{ issueStatusLabel(row.issueStatus) }}</div>
                  </template>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <div v-if="canManage" class="drawer-actions">
            <el-button type="primary" plain @click="pushNow">补发推送</el-button>
            <el-button v-if="['PENDING','IN_PROGRESS','OVERDUE'].includes(detail.status)" type="danger" plain @click="exempt">豁免任务</el-button>
          </div>
          <div v-if="isSuperAdmin" class="drawer-actions">
            <el-button type="danger" @click="removeTask">删除该任务（测试记录）</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'

const isMobile = useIsMobile()
const route = useRoute()

const user = useUserStore()
const canManage = computed(() => user.hasPermission('ops.patrol.config'))
const isSuperAdmin = computed(() => user.hasPermission('ops.patrol.delete'))
const date = ref(localDate())
const depts = ref<any[]>([])
const slots = ref<any[]>([])
const tasks = ref<any[]>([])
const loading = ref(false)
const drawer = ref(false)
const detail = ref<any>(null)
const detailLoading = ref(false)

const entryLink = computed(() => detail.value ? `${location.origin}/ops-patrol/entry?token=${detail.value.entry_token}` : '')
const issueCount = computed(() => (detail.value?.items || []).reduce((n: number, it: any) => n + ((it.failures || []).length || (it.issueNo ? 1 : 0)), 0))

function localDate() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
async function load() {
  loading.value = true
  try {
    const data: any = await opsPatrolApi.taskBoard(date.value)
    depts.value = data?.depts || []
    slots.value = data?.slots || []
    tasks.value = data?.tasks || []
  } finally { loading.value = false }
}
function taskOf(deptId: number, slotId: number) {
  return tasks.value.find(t => t.deptId === deptId && t.slotId === slotId)
}
function slotTime(s: any) {
  if (s.relativeToClose) return `闭店+${s.offsetStartMin}~${s.offsetEndMin}分`
  return `${s.startTime || ''}~${s.endTime || ''}`
}
function statusLabel(st: string) {
  return ({ PENDING: '待填报', IN_PROGRESS: '填报中', DONE: '已完成', OVERDUE: '逾期未报', EXEMPT: '已豁免' } as Record<string, string>)[st] || st
}
function statusTagType(st: string) {
  return ({ PENDING: 'info', IN_PROGRESS: 'warning', DONE: 'success', OVERDUE: 'danger', EXEMPT: 'info' } as Record<string, any>)[st] || 'info'
}
function resultLabel(r: string) { return ({ PASS: '合格', FAIL: '不合格', NA: '不适用' } as Record<string, string>)[r] || r }
function resultTagType(r: string) { return ({ PASS: 'success', FAIL: 'danger', NA: 'info' } as Record<string, any>)[r] || 'info' }
function issueStatusLabel(st: string) {
  return ({ PENDING: '待跟进', RECTIFYING: '整改中', REVIEWING: '待复核', CLOSED: '已闭环' } as Record<string, string>)[st] || st
}
function fmt(v: string) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-' }

async function openDetail(task: any) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  try { detail.value = await opsPatrolApi.taskDetail(task.id) } finally { detailLoading.value = false }
}
async function pushNow() {
  await opsPatrolApi.pushTask(detail.value.id)
  ElMessage.success('已补发推送')
  detail.value = await opsPatrolApi.taskDetail(detail.value.id)
  await load()
}
async function copyLink() {
  try { await navigator.clipboard.writeText(entryLink.value); ElMessage.success('链接已复制') }
  catch { ElMessage.warning('复制失败，请手动复制') }
}
async function exempt() {
  const { value } = await ElMessageBox.prompt('请输入豁免原因', '豁免任务', { inputPattern: /\S+/, inputErrorMessage: '请填写豁免原因' })
  await opsPatrolApi.exemptTask(detail.value.id, value)
  ElMessage.success('已豁免')
  drawer.value = false
  await load()
}
async function removeTask() {
  const label = `${detail.value.deptName} · ${detail.value.slotName}`
  try { await ElMessageBox.confirm(`确认彻底删除任务「${label}」？检查项结果、问题单与照片会一并删除，且不可恢复。`, '删除测试记录', { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' }) }
  catch { return }
  await opsPatrolApi.removeTask(detail.value.id)
  ElMessage.success('已删除')
  drawer.value = false
  await load()
}
onMounted(async () => {
  const qd = String(route.query.date || '').slice(0, 10)
  if (/^\d{4}-\d{2}-\d{2}$/.test(qd)) date.value = qd
  await load()
  const taskId = Number(route.query.taskId || 0)
  if (taskId) await openDetail({ id: taskId })
})
</script>

<style scoped>
.actions{display:flex;align-items:center;gap:10px}
.board-wrap{overflow-x:auto}
.board{border-collapse:collapse;width:100%;min-width:960px;background:#fff}
.board th,.board td{border:1px solid #ebeef5;padding:8px;text-align:center;vertical-align:middle}
.board th{background:#f5f7fa;font-size:13px;color:#606266;font-weight:600}
.slot-time{font-size:12px;color:#909399;font-weight:400;margin-top:2px}
.dept-col{width:130px;min-width:130px}
.dept-name{font-weight:600;margin-bottom:4px}
.cell{border-radius:8px;padding:8px 6px;cursor:pointer;font-size:12px;transition:transform .1s}
.cell:hover{transform:scale(1.03)}
.cell-top{display:flex;justify-content:center;gap:6px;align-items:center}
.cell-status{font-weight:600}
.late{color:#f56c6c}
.cell-sub{color:#606266;margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.cell-count{margin-top:4px;color:#909399}
.cell-count i{font-style:normal}
.cell-count .ok{color:#67c23a;font-weight:600}
.cell-count .bad{color:#f56c6c;font-weight:600}
.st-pending{background:#f4f4f5;color:#606266}
.st-in_progress{background:#fdf6ec;color:#b88230}
.st-done{background:#f0f9eb;color:#529b2e}
.st-overdue{background:#fef0f0;color:#c45656}
.st-exempt{background:#f4f4f5;color:#909399;text-decoration:line-through}
.st-none{background:#fafafa;color:#c0c4cc;cursor:default}
.st-none:hover{transform:none}
.legend{display:flex;gap:16px;margin-top:12px;font-size:12px;color:#909399;align-items:center}
.dot{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:4px;vertical-align:middle}
.detail .kv{display:flex;gap:10px;padding:6px 0;font-size:13px;color:#606266}
.detail .kv label{width:70px;flex-shrink:0;color:#909399}
.sec-title{font-size:14px;font-weight:600;margin:16px 0 10px}
.issue-sub{font-size:12px;color:#909399}
.issue-line + .issue-line{margin-top:6px;padding-top:6px;border-top:1px dashed #ebeef5}
.drawer-actions{margin-top:20px;display:flex;gap:10px}

/* 移动端：处室分组的时段任务列表 */
.m-board{display:flex;flex-direction:column;gap:14px}
.m-dept{background:#fff;border-radius:12px;border:1px solid #ebeef5;overflow:hidden}
.m-dept-hd{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;background:#f8fafc;border-bottom:1px solid #f0f2f5}
.m-dept-name{font-size:16px;font-weight:600;color:#303133}
.m-slot{display:flex;justify-content:space-between;gap:10px;padding:11px 14px;border-bottom:1px solid #f5f7fa}
.m-slot:last-child{border-bottom:none}
.m-slot:active{background:#f5f9ff}
.m-slot-l{flex:1;min-width:0}
.m-slot-name{font-size:15px;color:#303133;font-weight:500}
.m-slot-time{font-size:12px;color:#909399;margin-top:2px}
.m-slot-r{text-align:right;flex-shrink:0}
.m-slot-st{font-size:14px;font-weight:600}
.m-slot-cnt{font-size:12px;color:#909399;margin-top:2px}
.m-slot-who{font-size:12px;color:#b1b3b8;margin-top:2px}
.m-slot.st-pending .m-slot-st{color:#606266}
.m-slot.st-in_progress .m-slot-st{color:#b88230}
.m-slot.st-done .m-slot-st{color:#529b2e}
.m-slot.st-overdue .m-slot-st{color:#c45656}
.m-slot.st-exempt .m-slot-st{color:#909399}
.m-slot.st-none .m-slot-st{color:#c0c4cc}
@media (max-width:768px){
  .detail .kv{font-size:14px}
  .detail .kv label{width:64px}
  .drawer-actions .el-button{flex:1}
}
</style>
