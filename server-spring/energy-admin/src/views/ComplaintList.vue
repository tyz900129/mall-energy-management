<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="编号/品牌/内容" clearable style="width:200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="业态">
          <el-select v-model="filters.businessType" clearable placeholder="全部" style="width:120px">
            <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-select v-model="filters.floor" clearable placeholder="全部" style="width:110px">
            <el-option v-for="item in floors" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="filters.category" clearable placeholder="全部" style="width:130px">
            <el-option v-for="item in dicts.CATEGORY" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width:110px">
            <el-option v-for="item in dicts.STATUS" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="filters.channel" clearable placeholder="全部" style="width:130px">
            <el-option v-for="item in dicts.CHANNEL" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="满意度">
          <el-select v-model="filters.satisfaction" clearable placeholder="全部" style="width:110px">
            <el-option v-for="item in dicts.SATISFACTION" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任商管">
          <el-select v-model="filters.managerStaffId" clearable filterable placeholder="全部" style="width:130px">
            <el-option v-for="item in managerStaff" :key="item.id" :label="item.staffName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="经办人">
          <el-select v-model="filters.handlerStaffId" clearable filterable placeholder="全部" style="width:130px">
            <el-option v-for="item in handlerStaff" :key="item.id" :label="item.staffName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束" style="width:230px" />
        </el-form-item>
        <el-form-item label="费用">
          <el-input-number v-model="filters.costMin" :min="0" :controls="false" placeholder="最小" style="width:90px" />
          <span class="muted">-</span>
          <el-input-number v-model="filters.costMax" :min="0" :controls="false" placeholder="最大" style="width:90px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-if="can('complaint.create')" type="primary" @click="openCreate">+ 登记客诉</el-button>
        <el-button v-if="can('complaint.create')" @click="copyQuickLink">复制移动登记链接</el-button>
        <el-button v-if="can('complaint.import')" @click="importVisible = true">导入历史数据</el-button>
        <el-button v-if="can('complaint.import')" @click="downloadTemplate">下载导入模板</el-button>
        <el-button v-if="can('complaint.import')" @click="openUpdateImport">更新导入</el-button>
        <el-button v-if="can('complaint.export')" @click="exportList">导出明细</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe size="small" @row-dblclick="openEdit">
        <el-table-column prop="complaintNo" label="编号" width="132" fixed />
        <el-table-column prop="occurDate" label="发生日期" width="106" />
        <el-table-column prop="brandName" label="品牌/铺位" min-width="140" show-overflow-tooltip />
        <el-table-column prop="businessTypeLabel" label="业态" width="90" />
        <el-table-column prop="floorLabel" label="楼层" width="80" />
        <el-table-column prop="categoryLabel" label="类别" width="110" />
        <el-table-column prop="channelLabel" label="渠道" width="110" show-overflow-tooltip />
        <el-table-column prop="content" label="案例内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="费用(元)" width="100" align="right">
          <template #default="{ row }">{{ money(row.totalCost) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="managerNames" label="责任商管" min-width="110" show-overflow-tooltip />
        <el-table-column prop="handlerNames" label="经办人" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ can('complaint.update') ? '编辑' : '详情' }}</el-button>
            <el-button v-if="can('complaint.update') && row.status === 'pending'" link type="success" @click="changeStatus(row, 'processing')">开始处理</el-button>
            <el-button v-if="can('complaint.update') && row.status === 'processing'" link type="success" @click="changeStatus(row, 'resolved')">标记已解决</el-button>
            <el-button v-if="can('complaint.update') && row.status === 'resolved'" link @click="changeStatus(row, 'processing')">退回处理</el-button>
            <el-button v-if="can('complaint.close') && row.status === 'resolved'" link type="warning" @click="openClose(row)">关闭</el-button>
            <el-button v-if="can('complaint.delete')" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" background layout="total, sizes, prev, pager, next" :total="total"
                     :page-sizes="[10, 20, 50, 100]" v-model:current-page="pageNum" v-model:page-size="pageSize"
                     @current-change="load" @size-change="load" />
    </el-card>

    <ComplaintFormDrawer v-model="drawerVisible" :complaint-id="editingId" @saved="load" />

    <el-dialog v-model="closeVisible" title="关闭客诉" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="满意度">
          <el-radio-group v-model="closeForm.satisfaction">
            <el-radio v-for="item in dicts.SATISFACTION" :key="item.itemValue" :value="item.itemValue">{{ item.itemLabel }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="closeForm.note" type="textarea" :rows="2" placeholder="关闭说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closeSaving" @click="submitClose">确认关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入历史客诉" width="620px" append-to-body>
      <el-alert type="info" :closable="false" show-icon title="请先下载导入模板，按模板列填写；品牌匹配不到时将自动新增为非铺位品牌。" />
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="onImportFile" :on-exceed="onImportExceed" style="margin-top:12px">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 Excel 到此处，或<em>点击选择</em></div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <el-alert :type="importResult.failCount ? 'warning' : 'success'" :closable="false" show-icon
                  :title="`导入完成：成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条`" />
        <div v-if="importResult.errors?.length" class="error-list">
          <div v-for="item in importResult.errors" :key="item.row" class="error-item">
            第 {{ item.row }} 行（{{ item.brand || '-' }}）：{{ item.reason }}
          </div>
        </div>
        <el-button v-if="importResult.errorToken" type="warning" plain style="margin-top:8px" @click="downloadErrors">下载错误行明细</el-button>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFileRaw" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="updateImportVisible" title="更新导入（费用修正）" width="760px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon
                title="仅更新「公司承担费用 / 商户承担费用 / 无需承担费用」三列，按表头名识别列；空单元格表示不修改；默认不支持已解决/已关闭单据，未命中编号的行不会新增。" />
      <div v-if="can('complaint.import.force')" class="allow-closed-block">
        <el-checkbox v-model="updateImportAllowClosed" @change="onAllowClosedChange">
          允许修正已关闭单据（仅费用）
        </el-checkbox>
        <el-alert v-if="updateImportAllowClosed" type="error" :closable="false" show-icon
                  title="已开启：将覆盖「已解决/已关闭」单据的费用三列。状态、关闭时间、处理天数、参与人、品牌与创建时间均不会改动；执行前需再次输入确认文字。" />
      </div>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="onUpdateImportFile"
                 :on-exceed="onImportExceed" style="margin-top:12px">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 Excel 到此处，或<em>点击选择</em></div>
      </el-upload>

      <div v-if="updateImportPreview" class="import-result">
        <el-alert :type="updateImportPreview.failCount ? 'warning' : 'info'" :closable="false" show-icon
                  :title="`预演：共 ${updateImportPreview.totalRows} 行，命中 ${updateImportPreview.matchedCount} 行，未命中 ${updateImportPreview.unmatchedCount} 行，将变更 ${updateImportPreview.changedCount} 行，无变化 ${updateImportPreview.unchangedCount} 行，失败 ${updateImportPreview.failCount} 行${updateImportPreview.allowClosed ? '（已包含已解决/已关闭单据）' : ''}`" />
        <div class="preview-summary">
          商户承担费用变更 {{ updateImportPreview.merchantCostChangedCount }} 行（净增 {{ formatMoney(updateImportPreview.merchantCostDelta) }} 元）；
          公司承担费用变更 {{ updateImportPreview.companyCostChangedCount }} 行（净增 {{ formatMoney(updateImportPreview.companyCostDelta) }} 元）；
          无需承担费用变更 {{ updateImportPreview.noCostChangedCount }} 行
        </div>
        <div v-if="updateImportPreview.preview?.length" class="preview-block">
          <div class="preview-title">变更预览（最多展示 200 行）</div>
          <el-table :data="updateImportPreview.preview" size="small" border max-height="260">
            <el-table-column prop="row" label="Excel行" width="80" />
            <el-table-column prop="complaintNo" label="编号" width="140" />
            <el-table-column prop="brandName" label="品牌" min-width="120" show-overflow-tooltip />
            <el-table-column prop="statusLabel" label="状态" width="90" />
            <el-table-column label="改前 → 改后" min-width="240">
              <template #default="{ row }">
                <div v-for="item in row.changes" :key="item.field" class="change-item">
                  {{ item.label }}：{{ item.from }} → {{ item.to }}
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="updateImportPreview.errors?.length" class="error-list">
          <div v-for="item in updateImportPreview.errors" :key="item.row" class="error-item">
            第 {{ item.row }} 行（{{ item.complaintNo || item.brand || '-' }}）：{{ item.reason }}
          </div>
        </div>
        <el-button v-if="updateImportPreview.errorToken" type="warning" plain style="margin-top:8px"
                   @click="downloadUpdateErrors">下载错误行明细</el-button>
      </div>

      <div v-if="updateImportResult" class="import-result">
        <el-alert :type="updateImportResult.failCount ? 'warning' : 'success'" :closable="false" show-icon
                  :title="`更新完成：成功 ${updateImportResult.successCount} 行，失败 ${updateImportResult.failCount} 行，其中费用变更 ${updateImportResult.changedCount} 行`" />
        <div v-if="updateImportResult.errors?.length" class="error-list">
          <div v-for="item in updateImportResult.errors" :key="item.row" class="error-item">
            第 {{ item.row }} 行（{{ item.complaintNo || item.brand || '-' }}）：{{ item.reason }}
          </div>
        </div>
        <el-button v-if="updateImportResult.errorToken" type="warning" plain style="margin-top:8px"
                   @click="downloadUpdateErrors">下载错误行明细</el-button>
      </div>

      <template #footer>
        <el-button @click="updateImportVisible = false">关闭</el-button>
        <el-button :loading="updateImporting" :disabled="!updateImportFileRaw" @click="runUpdateImport(true)">预览变更</el-button>
        <el-button type="primary" :loading="updateImporting"
                   :disabled="!updateImportFileRaw || !updateImportPreview || updateImportPreview.changedCount === 0 || !!updateImportResult"
                   @click="confirmUpdateImport">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { complaintApi, saveBlob } from '@/api/complaint'
import { useUserStore } from '@/stores/user'
import ComplaintFormDrawer from './ComplaintFormDrawer.vue'

const userStore = useUserStore()
const can = (code: string) => userStore.hasPermission(code)

const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const dateRange = ref<string[]>([])
const businessTypes = ref<any[]>([])
const floors = ref<any[]>([])
const handlerStaff = ref<any[]>([])
const managerStaff = ref<any[]>([])
const dicts = reactive<Record<string, any[]>>({ CATEGORY: [], CHANNEL: [], SATISFACTION: [], STATUS: [] })

const filters = reactive<any>({
  keyword: '', businessType: '', floor: '', category: '', status: '', channel: '', satisfaction: '',
  managerStaffId: null, handlerStaffId: null, costMin: null, costMax: null
})

const drawerVisible = ref(false)
const editingId = ref<number | null>(null)
const closeVisible = ref(false)
const closeSaving = ref(false)
const closingRow = ref<any>(null)
const closeForm = reactive({ satisfaction: 'SATISFIED', note: '' })

const importVisible = ref(false)
const importing = ref(false)
const importFileRaw = ref<File | null>(null)
const importResult = ref<any>(null)

const updateImportVisible = ref(false)
const updateImporting = ref(false)
const updateImportFileRaw = ref<File | null>(null)
const updateImportPreview = ref<any>(null)
const updateImportResult = ref<any>(null)
const updateImportAllowClosed = ref(false)

onMounted(async () => {
  await loadOptions()
  await load()
})

async function loadOptions() {
  const [biz, dic, handlers, managers] = await Promise.all([
    complaintApi.businessTypes() as Promise<any>,
    complaintApi.dicts() as Promise<any>,
    complaintApi.staffOptions('HANDLER') as Promise<any>,
    complaintApi.staffOptions('MANAGER') as Promise<any>
  ])
  businessTypes.value = biz.businessTypes || []
  floors.value = biz.floors || []
  handlerStaff.value = handlers || []
  managerStaff.value = managers || []
  dicts.CATEGORY = (dic || []).filter((d: any) => d.dictType === 'CATEGORY')
  dicts.CHANNEL = (dic || []).filter((d: any) => d.dictType === 'CHANNEL')
  dicts.SATISFACTION = (dic || []).filter((d: any) => d.dictType === 'SATISFACTION')
  dicts.STATUS = (dic || []).filter((d: any) => d.dictType === 'STATUS')
}

function params() {
  return {
    ...filters,
    occurDateStart: dateRange.value?.[0] || null,
    occurDateEnd: dateRange.value?.[1] || null,
    pageNum: pageNum.value,
    pageSize: pageSize.value
  }
}

async function load() {
  loading.value = true
  try {
    const data: any = await complaintApi.page(params())
    rows.value = data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}

function search() { pageNum.value = 1; load() }
function reset() {
  Object.assign(filters, { keyword: '', businessType: '', floor: '', category: '', status: '', channel: '', satisfaction: '', managerStaffId: null, handlerStaffId: null, costMin: null, costMax: null })
  dateRange.value = []
  search()
}

function openCreate() { editingId.value = null; drawerVisible.value = true }
function openEdit(row: any) { editingId.value = row.id; drawerVisible.value = true }

async function changeStatus(row: any, status: string) {
  await complaintApi.changeStatus(row.id, status)
  ElMessage.success('状态已更新')
  load()
}

function openClose(row: any) {
  closingRow.value = row
  closeForm.satisfaction = row.satisfaction || 'SATISFIED'
  closeForm.note = ''
  closeVisible.value = true
}

async function submitClose() {
  closeSaving.value = true
  try {
    await complaintApi.close(closingRow.value.id, closeForm.satisfaction, closeForm.note)
    ElMessage.success('客诉已关闭')
    closeVisible.value = false
    load()
  } finally { closeSaving.value = false }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除客诉 ${row.complaintNo}？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await complaintApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function copyQuickLink() {
  const link = `${location.origin}/complaints/quick`
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success('移动登记链接已复制：' + link)
  } catch {
    ElMessage.info(link)
  }
}

async function downloadTemplate() {
  const blob: any = await complaintApi.template()
  saveBlob(blob, '客诉导入模板.xlsx')
}

async function exportList() {
  const p = params()
  delete (p as any).pageNum
  delete (p as any).pageSize
  const blob: any = await complaintApi.export(p)
  saveBlob(blob, '客诉明细.xlsx')
}

function onImportFile(file: any) { importFileRaw.value = file.raw }
function onImportExceed() { ElMessage.warning('一次只能导入一个文件') }

async function doImport() {
  if (!importFileRaw.value) return
  importing.value = true
  try {
    importResult.value = await complaintApi.importExcel(importFileRaw.value)
    ElMessage.success('导入完成')
    load()
  } finally { importing.value = false }
}

async function downloadErrors() {
  const blob: any = await complaintApi.importErrors(importResult.value.errorToken)
  saveBlob(blob, '客诉导入错误明细.xlsx')
}

function openUpdateImport() {
  updateImportFileRaw.value = null
  updateImportPreview.value = null
  updateImportResult.value = null
  updateImportAllowClosed.value = false
  updateImportVisible.value = true
}

function formatMoney(value: number | string | null | undefined) {
  const n = Number(value || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 切换「允许修正已关闭单据」后，旧预演结果不再适用，需重新预览。
function onAllowClosedChange() {
  updateImportPreview.value = null
  updateImportResult.value = null
}

function onUpdateImportFile(file: any) {
  updateImportFileRaw.value = file.raw
  updateImportPreview.value = null
  updateImportResult.value = null
}

async function runUpdateImport(dryRun: boolean) {
  if (!updateImportFileRaw.value) return
  updateImporting.value = true
  try {
    const data: any = await complaintApi.updateImportExcel(updateImportFileRaw.value, dryRun, updateImportAllowClosed.value)
    if (dryRun) {
      updateImportPreview.value = data
      ElMessage.success(`预演完成：将变更 ${data.changedCount} 行`)
    } else {
      updateImportResult.value = data
      ElMessage.success(`更新完成：成功 ${data.successCount} 行，失败 ${data.failCount} 行`)
      load()
    }
  } finally {
    updateImporting.value = false
  }
}

async function confirmUpdateImport() {
  const count = updateImportPreview.value?.changedCount || 0
  if (updateImportAllowClosed.value) {
    try {
      await ElMessageBox.prompt(
        `本次将修正「已解决/已关闭」单据的费用，共 ${count} 条；状态、关闭时间、处理天数与参与人不会改动。请输入「确认修正已关闭单据」继续。`,
        '高风险操作确认',
        {
          type: 'warning',
          confirmButtonText: '确认执行',
          cancelButtonText: '取消',
          inputPlaceholder: '确认修正已关闭单据',
          inputValidator: (value: string) => value === '确认修正已关闭单据' || '确认文字不匹配，请完整输入「确认修正已关闭单据」'
        }
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(`确认按预演结果更新 ${count} 条客诉的费用？`, '确认更新', { type: 'warning' })
    } catch {
      return
    }
  }
  await runUpdateImport(false)
}

async function downloadUpdateErrors() {
  const token = updateImportResult.value?.errorToken || updateImportPreview.value?.errorToken
  if (!token) return
  const blob: any = await complaintApi.importErrors(token)
  saveBlob(blob, '客诉更新导入错误明细.xlsx')
}

function money(value: any) {
  return Number(value || 0).toFixed(2)
}

function statusType(status: string): any {
  const map: Record<string, string> = { pending: 'warning', processing: '', resolved: 'success', closed: 'info' }
  return map[status] || ''
}
</script>

<style scoped>
.page { padding: 4px; }
.filter-form { margin-bottom: 4px; }
.toolbar { display: flex; gap: 8px; margin: 4px 0 12px; flex-wrap: wrap; }
.muted { color: #909399; margin: 0 4px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.allow-closed-block { margin-top: 10px; }
.preview-summary { margin-top: 10px; font-size: 13px; color: #606266; }
.allow-closed-block .el-alert { margin-top: 6px; }
.import-result { margin-top: 14px; }
.error-list { max-height: 180px; overflow: auto; background: #fafafa; padding: 8px; margin-top: 8px; font-size: 12px; }
.error-item { color: #f56c6c; line-height: 1.8; }
.preview-block { margin-top: 10px; }
.preview-title { font-size: 12px; color: #606266; margin: 6px 0; }
.change-item { font-size: 12px; line-height: 1.7; }

@media (max-width: 768px) {
  .page { padding: 0; }
  .filter-form :deep(.el-form-item) { display: flex; width: 100%; margin-right: 0; }
  .filter-form :deep(.el-form-item__content) { flex: 1; }
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-input),
  .filter-form :deep(.el-date-editor),
  .filter-form :deep(.el-input-number) { width: 100% !important; }
  .toolbar .el-button { margin-left: 0; }
  .pager { justify-content: center; }
}
</style>
