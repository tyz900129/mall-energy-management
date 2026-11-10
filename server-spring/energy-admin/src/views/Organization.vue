<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-title">组织架构</div><div class="subtitle">维护「部 / 处·课 / 班」三级部门树，用户与业务归属以最新组织为准</div></div>
      <div class="page-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreateRoot">新增根部门（部）</el-button>
      </div>
    </div>
    <el-card v-loading="loading" shadow="never">
      <el-table :data="tree" row-key="id" :tree-props="{ children: 'children' }" default-expand-all border>
        <template #empty><el-empty :image-size="80" description="暂无部门，点击「新增根部门」开始建设组织架构" /></template>
        <el-table-column label="部门名称" min-width="240">
          <template #default="{ row }"><el-tag size="small" effect="plain" :type="levelTag(row.deptLevel)">{{ levelLabel(row.deptLevel) }}</el-tag><span class="dept-name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="编码" min-width="160">
          <template #default="{ row }"><span class="code">{{ row.code }}</span><el-tag v-if="row.codeLocked === 1" size="small" type="info" class="locked">内置</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="userCount" label="人数" width="80" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <span class="op-wrap" :title="row.depth >= 2 ? '最多三级（部/处·课/班），不能再添加下级' : ''"><el-button link type="primary" :disabled="row.depth >= 2" @click="openCreateChild(row as SystemDepartment)">新增下级</el-button></span>
            <el-button link type="primary" @click="openEdit(row as SystemDepartment)">编辑</el-button>
            <el-button link type="primary" @click="openMove(row as SystemDepartment)">移动</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row as SystemDepartment)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            <span class="op-wrap" :title="row.codeLocked === 1 ? '系统内置部门不可删除' : ''"><el-button link type="danger" :disabled="row.codeLocked === 1" @click="remove(row as SystemDepartment)">删除</el-button></span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑部门' : (form.parentId ? '新增下级部门' : '新增根部门（部）')" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="!form.id" label="上级部门">
          <el-tree-select v-model="form.parentId" :data="createParentOptions" node-key="id" :props="{ label: 'label', children: 'children', disabled: 'disabled' }" check-strictly clearable default-expand-all style="width:100%" placeholder="留空表示新增根部门（部）" />
        </el-form-item>
        <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" placeholder="例如：营运部" /></el-form-item>
        <el-form-item label="部门编码" prop="code">
          <el-input v-model="form.code" :disabled="form.id > 0 && form.codeLocked === 1" placeholder="仅限大写字母、数字和下划线" />
          <div v-if="form.id > 0 && form.codeLocked === 1" class="form-tip">系统内置部门编码不可修改</div>
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="form.deptGroup" style="width:100%">
            <el-option label="商管" value="OPERATION" /><el-option label="客服" value="SERVICE" /><el-option label="物业" value="PROPERTY" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :precision="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="moveVisible" title="移动部门" width="520px">
      <div class="move-tip">正在移动：<b>{{ moving?.name }}</b><span class="text-muted">（{{ moving?.code }}）</span></div>
      <el-tree-select v-model="moveParentId" :data="moveOptions" node-key="id" :props="{ label: 'label', children: 'children', disabled: 'disabled' }" check-strictly default-expand-all :render-after-expand="false" style="width:100%" placeholder="请选择新的上级部门" />
      <div class="move-hint">选择「（移到根：部）」表示移到最上层；被移动部门自身及其下级不可作为目标。</div>
      <template #footer><el-button @click="moveVisible = false">取消</el-button><el-button type="primary" :loading="movingSaving" @click="saveMove">确认移动</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { systemDepartmentApi, type DepartmentGroup, type DepartmentLevel, type SystemDepartment } from '@/api/system'

interface DeptOption { id: number; label: string; disabled?: boolean; children?: DeptOption[] }
interface DeptForm { id: number; name: string; code: string; parentId: number|null; deptGroup: DepartmentGroup; sortOrder: number; codeLocked: number }

const tree = ref<SystemDepartment[]>([]), loading = ref(false), saving = ref(false), movingSaving = ref(false)
const editVisible = ref(false), moveVisible = ref(false), formRef = ref<FormInstance>()
const form = reactive<DeptForm>({ id: 0, name: '', code: '', parentId: null, deptGroup: 'OPERATION', sortOrder: 0, codeLocked: 0 })
const moving = ref<SystemDepartment>(), moveParentId = ref<number|null>(null)
const rules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '以字母开头，仅支持大写字母、数字和下划线', trigger: 'blur' }
  ]
}

function levelLabel(level: DepartmentLevel) { return level === 'GROUP' ? '部' : level === 'SECTION' ? '处·课' : '班' }
function levelTag(level: DepartmentLevel) { return level === 'GROUP' ? 'danger' : level === 'SECTION' ? 'warning' : 'info' }

function toOptions(nodes: SystemDepartment[], excludeIds?: Set<number>, disableDepth?: number): DeptOption[] {
  return nodes.map(node => {
    const option: DeptOption = { id: node.id, label: `${levelLabel(node.deptLevel)} · ${node.name}` }
    if (excludeIds?.has(node.id) || (disableDepth !== undefined && node.depth >= disableDepth)) option.disabled = true
    if (node.children?.length) option.children = toOptions(node.children, excludeIds, disableDepth)
    return option
  })
}
const createParentOptions = computed(() => toOptions(tree.value, undefined, 2))
const moveOptions = computed<DeptOption[]>(() => {
  if (!moving.value) return []
  const excluded = new Set<number>()
  const walk = (node: SystemDepartment) => { excluded.add(node.id); (node.children || []).forEach(walk) }
  walk(moving.value)
  return [{ id: 0, label: '（移到根：部）', children: toOptions(tree.value, excluded) }]
})

async function load() {
  loading.value = true
  try { tree.value = await systemDepartmentApi.tree() } catch { /* http.ts 已统一提示 */ } finally { loading.value = false }
}
function resetForm() {
  Object.assign(form, { id: 0, name: '', code: '', parentId: null, deptGroup: 'OPERATION', sortOrder: 0, codeLocked: 0 })
  formRef.value?.clearValidate()
}
function openCreateRoot() { resetForm(); editVisible.value = true }
function openCreateChild(node: SystemDepartment) {
  resetForm()
  Object.assign(form, { parentId: node.id, deptGroup: node.deptGroup })
  editVisible.value = true
}
function openEdit(node: SystemDepartment) {
  resetForm()
  Object.assign(form, { id: node.id, name: node.name, code: node.code, parentId: node.parentId, deptGroup: node.deptGroup, sortOrder: node.sortOrder, codeLocked: node.codeLocked })
  editVisible.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = { code: form.code.trim(), name: form.name.trim(), deptGroup: form.deptGroup, sortOrder: form.sortOrder }
    if (form.id) await systemDepartmentApi.update(form.id, payload)
    else await systemDepartmentApi.create({ ...payload, parentId: form.parentId ?? null })
    ElMessage.success(form.id ? '部门已更新' : '部门已创建')
    editVisible.value = false
    await load()
  } catch { /* http.ts 已统一提示 */ } finally { saving.value = false }
}
function openMove(node: SystemDepartment) {
  moving.value = node
  moveParentId.value = node.parentId ?? 0
  moveVisible.value = true
}
async function saveMove() {
  if (!moving.value) return
  if (moveParentId.value === null || moveParentId.value === undefined) { ElMessage.warning('请选择新的上级部门'); return }
  movingSaving.value = true
  try {
    await systemDepartmentApi.move(moving.value.id, moveParentId.value === 0 ? null : moveParentId.value)
    ElMessage.success('部门已移动，组织树已更新')
    moveVisible.value = false
    await load()
  } catch { /* http.ts 已统一提示 */ } finally { movingSaving.value = false }
}
async function toggleStatus(node: SystemDepartment) {
  const next = node.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      next === 'DISABLED' ? `确定停用「${node.name}」？停用后该部门不再出现在下拉选择中，已有数据不受影响。` : `确定启用「${node.name}」？`,
      next === 'DISABLED' ? '停用部门' : '启用部门',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await systemDepartmentApi.changeStatus(node.id, next)
    ElMessage.success(next === 'DISABLED' ? '部门已停用' : '部门已启用')
    await load()
  } catch { /* http.ts 已统一提示 */ }
}
async function remove(node: SystemDepartment) {
  try {
    await ElMessageBox.confirm(`删除后「${node.name}」将停用并从组织树中移除，历史数据（人员、业务记录）仍然保留。确定删除？`, '删除部门', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  } catch { return }
  try {
    await systemDepartmentApi.remove(node.id)
    ElMessage.success('部门已删除')
    await load()
  } catch { /* http.ts 已统一提示 */ }
}
onMounted(load)
</script>

<style scoped lang="scss">
.subtitle { font-size: 13px; color: #909399; margin-top: 4px; }
.page-actions { display: flex; align-items: center; gap: 8px; .el-button { margin-left: 0; } }
.dept-name { margin-left: 8px; font-weight: 500; }
.code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: #606266; }
.locked { margin-left: 6px; }
.op-wrap { display: inline-block; }
.form-tip { width: 100%; margin-top: 2px; font-size: 12px; line-height: 1.5; color: #909399; }
.move-tip { margin-bottom: 12px; color: #303133; }
.move-hint { margin-top: 8px; font-size: 12px; line-height: 1.5; color: #909399; }
@media (max-width: 768px) {
  .page-header { align-items: flex-start; flex-direction: column; gap: 12px; }
  .page-actions { width: 100%; display: grid; grid-template-columns: 1fr 1fr; .el-button { width: 100%; } }
}
</style>
