<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">时段设置</div>
        <div class="page-note">配置各时段的推送与催办参数，保存后次日生效（已生成任务不受影响）。「清场之后」按各处室闭店时间相对计算。</div>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="编码" width="70" />
      <el-table-column prop="name" label="时段名称" width="120" />
      <el-table-column label="时间" min-width="170">
        <template #default="{row}">
          <span v-if="row.relativeToClose">闭店 +{{row.offsetStartMin}} ~ +{{row.offsetEndMin}} 分钟</span>
          <span v-else>{{row.startTime}} - {{row.endTime}}</span>
          <el-tag v-if="row.isHandover" size="small" type="warning" style="margin-left:6px">交接</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="推送提前" width="90"><template #default="{row}">{{row.pushAdvanceMinutes}} 分钟</template></el-table-column>
      <el-table-column label="未开始催办" width="100"><template #default="{row}">开始后 {{row.urgeAfterMinutes}} 分</template></el-table-column>
      <el-table-column label="截止提醒" width="100"><template #default="{row}">提前 {{row.remindBeforeEndMinutes}} 分</template></el-table-column>
      <el-table-column label="判逾期" width="100"><template #default="{row}">结束后 {{row.overdueAfterMinutes}} 分</template></el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="90" fixed="right">
        <template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="`编辑 ${form.name}`" width="520px">
      <el-form label-position="top">
        <el-row :gutter="16" v-if="!form.relativeToClose">
          <el-col :span="12"><el-form-item label="开始时间"><el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结束时间"><el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16" v-else>
          <el-col :span="12"><el-form-item label="闭店后开始（分钟）"><el-input-number v-model="form.offsetStartMin" :min="0" :max="240" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="闭店后结束（分钟）"><el-input-number v-model="form.offsetEndMin" :min="0" :max="360" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="推送提前（分钟）"><el-input-number v-model="form.pushAdvanceMinutes" :min="0" :max="120" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开始后催办（分钟）"><el-input-number v-model="form.urgeAfterMinutes" :min="1" :max="120" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="截止前提醒（分钟）"><el-input-number v-model="form.remindBeforeEndMinutes" :min="1" :max="120" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结束后判逾期（分钟）"><el-input-number v-model="form.overdueAfterMinutes" :min="1" :max="240" style="width:100%" /></el-form-item></el-col>
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
const canManage = computed(() => user.hasPermission('ops.patrol.config'))
const rows = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({})

async function load(){ loading.value=true; try{ rows.value = await opsPatrolApi.slots() as any || [] } finally{ loading.value=false } }
function edit(row:any){
  Object.assign(form,{ id:row.id, name:row.name, relativeToClose:!!row.relativeToClose,
    startTime:row.startTime, endTime:row.endTime, offsetStartMin:row.offsetStartMin, offsetEndMin:row.offsetEndMin,
    pushAdvanceMinutes:row.pushAdvanceMinutes, urgeAfterMinutes:row.urgeAfterMinutes,
    remindBeforeEndMinutes:row.remindBeforeEndMinutes, overdueAfterMinutes:row.overdueAfterMinutes, enabled:!!row.enabled })
  visible.value=true
}
async function save(){
  saving.value=true
  try{
    await opsPatrolApi.updateSlot(form.id, form)
    ElMessage.success('保存成功，次日生效')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
onMounted(load)
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
</style>
