<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">铺位管理</div>
      <div class="header-actions"><el-button v-if="canExport" :loading="exporting" @click="onExport">导出Excel</el-button><el-button v-if="isAdmin" :loading="importing" @click="fileInput?.click()">导入Excel</el-button><input ref="fileInput" type="file" accept=".xlsx" hidden @change="onImport"/><el-button type="primary" @click="onAdd">新增铺位</el-button></div>
    </div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索铺位编码/名称" style="width: 240px" clearable @keyup.enter="load" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px; margin-left: 8px">
        <el-option label="在用" value="OCCUPIED" />
        <el-option label="空置" value="AVAILABLE" />
        <el-option label="维护" value="MAINTENANCE" />
        <el-option label="停用" value="DECOMMISSIONED" />
      </el-select>
      <el-select v-model="query.floorCode" placeholder="楼层" clearable style="width: 120px; margin-left: 8px"><el-option v-for="d in dictionaries.FLOOR" :key="d.id" :label="d.itemLabel" :value="d.itemValue"/></el-select>
      <el-select v-model="query.businessType" placeholder="业态" clearable style="width: 140px; margin-left: 8px"><el-option v-for="d in dictionaries.BUSINESS_TYPE" :key="d.id" :label="d.itemLabel" :value="d.itemValue"/></el-select>
      <el-button @click="load" style="margin-left: 8px">查询</el-button>
    </div>
    <div class="table-card">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="stallCode" label="铺位编码" width="120" />
        <el-table-column prop="stallName" label="当前商户（账户名称）" min-width="190" />
        <el-table-column prop="floorCode" label="楼层" width="100"><template #default="{row}">{{dictLabel('FLOOR',row.floorCode)}}</template></el-table-column>
        <el-table-column prop="area" label="面积" width="100" />
        <el-table-column prop="businessType" label="业态" width="130"><template #default="{row}">{{dictLabel('BUSINESS_TYPE',row.businessType)}}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="onEdit(row)">编辑</el-button>
            <el-button size="small" link type="primary" @click="router.push(`/stall/${row.id}/engineering`)">工程资料</el-button>
            <el-popconfirm v-if="row.status !== 'DECOMMISSIONED'" title="将铺位标记为停用？" @confirm="onDelete(row)">
              <template #reference><el-button size="small" link type="warning">停用</el-button></template>
            </el-popconfirm>
            <el-button v-if="isSuperAdmin" size="small" link type="danger" @click="onPermanentDelete(row)">永久删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; display: flex; justify-content: flex-end"
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑铺位' : '新增铺位'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="铺位编码"><el-input v-model="form.stallCode" /></el-form-item>
        <el-form-item label="当前商户"><el-input :model-value="form.stallName || '同步后自动获取'" disabled /><div class="form-tip">按铺位编码从源平台账户自动同步，与设备绑定无关</div></el-form-item>
        <el-form-item label="楼层"><el-select v-model="form.floorCode" style="width:100%"><el-option v-for="d in dictionaries.FLOOR" :key="d.id" :label="d.itemLabel" :value="d.itemValue"/></el-select></el-form-item>
        <el-form-item label="面积(㎡)"><el-input-number v-model="form.area" :min="0" /></el-form-item>
        <el-form-item label="业态"><el-select v-model="form.businessType" style="width:100%"><el-option v-for="d in dictionaries.BUSINESS_TYPE" :key="d.id" :label="d.itemLabel" :value="d.itemValue"/></el-select></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="在用 OCCUPIED" value="OCCUPIED" />
            <el-option label="空置 AVAILABLE" value="AVAILABLE" />
            <el-option label="维护 MAINTENANCE" value="MAINTENANCE" />
            <el-option label="停用 DECOMMISSIONED" value="DECOMMISSIONED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stallApi } from '@/api/merchant'
import { dictionaryApi, type DictionaryItem, type DictionaryType } from '@/api/system'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const rows = ref<any[]>([])
const router=useRouter()
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const exporting=ref(false),importing=ref(false),fileInput=ref<HTMLInputElement>();const userStore=useUserStore();const isAdmin=computed(()=>userStore.user?.roles?.some(r=>['SUPER_ADMIN','SYSTEM_ADMIN'].includes(r)));const isSuperAdmin=computed(()=>userStore.user?.roles?.includes('SUPER_ADMIN'));const canExport=computed(()=>isAdmin.value||userStore.user?.roles?.includes('VIEWER'))
const dictionaries=reactive<Record<DictionaryType,DictionaryItem[]>>({FLOOR:[],AREA:[],BUSINESS_TYPE:[],BUSINESS_CATEGORY:[],METER_LOCATION:[]})
function dictLabel(type:DictionaryType,value:string){return dictionaries[type].find(d=>d.itemValue===value)?.itemLabel||value||'-'}
const page = reactive({ pageNum: 1, pageSize: 20 })
const query = reactive<{ keyword: string; status: string; floorCode: string; businessType: string }>({
  keyword: '', status: '', floorCode: '', businessType: ''
})
const dialogVisible = ref(false)
const form = reactive<any>({
  id: 0, stallCode: '', stallName: '', floorCode: '',
  areaName: '', area: 0, businessType: '', businessCategory: '', status: 'AVAILABLE', remark: ''
})

async function load() {
  loading.value = true
  try {
    const r: any = await stallApi.page({
      ...page, ...query,
      status: query.status || undefined,
      floorCode: query.floorCode || undefined,
      businessType: query.businessType || undefined
    })
    rows.value = r.records || []
    total.value = Number(r.total) || 0
  } finally { loading.value = false }
}
function reset() { Object.assign(form, { id: 0, stallCode: '', stallName: '', floorCode: '', areaName: '', area: 0, businessType: '', businessCategory: '', status: 'AVAILABLE', remark: '' }) }
function onAdd() { reset(); dialogVisible.value = true }
function onEdit(row: any) { Object.assign(form, row); dialogVisible.value = true }

async function onSubmit() {
  submitting.value = true
  try {
    if (form.id) await stallApi.update(form.id, form)
    else await stallApi.create(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally { submitting.value = false }
}

async function onDelete(row: any) {
  await stallApi.softDelete(row.id)
  ElMessage.success('铺位已停用')
  load()
}
async function onPermanentDelete(row:any){await ElMessageBox.prompt(`永久删除将清除铺位 ${row.stallCode} 及全部本地设备绑定，且无法恢复。请输入铺位编码确认：`,'永久删除铺位',{type:'error',confirmButtonText:'永久删除',inputValidator:v=>v===row.stallCode||'请输入正确的铺位编码'});await stallApi.permanentDelete(row.id);ElMessage.success('铺位及本地绑定关系已永久删除，可重新创建');await load()}

async function onExport(){exporting.value=true;try{const blob:any=await stallApi.exportExcel({keyword:query.keyword||undefined,status:query.status||undefined,floorCode:query.floorCode||undefined,businessType:query.businessType||undefined});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`铺位数据_${new Date().toISOString().slice(0,10)}.xlsx`;a.click();URL.revokeObjectURL(url)}finally{exporting.value=false}}
async function onImport(e:Event){const input=e.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;importing.value=true;try{const r:any=await stallApi.importExcel(file);const errors=(r.errors||[]).join('\n');await ElMessageBox.alert(`成功：${r.successCount} 条\n失败：${r.failureCount} 条${errors?'\n\n'+errors:''}`,'导入结果',{confirmButtonText:'确定'});await load()}finally{importing.value=false;input.value=''}}

function statusLabel(s: string) {
  return { OCCUPIED: '在用', AVAILABLE: '空置', MAINTENANCE: '维护', DECOMMISSIONED: '停用' }[s] || s
}
function statusTag(s: string) {
  return ({ OCCUPIED: 'success', AVAILABLE: '', MAINTENANCE: 'warning', DECOMMISSIONED: 'info' } as any)[s] || ''
}
onMounted(async()=>{await Promise.all((['FLOOR','BUSINESS_TYPE'] as DictionaryType[]).map(async t=>{dictionaries[t]=await dictionaryApi.list(t) as any}));await load()})
</script>
<style scoped>.form-tip{font-size:12px;color:#909399;line-height:20px}.header-actions{display:flex;align-items:center;gap:8px}.header-actions :deep(.el-button+.el-button){margin-left:0}@media(max-width:768px){.header-actions{flex-wrap:wrap;justify-content:flex-end}}</style>
