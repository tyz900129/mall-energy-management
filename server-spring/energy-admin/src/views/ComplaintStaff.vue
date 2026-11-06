<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">客诉人员名单</div>
        <div class="page-note">维护客服员与责任商管名单。录入客诉时直接选择名单人员，历史数据按姓名保存快照，账号注销或人员离职不影响历史记录。</div>
      </div>
      <div class="actions">
        <el-input v-model="keyword" placeholder="按姓名搜索" clearable style="width:200px" @keyup.enter="load" />
        <el-switch v-model="includeDisabled" active-text="显示停用" @change="load" />
        <el-button v-if="canManage" type="primary" @click="edit()">新增人员</el-button>
      </div>
    </div>

    <el-tabs v-model="activeType" @tab-change="load">
      <el-tab-pane label="客服员（经办人）" name="HANDLER" />
      <el-tab-pane label="责任商管" name="MANAGER" />
    </el-tabs>

    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="staffName" label="姓名" min-width="110" />
      <el-table-column prop="department" label="部门" min-width="140"><template #default="{ row }">{{ row.department || '-' }}</template></el-table-column>
      <el-table-column prop="phone" label="电话" width="130"><template #default="{ row }">{{ row.phone || '-' }}</template></el-table-column>
      <el-table-column label="关联系统账号" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.sourceUserId" type="success" effect="plain">已关联</el-tag>
          <el-tag v-else type="info" effect="plain">无账号</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.remark || '-' }}</template></el-table-column>
      <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑人员' : `新增${activeType === 'HANDLER' ? '客服员' : '责任商管'}`" width="480px">
      <el-form label-position="top">
        <el-form-item label="姓名" required>
          <el-input v-model="form.staffName" maxlength="64" placeholder="与导入数据中的姓名保持一致" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="部门"><el-input v-model="form.department" maxlength="64" placeholder="如 服务台、营运部" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="form.phone" maxlength="32" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.active" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="256" placeholder="如 超市、自营主力等非系统角色人员" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { complaintApi } from '@/api/complaint'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const canManage = computed(() => user.hasPermission('complaint.staff.manage'))
const activeType = ref('HANDLER')
const keyword = ref('')
const includeDisabled = ref(true)
const rows = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)

const emptyForm = () => ({
  id: null as number | null, staffName: '', department: '', phone: '',
  sortOrder: 0, active: true, remark: ''
})
const form = reactive(emptyForm())

onMounted(load)

async function load() {
  loading.value = true
  try {
    rows.value = await complaintApi.staffList({
      type: activeType.value,
      keyword: keyword.value || '',
      status: includeDisabled.value ? '' : 'ACTIVE'
    }) as any || []
  } finally { loading.value = false }
}

function edit(row?: any) {
  Object.assign(form, row
    ? { id: row.id, staffName: row.staffName, department: row.department || '', phone: row.phone || '', sortOrder: row.sortOrder || 0, active: row.status === 'ACTIVE', remark: row.remark || '' }
    : emptyForm())
  visible.value = true
}

async function save() {
  if (!form.staffName.trim()) return ElMessage.warning('请填写姓名')
  saving.value = true
  try {
    const payload = {
      staffType: activeType.value,
      staffName: form.staffName.trim(),
      department: form.department || null,
      phone: form.phone || null,
      sortOrder: form.sortOrder,
      status: form.active ? 'ACTIVE' : 'INACTIVE',
      remark: form.remark || null
    }
    if (form.id) await complaintApi.updateStaff(form.id, payload)
    else await complaintApi.createStaff(payload)
    ElMessage.success('保存成功')
    visible.value = false
    await load()
  } finally { saving.value = false }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除“${row.staffName}”？已被客诉引用的人员请改用停用。`, '删除确认', { type: 'warning' })
  await complaintApi.removeStaff(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<style scoped>
.page-note { color: #909399; margin-top: 6px; font-size: 13px; }
.actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 768px) {
  .page-header { align-items: flex-start; }
  .actions { width: 100%; }
  .actions .el-input { width: 100% !important; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
