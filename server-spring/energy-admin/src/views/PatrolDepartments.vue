<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">整改部门配置</div>
        <div class="page-note">系统固定设备课 / 安保课 / 保洁课三个部门。绑定群组后，巡场异常才会推送到该部门群。</div>
      </div>
      <div class="actions">
        <el-button v-if="canManage" @click="refreshTokens">刷新工作台链接</el-button>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="name" label="部门" width="110" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column label="绑定群组" min-width="200">
        <template #default="{row}">
          <span v-if="row.webhookName">{{row.webhookName}}</span>
          <el-tag v-else type="warning">未配置群组</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contactName" label="联系人" width="110">
        <template #default="{row}">{{row.contactName||'-'}}</template>
      </el-table-column>
      <el-table-column prop="dueTime" label="每日整改截止" width="130">
        <template #default="{row}">{{ String(row.dueTime||'20:00:00').slice(0,5) }}</template>
      </el-table-column>
      <el-table-column prop="pendingCount" label="待处理" width="90" />
      <el-table-column label="部门工作台链接" min-width="260">
        <template #default="{row}">
          <div class="link-cell">
            <el-button link type="primary" :disabled="!row.accessUrl" @click="copy(row.accessUrl)">复制链接</el-button>
            <el-button link type="primary" :disabled="!row.accessUrl" @click="openWorkbench(row.accessUrl)">打开</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="85">
        <template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="110" fixed="right">
        <template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="`编辑 ${form.name}`" width="560px">
      <el-form label-position="top">
        <el-form-item label="绑定通知群组">
          <el-select v-model="form.webhookConfigId" clearable placeholder="选择 Webhook 群组" style="width:100%">
            <el-option v-for="item in webhooks" :key="item.id" :label="`${item.name}（${item.provider}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="抄送群组 ID（多个用逗号分隔）">
          <el-input v-model="form.ccWebhookIds" placeholder="可选" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contactName" maxlength="64" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="32" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="每日整改截止时间">
              <el-time-picker v-model="form.dueTime" value-format="HH:mm" format="HH:mm" placeholder="如 20:00" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
          </el-col>
        </el-row>
        <div class="form-hint">当天该时间点前提交或分派，当天截止；超过该时间点自动顺延到次日同一时间。</div>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const canManage = computed(() => user.hasPermission('patrol.config'))
const rows = ref<any[]>([])
const webhooks = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({id:null,name:'',webhookConfigId:null,ccWebhookIds:'',contactName:'',contactPhone:'',dueTime:'20:00',enabled:true})

async function load(){
  loading.value=true
  try{ rows.value = await patrolApi.departments() as any||[] }
  finally{ loading.value=false }
}
async function loadWebhooks(){ webhooks.value = await patrolApi.webhooks() as any||[] }
function edit(row:any){
  Object.assign(form, { ...row, enabled: !!row.enabled, dueTime: String(row.dueTime||'20:00:00').slice(0,5) })
  visible.value=true
}
async function save(){
  saving.value=true
  try{
    await patrolApi.updateDepartment(form.id, form)
    ElMessage.success('保存成功')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
async function refreshTokens(){
  await ElMessageBox.confirm('刷新后旧链接立即失效，需要重新发送给部门。是否继续？','刷新链接',{type:'warning'})
  for(const row of rows.value) await patrolApi.resetDepartmentToken(row.id)
  ElMessage.success('已刷新')
  await load()
}
async function copy(url:string){
  if(!url) return
  try{ await navigator.clipboard.writeText(url); ElMessage.success('链接已复制') }
  catch{ ElMessage.warning('复制失败，请手动选择链接') }
}
function openWorkbench(url:string){ if(url) window.open(url,'_blank') }
onMounted(async()=>{ await Promise.all([load(),loadWebhooks()]) })
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
.link-cell{display:flex;gap:6px}
.form-hint{font-size:12px;color:#909399;line-height:1.6;margin:-6px 0 6px}
@media(max-width:768px){.page{overflow-x:auto}.page-header{align-items:flex-start}}
</style>
