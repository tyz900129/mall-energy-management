<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">处室与群配置</div>
        <div class="page-note">维护商管处室的负责楼层、闭店时间与专属通知群。清场任务按各处闭店时间触发。</div>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="name" label="处室" width="120" />
      <el-table-column label="表单类型" width="100">
        <template #default="{row}"><el-tag :type="row.formType==='FOOD'?'warning':'primary'">{{row.formType==='FOOD'?'餐饮':'零售'}}</el-tag></template>
      </el-table-column>
      <el-table-column label="负责楼层" min-width="160">
        <template #default="{row}">{{(row.floors||[]).map((f:any)=>f.floorName).join('、')||'-'}}</template>
      </el-table-column>
      <el-table-column prop="closeTime" label="闭店时间" width="90" />
      <el-table-column label="通知群" min-width="140"><template #default="{row}">{{row.webhookName||'未配置'}}</template></el-table-column>
      <el-table-column label="处长" width="100"><template #default="{row}">{{row.leaderName||'-'}}</template></el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="100" fixed="right">
        <template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="`配置 ${form.name}`" width="560px">
      <el-form label-position="top">
        <el-form-item label="通知群机器人">
          <el-select v-model="form.webhookConfigId" clearable placeholder="选择群机器人（未配置则不推送）" style="width:100%">
            <el-option v-for="w in webhooks" :key="w.id" :label="`${w.name}（${w.provider}）`" :value="w.id" />
          </el-select>
          <div class="hint">机器人可在「系统管理 - 通知机器人」中新增</div>
        </el-form-item>
        <el-form-item label="负责楼层">
          <el-select v-model="form.floorCodes" multiple filterable placeholder="选择楼层" style="width:100%">
            <el-option v-for="f in floorOptions" :key="f.floorCode" :label="f.floorName" :value="f.floorCode" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="闭店时间（清场任务基准）">
              <el-time-picker v-model="form.closeTime" format="HH:mm" value-format="HH:mm" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="处长姓名"><el-input v-model="form.leaderName" maxlength="64" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const canManage = computed(() => user.hasPermission('ops.patrol.dept'))
const rows = ref<any[]>([])
const webhooks = ref<any[]>([])
const floorOptions = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({id:null,name:'',webhookConfigId:null,floorCodes:[],closeTime:'21:30',leaderName:'',enabled:true})

async function load(){ loading.value=true; try{ rows.value = await opsPatrolApi.depts() as any || [] } finally{ loading.value=false } }
function edit(row:any){
  Object.assign(form,{ id:row.id, name:row.name, webhookConfigId:row.webhookConfigId,
    floorCodes:(row.floors||[]).map((f:any)=>f.floorCode), closeTime:row.closeTime||'21:30',
    leaderName:row.leaderName||'', enabled:!!row.enabled })
  visible.value=true
}
async function save(){
  saving.value=true
  try{
    await opsPatrolApi.saveDept(form.id,{
      webhookConfigId: form.webhookConfigId,
      leaderName: form.leaderName,
      closeTime: form.closeTime,
      enabled: form.enabled,
      floors: form.floorCodes.map((code:string)=>{
        const f = floorOptions.value.find((x:any)=>x.floorCode===code)
        return { floorCode: code, floorName: f?.floorName || code }
      })
    })
    ElMessage.success('保存成功')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
onMounted(async()=>{
  await Promise.all([load(), (async()=>{ webhooks.value = await opsPatrolApi.webhooks() as any || [] })(), (async()=>{ floorOptions.value = await opsPatrolApi.floorOptions() as any || [] })()])
})
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.hint{color:#909399;font-size:12px;margin-top:4px}
</style>
