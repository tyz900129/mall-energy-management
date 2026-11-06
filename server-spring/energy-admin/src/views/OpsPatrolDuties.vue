<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">干部排班</div>
        <div class="page-note">按处室排值班干部，可留空：未排班时任务照常推送，由当班干部通过群链接填报。</div>
      </div>
      <div class="actions">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" @change="load" />
        <el-button v-if="canManage" type="primary" @click="edit()">新增排班</el-button>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="dutyDate" label="日期" width="120" />
      <el-table-column prop="deptName" label="处室" width="110" />
      <el-table-column label="值班干部" width="120"><template #default="{row}">{{row.userName||'未排班'}}</template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160"><template #default="{row}">{{row.remark||'-'}}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.editing?'编辑排班':'新增排班'" width="480px">
      <el-form label-position="top">
        <el-form-item label="值班日期" required>
          <el-date-picker v-model="form.dutyDate" type="date" value-format="YYYY-MM-DD" style="width:100%" :disabled="form.editing" />
        </el-form-item>
        <el-form-item label="处室" required>
          <el-select v-model="form.deptId" style="width:100%" :disabled="form.editing">
            <el-option v-for="d in depts" :key="d.id" :label="`${d.name}（${d.formType==='FOOD'?'餐饮':'零售'}）`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="值班干部（可留空，走通用通知）">
          <el-select v-model="form.userId" filterable clearable placeholder="选择人员" style="width:100%" @change="onUserChange">
            <el-option v-for="u in users" :key="u.id" :label="u.displayName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="255" /></el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const canManage = computed(() => user.hasPermission('ops.patrol.config'))
const month = ref(new Date().toISOString().slice(0,7))
const rows = ref<any[]>([])
const users = ref<any[]>([])
const depts = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({editing:false,dutyDate:'',deptId:null,userId:null,userName:'',remark:''})

async function load(){
  loading.value=true
  try{
    const data:any = await opsPatrolApi.duties(month.value)
    rows.value = data?.rows || []
    users.value = data?.users || []
    depts.value = data?.depts || []
  }finally{ loading.value=false }
}
function edit(row?:any){
  Object.assign(form,{ editing:!!row, dutyDate:row?.dutyDate || new Date().toISOString().slice(0,10),
    deptId:row?.deptId || null, userId:row?.userId || null, userName:row?.userName || '', remark:row?.remark || '' })
  visible.value=true
}
function onUserChange(id:number){ form.userName = users.value.find(u=>u.id===id)?.displayName || '' }
async function save(){
  if(!form.dutyDate) return ElMessage.warning('请选择值班日期')
  if(!form.deptId) return ElMessage.warning('请选择处室')
  saving.value=true
  try{
    await opsPatrolApi.saveDuty({ dutyDate:form.dutyDate, deptId:form.deptId, userId:form.userId, userName:form.userName, remark:form.remark })
    ElMessage.success('已保存')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
async function remove(row:any){
  await ElMessageBox.confirm(`确认删除 ${row.dutyDate} ${row.deptName} 的排班？`,'删除确认',{type:'warning'})
  await opsPatrolApi.deleteDuty(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
</style>
