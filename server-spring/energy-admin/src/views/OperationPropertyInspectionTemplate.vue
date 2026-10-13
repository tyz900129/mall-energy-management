<template>
  <div class="page">
    <div class="page-header"><div class="page-title">物业清场检查模板</div><el-button v-if="canManage" type="primary" @click="open">新增模板版本</el-button></div>
    <el-alert title="模板按铺位业态匹配；未配置对应业态时自动使用 DEFAULT 通用模板。历史检查记录始终保留提交时的模板版本。" type="info" :closable="false" class="tip"/>
    <el-table :data="rows" border>
      <el-table-column prop="businessType" label="适用业态" min-width="110"/><el-table-column prop="name" label="模板名称" min-width="180"/><el-table-column prop="versionNo" label="版本" width="80"/>
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'使用中':'历史版'}}</el-tag></template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="100"><template #default="{row}"><el-button v-if="!row.enabled" link @click="enable(row)">启用</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="新增物业检查模板版本" width="760px">
      <el-form label-width="90px"><el-form-item label="模板名称"><el-input v-model="form.name"/></el-form-item><el-form-item label="适用业态"><el-input v-model="form.businessType" placeholder="DEFAULT 为通用模板"/></el-form-item>
        <el-form-item label="检查题"><div class="questions"><div v-for="(f,i) in form.fields" :key="i" class="question"><el-input v-model="f.label" placeholder="题目"/><el-input v-model="f.code" placeholder="唯一编码"/><el-select v-model="f.type"><el-option label="是否" value="BOOLEAN"/><el-option label="文本" value="TEXT"/><el-option label="数值" value="NUMBER"/></el-select><el-checkbox v-model="f.required">必填</el-checkbox><el-button link type="danger" @click="form.fields.splice(i,1)">删除</el-button></div><el-button @click="add">添加题目</el-button></div></el-form-item>
      </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并启用</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {operationPropertyApi} from '@/api/operation'
import {useUserStore} from '@/stores/user'
const user=useUserStore(),canManage=computed(()=>user.hasPermission('operation.property.template.manage'))
const rows=ref<any[]>([]),visible=ref(false),saving=ref(false),form=reactive<any>({name:'',businessType:'DEFAULT',fields:[]})
async function load(){rows.value=await operationPropertyApi.templates() as any||[]}
function add(){form.fields.push({code:`field${form.fields.length+1}`,label:'',type:'BOOLEAN',required:true,expected:true})}
function open(){Object.assign(form,{name:'',businessType:'DEFAULT',fields:[]});add();visible.value=true}
async function save(){if(!form.name.trim())return ElMessage.warning('请填写模板名称');if(!form.fields.length)return ElMessage.warning('请至少添加一个检查题');saving.value=true;try{await operationPropertyApi.saveTemplate(form);ElMessage.success('模板已保存并启用');visible.value=false;await load()}finally{saving.value=false}}
async function enable(row:any){await operationPropertyApi.enableTemplate(row.id);ElMessage.success('模板已启用');await load()}
onMounted(load)
</script>
<style scoped>.tip{margin-bottom:16px}.questions{width:100%}.question{display:grid;grid-template-columns:2fr 1.4fr 110px 70px 50px;gap:8px;margin-bottom:8px}@media(max-width:768px){.question{grid-template-columns:1fr}}</style>
