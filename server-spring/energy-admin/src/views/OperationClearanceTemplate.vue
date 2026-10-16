<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">日清表单模板</div>
      <el-button v-if="canManage" type="primary" @click="open()">新增模板</el-button>
    </div>
    <el-alert title="每个业态只保留一个当前模板，可直接编辑或删除；历史日清记录仍保留提交时的表单内容。" type="info" :closable="false" class="tip"/>
    <el-table :data="rows" border>
      <el-table-column prop="businessTypeLabel" label="适用业态" min-width="120"/>
      <el-table-column prop="name" label="模板名称" min-width="180"/>
      <el-table-column label="题目数" width="90"><template #default="{row}">{{row.fields?.length||0}}</template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180"><template #default="{row}">{{row.remark||'-'}}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id?'编辑日清模板':'新增日清模板'" width="760px">
      <el-form label-width="90px">
        <el-form-item label="模板名称"><el-input v-model="form.name"/></el-form-item>
        <el-form-item label="适用业态"><el-select v-model="form.businessType" style="width:100%"><el-option label="通用模板" value="DEFAULT"/><el-option v-for="item in businessTypes" :key="item.id" :label="item.itemLabel" :value="item.itemValue"/></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark"/></el-form-item>
        <el-form-item label="检查题">
          <div class="questions"><div v-for="(field,index) in form.fields" :key="index" class="question"><el-input v-model="field.label" placeholder="题目"/><el-input v-model="field.code" placeholder="英文编码"/><el-select v-model="field.type"><el-option label="是否" value="BOOLEAN"/><el-option label="文本" value="TEXT"/><el-option label="数值" value="NUMBER"/><el-option label="图片" value="IMAGE"/></el-select><el-checkbox v-model="field.required">必填</el-checkbox><el-button link type="danger" @click="form.fields.splice(index,1)">删除</el-button></div><el-button @click="add">添加题目</el-button></div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed,onMounted,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {operationTemplateApi} from '@/api/operation'
import {dictionaryApi,type DictionaryItem} from '@/api/system'
import {useUserStore} from '@/stores/user'

const user=useUserStore(),canManage=computed(()=>user.hasPermission('operation.clearance.template.manage'))
const rows=ref<any[]>([]),businessTypes=ref<DictionaryItem[]>([]),visible=ref(false),saving=ref(false)
const form=reactive<any>({id:null,name:'',businessType:'DEFAULT',remark:'',fields:[]})

async function load(){const[templates,dictionaries]:any=await Promise.all([operationTemplateApi.list(),dictionaryApi.list('BUSINESS_TYPE')]);rows.value=templates||[];businessTypes.value=dictionaries||[]}
function add(){let number=form.fields.length+1;while(form.fields.some((field:any)=>field.code===`field${number}`))number++;form.fields.push({code:`field${number}`,label:'',type:'BOOLEAN',required:true,expected:true})}
function open(row?:any){Object.assign(form,{id:row?.id||null,name:row?.name||'',businessType:row?.businessType||'DEFAULT',remark:row?.remark||'',fields:(row?.fields||[]).map((field:any)=>({...field}))});if(!form.fields.length)add();visible.value=true}
function payload(){return{name:form.name.trim(),businessType:form.businessType,remark:form.remark?.trim(),fields:form.fields.map((field:any)=>({...field,expected:field.type==='BOOLEAN'?field.expected:undefined}))}}
async function save(){if(!form.name.trim())return ElMessage.warning('请填写模板名称');if(!form.fields.length)return ElMessage.warning('请至少添加一个检查题');if(form.fields.some((field:any)=>!field.label?.trim()||!/^[A-Za-z][A-Za-z0-9_]{0,40}$/.test(field.code||'')))return ElMessage.warning('请完整填写题目，编码须以英文字母开头');if(new Set(form.fields.map((field:any)=>field.code)).size!==form.fields.length)return ElMessage.warning('题目编码不能重复');saving.value=true;try{form.id?await operationTemplateApi.update(form.id,payload()):await operationTemplateApi.save(payload());ElMessage.success(form.id?'模板已修改':'模板已新增');visible.value=false;await load()}finally{saving.value=false}}
async function remove(row:any){await ElMessageBox.confirm(`确认删除“${row.name}”吗？历史日清记录不会被删除。`,'删除模板',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'});await operationTemplateApi.remove(row.id);ElMessage.success('模板已删除');await load()}
onMounted(load)
</script>

<style scoped>.tip{margin-bottom:16px}.questions{width:100%}.question{display:grid;grid-template-columns:2fr 1.4fr 110px 70px 50px;gap:8px;margin-bottom:8px}@media(max-width:768px){.question{grid-template-columns:1fr}}</style>
