<template>
  <div class="m-page">
    <div class="m-head">
      <div class="m-title">{{data.department?.name || '部门工作台'}}</div>
      <div class="m-sub">待处理 {{data.pending??0}} 条　逾期 {{data.overdue??0}} 条</div>
      <div class="tabs">
        <span v-for="tab in tabs" :key="tab.value" class="tab" :class="{active:range===tab.value}" @click="switchRange(tab.value)">{{tab.label}}</span>
      </div>
    </div>
    <div v-loading="loading" class="m-body">
      <div v-for="issue in data.issues" :key="issue.id" class="issue" :class="{overdue: issue.overdue}" @click="open(issue)">
        <div class="issue-head">
          <span class="no">{{issue.issueNo}}</span>
          <el-tag :type="issue.overdue?'danger':statusType(issue.status)" size="small">{{issue.overdue?'已逾期':statusLabel(issue.status)}}</el-tag>
        </div>
        <div class="issue-title">{{issue.pointName}} · {{issue.area}} {{issue.floor||''}}</div>
        <div class="issue-desc">{{issue.description}}</div>
        <div class="issue-foot">
          <span>{{issue.category||'-'}}</span>
          <span>{{issue.urgency==='URGENT'?'紧急':'一般'}}</span>
          <span>时限 {{short(issue.dueAt)}}</span>
        </div>
      </div>
      <el-empty v-if="!loading && !data.issues?.length" description="当前没有待处理问题" />
    </div>
    <div class="m-tip">点击问题可进入整改反馈页；本页面链接有效期 90 天，过期请联系物业管理员刷新。</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute } from 'vue-router'
import { patrolApi } from '@/api/patrol'
const route = useRoute()
const token = String(route.params.token||'')
const loading = ref(false)
const range = ref('open')
const data = ref<any>({issues:[],pending:0,overdue:0})
const tabs = [
  {value:'open',label:'待处理'},{value:'today',label:'今日'},{value:'3d',label:'近3天'},{value:'7d',label:'近7天'}
]
function statusLabel(status:string){
  return ({ASSIGNED:'待整改',RECTIFYING:'整改中',REVIEWING:'待复核',CLOSED:'已闭环'} as Record<string,string>)[status] || status
}
function statusType(status:string):'primary'|'success'|'warning'|'info'|'danger'{
  return ({ASSIGNED:'primary',RECTIFYING:'primary',REVIEWING:'warning',CLOSED:'success'} as Record<string,'primary'|'success'|'warning'|'info'|'danger'>)[status] || 'info'
}
function short(value:string){ return value ? value.replace('T',' ').slice(5,16) : '-' }
async function load(){
  loading.value=true
  try{ data.value = await patrolApi.publicDepartment(token, range.value) as any || {issues:[]} }
  finally{ loading.value=false }
}
function switchRange(value:string){ range.value=value; load() }
function open(issue:any){
  window.location.href = `/patrol/i/${issue.feedbackToken}`
}
onMounted(load)
</script>

<style scoped>
.m-page{min-height:100vh;background:#f4f6f8;padding-bottom:24px}
.m-head{background:linear-gradient(135deg,#2f7cf6,#1c5fd0);color:#fff;padding:18px 16px 0}
.m-title{font-size:19px;font-weight:600}
.m-sub{margin-top:6px;font-size:13px;opacity:.9}
.tabs{display:flex;gap:18px;margin-top:14px}
.tab{padding:8px 0;font-size:13px;opacity:.75;cursor:pointer;border-bottom:2px solid transparent}
.tab.active{opacity:1;border-bottom-color:#fff;font-weight:600}
.m-body{padding:12px}
.issue{background:#fff;border-radius:10px;padding:12px 14px;margin-bottom:10px;cursor:pointer;border-left:4px solid #2f7cf6}
.issue.overdue{border-left-color:#f56c6c}
.issue-head{display:flex;justify-content:space-between;align-items:center}
.no{font-size:12px;color:#909399}
.issue-title{font-size:15px;font-weight:600;color:#303133;margin-top:8px}
.issue-desc{font-size:13px;color:#606266;line-height:1.6;margin-top:6px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.issue-foot{display:flex;gap:12px;font-size:12px;color:#909399;margin-top:8px}
.m-tip{padding:0 16px;color:#909399;font-size:12px;text-align:center;line-height:1.6}
</style>
