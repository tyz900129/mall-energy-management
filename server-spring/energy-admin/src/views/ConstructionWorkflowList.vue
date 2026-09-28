<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-title">商户施工流程</div><div class="page-subtitle">管理入场装修、撤场施工、现场巡检、验收和施工许可证。</div></div>
      <el-button v-if="canCreate" type="primary" @click="router.push('/construction/new')">发起施工申请</el-button>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="流程号/铺位号/商户名称" clearable @keyup.enter="search" />
      <el-select v-model="query.workflowType" placeholder="流程类型" clearable><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="query.status" placeholder="状态" clearable><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-button type="primary" plain @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div>
    <div class="table-card desktop-table">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="workflowNo" label="流程编号" min-width="210" />
        <el-table-column prop="permitNo" label="许可证编号" min-width="150" />
        <el-table-column label="流程类型" width="130"><template #default="{row}">{{ typeLabel(row.workflowType) }}</template></el-table-column>
        <el-table-column label="施工位置" min-width="150"><template #default="{row}">{{ locationLabel(row) }}</template></el-table-column>
        <el-table-column prop="merchantName" label="商户名称" min-width="150" />
        <el-table-column label="施工周期" min-width="285"><template #default="{row}">{{ formatDateTime(row.constructionStartTime) }} 至 {{ formatDateTime(row.constructionEndTime) }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">详情</el-button></template></el-table-column>
      </el-table>
    </div>
    <div class="mobile-cards" v-loading="loading">
      <el-card v-for="row in rows" :key="row.id" shadow="never" @click="open(row)">
        <div class="card-head"><b>{{ typeLabel(row.workflowType) }}</b><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></div>
        <div class="location">{{ locationLabel(row) }} · {{ row.merchantName }}</div>
        <div class="muted">{{ formatDateTime(row.constructionStartTime) }} 至 {{ formatDateTime(row.constructionEndTime) }}</div>
        <div class="muted">{{ row.workflowNo }}</div>
      </el-card>
      <el-empty v-if="!loading && !rows.length" description="暂无施工流程" />
    </div>
    <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[10,20,50]"
      layout="total, sizes, prev, pager, next" class="pager" @change="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { constructionApi, type ConstructionWorkflow } from '@/api/construction'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const router = useRouter(), userStore = useUserStore()
const rows = ref<ConstructionWorkflow[]>([]), total = ref(0), loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 20 })
const query = reactive({ keyword: '', workflowType: '', status: '' })
const canCreate = computed(() => userStore.hasPermission('construction.create'))
const typeOptions = [
  { value:'MERCHANT_CONSTRUCTION_ENTRY', label:'施工入场申请' },
  { value:'MERCHANT_CONSTRUCTION_EXIT', label:'撤场施工申请' }
]
const statusOptions = [{value:'IN_PROGRESS',label:'施工中'},{value:'PENDING_ACCEPTANCE',label:'待验收'},{value:'REJECTED',label:'验收不合格'},{value:'ARCHIVED',label:'已归档'},{value:'CANCELLED',label:'已取消'}]
function typeLabel(value:string){ return typeOptions.find(item=>item.value===value)?.label || value }
function statusLabel(value:string){ return statusOptions.find(item=>item.value===value)?.label || value }
function statusType(value:string):'primary'|'success'|'warning'|'info'|'danger'{ return value==='ARCHIVED'?'success':value==='PENDING_ACCEPTANCE'?'warning':value==='REJECTED'?'danger':value==='CANCELLED'?'info':'primary' }
function locationLabel(row:any){ return `${row.floorName || row.floorCode || '未设置楼层'} · ${row.stallCode || '-'}` }
function open(row:any){ router.push(`/construction/${row.id}`) }
async function load(){ loading.value=true; try { const result:any=await constructionApi.list({...page,...query,workflowType:query.workflowType||undefined,status:query.status||undefined,keyword:query.keyword||undefined});rows.value=result.records||[];total.value=Number(result.total)||0 } finally { loading.value=false } }
function search(){ page.pageNum=1;load() }
function reset(){ Object.assign(query,{keyword:'',workflowType:'',status:''});search() }
onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; gap:10px; flex-wrap:wrap; margin-bottom:12px; }
.toolbar :deep(.el-input) { width:250px; }.toolbar :deep(.el-select){width:170px}
.pager { margin-top:12px; justify-content:flex-end; }.mobile-cards{display:none}
@media (max-width:768px){
  .page{padding:12px}.page-header{align-items:flex-start}.page-header :deep(.el-button){min-height:40px}
  .toolbar{display:grid;grid-template-columns:1fr 1fr;gap:8px}.toolbar :deep(.el-input),.toolbar :deep(.el-select){width:100%}.toolbar :deep(.el-input){grid-column:1/-1}
  .desktop-table{display:none}.mobile-cards{display:grid;gap:10px}.mobile-cards :deep(.el-card__body){padding:14px}.card-head{display:flex;justify-content:space-between;align-items:center}.location{margin:10px 0 7px}.muted{font-size:12px;color:#909399;line-height:1.7}.pager{justify-content:center;overflow:auto}
}
</style>
