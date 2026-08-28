<template><div class="page">
 <div class="page-header"><div><div class="page-title">基础字典管理</div><div class="text-muted subtitle">统一维护铺位使用的楼层和业态</div></div><el-button type="primary" @click="openCreate">新增{{typeLabel}}</el-button></div>
 <el-tabs v-model="type" @tab-change="load"><el-tab-pane v-for="t in types" :key="t.value" :label="t.label" :name="t.value"/></el-tabs>
 <div class="table-card"><el-table :data="rows" v-loading="loading" border>
  <el-table-column prop="itemValue" label="取值编码" min-width="140"/><el-table-column prop="itemLabel" label="显示名称" min-width="160"/><el-table-column prop="sortOrder" label="排序" width="90"/>
  <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'停用'}}</el-tag></template></el-table-column>
  <el-table-column prop="remark" label="备注" min-width="180"/><el-table-column label="操作" width="140" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-popconfirm title="删除后历史数据仍保留原值，确定删除？" @confirm="remove(row)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm></template></el-table-column>
 </el-table></div>
 <el-dialog v-model="visible" :title="form.id?'编辑字典项':`新增${typeLabel}`" width="500px"><el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
  <el-form-item label="分类"><el-input :model-value="typeLabel" disabled/></el-form-item><el-form-item label="取值编码" prop="itemValue"><el-input v-model="form.itemValue" placeholder="例如：F1"/></el-form-item><el-form-item label="显示名称" prop="itemLabel"><el-input v-model="form.itemLabel" placeholder="例如：一层"/></el-form-item><el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0"/></el-form-item><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0"/></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item>
 </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
</div></template>
<script setup lang="ts">
import{computed,onMounted,reactive,ref}from'vue';import{ElMessage,type FormInstance}from'element-plus';import{dictionaryApi,type DictionaryItem,type DictionaryType}from'@/api/system'
const types=[{value:'FLOOR',label:'楼层'},{value:'BUSINESS_TYPE',label:'业态'}] as {value:DictionaryType,label:string}[]
const type=ref<DictionaryType>('FLOOR'),rows=ref<DictionaryItem[]>([]),loading=ref(false),saving=ref(false),visible=ref(false),formRef=ref<FormInstance>();const form=reactive<any>({id:0,itemValue:'',itemLabel:'',sortOrder:0,status:1,remark:''});const typeLabel=computed(()=>types.find(t=>t.value===type.value)?.label||'')
const rules={itemValue:[{required:true,message:'请输入取值编码'}],itemLabel:[{required:true,message:'请输入显示名称'}]}
async function load(){loading.value=true;try{rows.value=await dictionaryApi.list(type.value,false) as any}finally{loading.value=false}}
function openCreate(){Object.assign(form,{id:0,itemValue:'',itemLabel:'',sortOrder:0,status:1,remark:''});visible.value=true}function openEdit(row:any){Object.assign(form,row);visible.value=true}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;saving.value=true;try{const data={dictType:type.value,itemValue:form.itemValue,itemLabel:form.itemLabel,sortOrder:form.sortOrder,status:form.status,remark:form.remark};if(form.id)await dictionaryApi.update(form.id,data);else await dictionaryApi.create(data);ElMessage.success('保存成功');visible.value=false;await load()}finally{saving.value=false}}
async function remove(row:any){await dictionaryApi.remove(row.id);ElMessage.success('删除成功');await load()}onMounted(load)
</script><style scoped>.subtitle{font-size:13px;margin-top:4px}</style>
