<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">分类字典</div>
        <div class="page-note">巡场异常上报时的问题分类，以及巡场点位的类型，均可在此维护；停用后不再出现在表单中，历史数据保留原文字。</div>
      </div>
      <div class="actions">
        <el-button v-if="canManage" type="primary" @click="edit()">新增{{ tabLabel }}</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="问题分类" name="category" />
      <el-tab-pane label="点位类型" name="pointType" />
    </el-tabs>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="itemLabel" :label="tabLabel + '名称'" min-width="180" />
      <el-table-column prop="sortOrder" label="排序" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="240"><template #default="{row}">{{row.remark||'-'}}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="150" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="(form.id?'编辑':'新增') + tabLabel" width="520px">
      <el-form label-position="top">
        <el-form-item :label="tabLabel + '名称'" required>
          <el-input v-model="form.itemLabel" maxlength="32" :placeholder="tab==='category'?'例如：设施设备':'例如：公共区域'" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" /></el-form-item>
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
const tab = ref<'category'|'pointType'>('category')
const tabLabel = computed(() => tab.value === 'category' ? '问题分类' : '点位类型')
const rows = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({id:null,itemLabel:'',sortOrder:0,status:1,remark:''})

function api(){
  return tab.value === 'category'
    ? { list: patrolApi.categories, save: patrolApi.saveCategory, remove: patrolApi.removeCategory }
    : { list: patrolApi.pointTypes, save: patrolApi.savePointType, remove: patrolApi.removePointType }
}
async function load(){
  loading.value=true
  try{ rows.value = await api().list() as any || [] }
  finally{ loading.value=false }
}
function onTabChange(){ load() }
function edit(row?:any){
  Object.assign(form,{id:row?.id||null,itemLabel:row?.itemLabel||'',sortOrder:row?.sortOrder??0,status:row?.status??1,remark:row?.remark||''})
  visible.value=true
}
async function save(){
  if(!form.itemLabel.trim()) return ElMessage.warning(`请输入${tabLabel.value}名称`)
  saving.value=true
  try{
    await api().save({...form,itemLabel:form.itemLabel.trim()})
    ElMessage.success('已保存')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
async function remove(row:any){
  await ElMessageBox.confirm(`确认删除${tabLabel.value}「${row.itemLabel}」？历史数据仍保留原文字。`,'删除确认',{type:'warning'})
  await api().remove(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
@media(max-width:768px){.page{overflow-x:auto}.page-header{align-items:flex-start}.actions{flex-wrap:wrap}}
</style>
