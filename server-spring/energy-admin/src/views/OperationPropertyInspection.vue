<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">物业清场检查</div><div class="page-note">固定表单检查记录；异常记录包含违规项目及现场照片，可一键生成追责单。</div></div><el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" clearable @change="load"/></div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="periodDate" label="日清日期" width="115"/><el-table-column prop="stallCode" label="铺位" width="110"/><el-table-column prop="merchantName" label="商户" min-width="150"/><el-table-column prop="inspectorName" label="检查人" width="105"/><el-table-column prop="inspectorPhone" label="检查人电话" width="130"><template #default="{row}">{{row.inspectorPhone||'-'}}</template></el-table-column>
      <el-table-column label="商户已日清" width="110"><template #default="{row}"><el-tag :type="row.clearanceRecordId?'success':'danger'">{{row.clearanceRecordId?'是':'否'}}</el-tag></template></el-table-column>
      <el-table-column label="检查结果" width="100"><template #default="{row}"><el-tag :type="row.status==='NORMAL'?'success':'danger'">{{row.status==='NORMAL'?'正常':'异常'}}</el-tag></template></el-table-column>
      <el-table-column prop="exceptionDescription" label="违规问题" min-width="220"><template #default="{row}">{{row.exceptionDescription||'-'}}</template></el-table-column>
      <el-table-column label="照片" width="70"><template #default="{row}">{{row.photos?.length||0}} 张</template></el-table-column>
      <el-table-column label="检查时间" width="175"><template #default="{row}">{{formatDateTime(row.inspectedAt)}}</template></el-table-column>
      <el-table-column label="操作" width="170" fixed="right"><template #default="{row}"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button v-if="canManage&&row.status==='ABNORMAL'" link type="danger" :loading="creating===row.id" @click="createOrder(row)">生成追责单</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="物业清场检查详情" width="680px" @closed="releasePhotos">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="检查日期">{{detail.periodDate}}</el-descriptions-item><el-descriptions-item label="楼层铺位">{{detail.floorName||''}} {{detail.stallCode}}</el-descriptions-item>
        <el-descriptions-item label="检查人">{{detail.inspectorName||'-'}}</el-descriptions-item><el-descriptions-item label="检查人电话">{{detail.inspectorPhone||'-'}}</el-descriptions-item>
        <el-descriptions-item label="商户代码">{{detail.merchantCode||'-'}}</el-descriptions-item><el-descriptions-item label="商户名称">{{detail.merchantName||'-'}}</el-descriptions-item>
        <el-descriptions-item label="商户电话">{{detail.merchantPhone||'-'}}</el-descriptions-item><el-descriptions-item label="检查结果"><el-tag :type="detail.status==='NORMAL'?'success':'danger'">{{detail.status==='NORMAL'?'正常':'异常'}}</el-tag></el-descriptions-item>
      </el-descriptions>
      <template v-if="detail.status==='ABNORMAL'"><h4>违规项目</h4><div v-for="item in detail.violationItems||[]" :key="item.id" class="violation"><b>{{item.violationCategory}}：</b>{{item.violationContent}}<div v-if="item.incentiveMeasure">激励措施：{{item.incentiveMeasure}}</div></div><h4>现场照片</h4><div v-loading="photoLoading" class="photos"><el-image v-for="(url,index) in photoUrls" :key="url" :src="url" :preview-src-list="photoUrls" :initial-index="index" fit="cover"/></div></template>
      <template #footer><el-button type="primary" @click="detailVisible=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import{computed,onMounted,ref}from'vue';import{ElMessage,ElMessageBox}from'element-plus';import{operationAccountabilityApi,operationPropertyApi,operationFormImageApi}from'@/api/operation';import{formatDateTime}from'@/utils/format';import{useUserStore}from'@/stores/user';import{useRouter}from'vue-router'
const rows=ref<any[]>([]),date=ref(''),loading=ref(false),creating=ref<number|null>(null),user=useUserStore(),router=useRouter(),canManage=computed(()=>user.hasPermission('operation.accountability.manage')),detailVisible=ref(false),detail=ref<any>({}),photoUrls=ref<string[]>([]),photoLoading=ref(false)
async function load(){loading.value=true;try{rows.value=await operationPropertyApi.records(date.value||'')as any||[]}finally{loading.value=false}}
async function showDetail(row:any){releasePhotos();detail.value=row;detailVisible.value=true;if(row.photos?.length){photoLoading.value=true;try{for(const photo of row.photos){if(!photo.id)continue;const blob:any=await operationFormImageApi.image(photo.id);photoUrls.value.push(URL.createObjectURL(blob))}}finally{photoLoading.value=false}}}
function releasePhotos(){photoUrls.value.forEach(URL.revokeObjectURL);photoUrls.value=[]}
async function createOrder(row:any){await ElMessageBox.confirm(`确认将 ${row.stallCode} 的异常检查转为追责单？`,`生成追责单`,{type:'warning'});creating.value=row.id;try{const result:any=await operationAccountabilityApi.create(row.id,{issueDescription:row.exceptionDescription});ElMessage.success('追责单已生成');router.push(`/operation/accountability?open=${result.id}`)}finally{creating.value=null}}
onMounted(load)
</script>
<style scoped>.page-note{color:#909399;margin-top:6px;font-size:13px}.violation{padding:10px 12px;background:#fff5f5;border-left:3px solid #f56c6c;margin:8px 0;line-height:1.6}.photos{display:flex;flex-wrap:wrap;gap:10px;min-height:40px}.photos .el-image{width:130px;height:100px;border-radius:6px}@media(max-width:768px){.page-header{align-items:flex-start}.page{overflow-x:auto}:deep(.el-dialog){width:calc(100% - 20px)!important}:deep(.el-descriptions__cell){display:block;width:100%!important}.photos .el-image{width:calc(50% - 5px);height:120px}}</style>
