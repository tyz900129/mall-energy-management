<template>
  <div class="m-page">
    <div class="m-head">
      <div class="m-title">今日巡场</div>
      <div class="m-sub">{{data.roundDate}}　值班：{{data.dutyName||'物业管理员'}}</div>
      <div class="m-progress">
        <div class="bar"><i :style="{width: percent+'%'}"></i></div>
        <span>{{data.completed}}/{{data.total}}</span>
      </div>
    </div>

    <div v-loading="loading" class="m-body">
      <div v-for="group in data.groups" :key="group.area" class="group">
        <div class="group-title">{{group.area}}</div>
        <div class="grid">
          <button v-for="point in group.points" :key="point.id"
                  class="point-btn" :class="btnClass(point)" @click="open(point)">
            <span class="name">{{point.name}}</span>
            <span class="floor">{{point.floor||''}}{{point.locationText?' · '+point.locationText:''}}</span>
            <span class="state">
              <template v-if="point.state==='NORMAL'">正常 {{ shortTime(point.submittedAt) }}</template>
              <template v-else-if="point.state==='ABNORMAL'">异常 · {{ issueLabel(point.issueStatus) }}</template>
              <template v-else>待巡检</template>
            </span>
          </button>
        </div>
      </div>
      <el-empty v-if="!loading && !data.groups?.length" description="暂无巡场点位，请先在后台配置" />
    </div>
    <div class="m-tip">提交异常后会推送到对应整改部门群；如需修改已提交结果，请再次点击该点位重新提交。</div>
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { patrolApi } from '@/api/patrol'
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const data = ref<any>({groups:[],completed:0,total:0})
let token = String(route.query.token||'')
const percent = computed(()=> data.value.total ? Math.round((data.value.completed/data.value.total)*100) : 0)

function btnClass(point:any){
  return { normal: point.state==='NORMAL', abnormal: point.state==='ABNORMAL' }
}
function shortTime(value:string){ return value ? value.replace('T',' ').slice(11,16) : '' }
function issueLabel(status:string){
  return ({UNASSIGNED:'待分派',ASSIGNED:'已推送部门',RECTIFYING:'整改中',REVIEWING:'待复核',CLOSED:'已闭环'} as Record<string,string>)[status] || '处理中'
}
async function ensureToken(){
  if(token) return token
  const round:any = await patrolApi.todayRound()
  token = round?.entryToken || ''
  return token
}
async function load(){
  loading.value=true
  try{
    if(!await ensureToken()){ ElMessage.error('今日巡场尚未生成，请联系管理员'); return }
    data.value = await patrolApi.entry(token) as any || {groups:[],completed:0,total:0}
  }catch(e:any){
    if(e?.message) ElMessage.error(e.message)
    else ElMessage.error('巡场入口已失效，请使用今日推送的链接')
  }finally{ loading.value=false }
}
function open(point:any){
  router.push({path:'/patrol/form',query:{token,pointId:point.id}})
}
onMounted(load)
</script>

<style scoped>
.m-page{min-height:100vh;background:#f4f6f8;padding:0 0 24px}
.m-head{background:linear-gradient(135deg,#2f7cf6,#1c5fd0);color:#fff;padding:18px 16px 16px}
.m-title{font-size:20px;font-weight:600}
.m-sub{margin-top:6px;font-size:13px;opacity:.85}
.m-progress{display:flex;align-items:center;gap:10px;margin-top:12px}
.bar{flex:1;height:6px;background:rgba(255,255,255,.3);border-radius:3px;overflow:hidden}
.bar i{display:block;height:100%;background:#67e0a3;transition:width .3s}
.m-progress span{font-size:12px}
.m-body{padding:14px 12px}
.group{margin-bottom:16px}
.group-title{font-size:14px;font-weight:600;color:#303133;margin:0 4px 8px;padding-left:8px;border-left:3px solid #2f7cf6}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.point-btn{display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding:14px 12px;border-radius:10px;border:1px solid #dcdfe6;background:#fff;text-align:left;min-height:92px;cursor:pointer}
.point-btn .name{font-size:15px;font-weight:600;color:#303133}
.point-btn .floor{font-size:11px;color:#909399;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%}
.point-btn .state{font-size:12px;color:#909399;margin-top:auto}
.point-btn.normal{background:#eafaf1;border-color:#67c23a}
.point-btn.normal .state{color:#3f9c35}
.point-btn.abnormal{background:#fdecec;border-color:#f56c6c}
.point-btn.abnormal .state{color:#d93a3a}
.m-tip{padding:0 16px;color:#909399;font-size:12px;line-height:1.6;text-align:center}
</style>
