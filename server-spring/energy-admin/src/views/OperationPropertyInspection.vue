<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">物业清场检查</div><div class="page-note">扫码复查记录，可与商户当期日清记录进行核对；异常记录可一键转为追责单。</div></div><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" clearable @change="load"/></div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="periodDate" label="日清日期" width="115"/><el-table-column prop="stallCode" label="铺位" width="110"/><el-table-column prop="merchantName" label="商户" min-width="160"/><el-table-column prop="inspectorName" label="检查人" width="110"/><el-table-column prop="templateName" label="检查模板" min-width="150"/>
      <el-table-column label="商户已日清" width="110"><template #default="{row}"><el-tag :type="row.clearanceRecordId?'success':'danger'">{{row.clearanceRecordId?'是':'否'}}</el-tag></template></el-table-column>
      <el-table-column label="检查结果" width="100"><template #default="{row}"><el-tag :type="row.status==='NORMAL'?'success':'danger'">{{row.status==='NORMAL'?'正常':'异常'}}</el-tag></template></el-table-column>
      <el-table-column prop="exceptionDescription" label="异常说明" min-width="180"><template #default="{row}">{{row.exceptionDescription||'-'}}</template></el-table-column>
      <el-table-column label="检查时间" width="175"><template #default="{row}">{{formatDateTime(row.inspectedAt)}}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="110" fixed="right"><template #default="{row}"><el-button v-if="row.status==='ABNORMAL'" link type="danger" :loading="creating===row.id" @click="createOrder(row)">生成追责单</el-button></template></el-table-column>
    </el-table>
  </div>
</template>
<script setup lang="ts">
import{computed,onMounted,ref}from'vue';import{ElMessage,ElMessageBox}from'element-plus';import{operationAccountabilityApi,operationPropertyApi}from'@/api/operation';import{formatDateTime}from'@/utils/format';import{useUserStore}from'@/stores/user';import{useRouter}from'vue-router'
const rows=ref<any[]>([]),date=ref(''),loading=ref(false),creating=ref<number|null>(null),user=useUserStore(),router=useRouter(),canManage=computed(()=>user.hasPermission('operation.accountability.manage'))
async function load(){loading.value=true;try{rows.value=await operationPropertyApi.records(date.value||'')as any||[]}finally{loading.value=false}}
async function createOrder(row:any){await ElMessageBox.confirm(`确认将 ${row.stallCode} 的异常检查转为追责单？`,`生成追责单`,{type:'warning'});creating.value=row.id;try{const result:any=await operationAccountabilityApi.create(row.id,{issueDescription:row.exceptionDescription});ElMessage.success('追责单已生成');router.push(`/operation/accountability?open=${result.id}`)}finally{creating.value=null}}
onMounted(load)
</script>
<style scoped>.page-note{color:#909399;margin-top:6px;font-size:13px}@media(max-width:768px){.page-header{align-items:flex-start}.page{overflow-x:auto}}</style>
