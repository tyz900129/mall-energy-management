<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">水电费变更流程</div><div class="page-subtitle">查看开户、增铺和撤柜流程；商户施工申请请进入“商户施工流程”。</div></div><el-button v-if="canApply" type="primary" @click="router.push('/workflow/new-merchant')">水电费变更</el-button></div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="流程号/商户代码/名称" clearable style="width:240px" @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:190px;margin-left:8px"><el-option v-for="s in statuses" :key="s" :label="statusLabel(s)" :value="s" /></el-select>
      <el-button style="margin-left:8px" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div>
    <div class="table-card">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="workflowNo" label="流程编号" width="225" />
        <el-table-column prop="workflowType" label="业务类型" width="170"><template #default="{row}">{{typeLabel(row.workflowType)}}</template></el-table-column>
        <el-table-column prop="merchantCode" label="商户代码" width="100" />
        <el-table-column prop="merchantName" label="商户名称" min-width="150" />
        <el-table-column prop="stallCode" label="铺位" width="110" />
        <el-table-column prop="status" label="状态" width="160"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180"><template #default="{row}">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="router.push(`/workflow/${row.id}`)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" style="margin-top:12px;justify-content:flex-end" @change="load" />
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed,onMounted, reactive, ref } from 'vue'; import { useRouter } from 'vue-router'; import { workflowApi } from '@/api/workflow';import{useUserStore}from'@/stores/user'
import { formatDateTime } from '@/utils/format'
const router=useRouter(), rows=ref<any[]>([]), total=ref(0), loading=ref(false); const page=reactive({pageNum:1,pageSize:20}); const query=reactive({keyword:'',status:''})
const userStore=useUserStore();const canApply=computed(()=>userStore.hasPermission('workflow.create'))
const statuses=['DRAFT','FIELD_CHECK_PENDING','FIELD_CHECKING','FIELD_VERIFIED','FIELD_EXCEPTION','WAITING_OA_SUBMISSION','WAITING_PLATFORM_CHANGE','PLATFORM_VERIFYING','VERIFIED','PARTIAL_MATCH','EXCEPTION','CANCELLED','COMPLETED']
const labels:Record<string,string>={DRAFT:'草稿',FIELD_CHECK_PENDING:'待现场核验',FIELD_CHECKING:'核验中',FIELD_VERIFIED:'现场核验通过',FIELD_EXCEPTION:'现场异常',WAITING_OA_SUBMISSION:'待OA提交',WAITING_PLATFORM_CHANGE:'等待平台处理',PLATFORM_VERIFYING:'平台核对中',VERIFIED:'平台验证通过',PARTIAL_MATCH:'部分匹配',EXCEPTION:'异常',CANCELLED:'已取消',COMPLETED:'已完成'}
function statusLabel(s:string){return labels[s]||s} function statusType(s:string){return ['COMPLETED','VERIFIED'].includes(s)?'success':['EXCEPTION','FIELD_EXCEPTION'].includes(s)?'danger':['DRAFT','CANCELLED'].includes(s)?'info':'warning'}
function typeLabel(t:string){return ({NEW_MERCHANT:'新商户开户',EXISTING_MERCHANT_ADD_STALL:'已有商户增加铺位',MERCHANT_EXIT:'已有商户撤柜'} as Record<string,string>)[t]||t}
async function load(){loading.value=true;try{const r:any=await workflowApi.list({...page,...query,status:query.status||undefined});rows.value=r.records||[];total.value=Number(r.total)||0}finally{loading.value=false}}
function search(){page.pageNum=1;load()} function reset(){query.keyword='';query.status='';search()} onMounted(load)
</script>
