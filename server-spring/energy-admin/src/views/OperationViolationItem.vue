<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-title">违规项目管理</div><div class="page-note">维护物业清场异常时可选择的违规分类、违规内容和对应激励措施。</div></div>
      <div class="actions"><el-switch v-model="includeDisabled" active-text="显示停用" @change="load"/><el-button v-if="canManage" type="primary" @click="edit()">新增违规项目</el-button></div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="violationCategory" label="违规分类" width="150"/>
      <el-table-column prop="violationContent" label="违规内容" min-width="260" show-overflow-tooltip/>
      <el-table-column prop="incentiveMeasure" label="激励措施" min-width="240"><template #default="{row}">{{row.incentiveMeasure||'-'}}</template></el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="75"/>
      <el-table-column label="状态" width="85"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="130" fixed="right"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id?'编辑违规项目':'新增违规项目'" width="560px">
      <el-form label-position="top">
        <el-form-item label="违规分类" required><el-input v-model="form.violationCategory" maxlength="64" placeholder="例如：用电安全、消防安全、闭店管理"/></el-form-item>
        <el-form-item label="违规内容" required><el-input v-model="form.violationContent" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item>
        <el-form-item label="激励措施"><el-input v-model="form.incentiveMeasure" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写对应处理或激励措施"/></el-form-item>
        <div class="form-row"><el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999"/></el-form-item><el-form-item label="启用"><el-switch v-model="form.enabled"/></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {operationViolationApi} from '@/api/operation'
import {useUserStore} from '@/stores/user'
const user=useUserStore(),canManage=computed(()=>user.hasPermission('operation.violation.manage')),rows=ref<any[]>([]),loading=ref(false),saving=ref(false),visible=ref(false),includeDisabled=ref(true)
const form=reactive<any>({id:null,violationCategory:'',violationContent:'',incentiveMeasure:'',sortOrder:0,enabled:true})
async function load(){loading.value=true;try{rows.value=await operationViolationApi.list(includeDisabled.value) as any||[]}finally{loading.value=false}}
function edit(row?:any){Object.assign(form,row?{...row,enabled:!!row.enabled}:{id:null,violationCategory:'',violationContent:'',incentiveMeasure:'',sortOrder:0,enabled:true});visible.value=true}
async function save(){if(!form.violationCategory.trim())return ElMessage.warning('请填写违规分类');if(!form.violationContent.trim())return ElMessage.warning('请填写违规内容');saving.value=true;try{form.id?await operationViolationApi.update(form.id,form):await operationViolationApi.save(form);ElMessage.success('保存成功');visible.value=false;await load()}finally{saving.value=false}}
async function remove(row:any){await ElMessageBox.confirm(`确认删除违规项目“${row.violationContent}”？历史检查记录会保留当时快照。`,'删除确认',{type:'warning'});await operationViolationApi.remove(row.id);ElMessage.success('已删除');await load()}
onMounted(load)
</script>
<style scoped>.page-note{color:#909399;margin-top:6px;font-size:13px}.actions{display:flex;align-items:center;gap:14px}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:20px}@media(max-width:768px){.page-header{align-items:flex-start}.actions{width:100%;justify-content:space-between}.page{overflow-x:auto}.form-row{grid-template-columns:1fr}}</style>
