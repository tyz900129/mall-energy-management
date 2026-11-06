<template>
  <el-drawer v-model="visible" title="角色管理" size="860px" destroy-on-close>
    <div class="role-layout">
      <div class="role-panel">
        <div class="panel-head">
          <span>角色（{{ roles.length }}）</span>
          <el-button size="small" type="primary" @click="openCreateRole">新增角色</el-button>
        </div>
        <div class="role-list">
          <div v-for="r in roles" :key="r.id" class="role-item" :class="{ active: r.id === currentId }" @click="selectRole(r)">
            <div class="role-name">{{ r.name }}</div>
            <div class="role-meta">{{ r.code }} · {{ r.userCount || 0 }}人 · {{ r.permissionCount || 0 }}项</div>
          </div>
        </div>
      </div>

      <div v-loading="loading" class="permission-panel">
        <template v-if="currentRole">
          <div class="perm-head">
            <div>
              <div class="perm-title">
                {{ currentRole.name }}
                <el-tag size="small" style="margin-left:8px">{{ currentRole.code }}</el-tag>
              </div>
              <div class="perm-sub">角色默认权限：保存后对该角色下所有用户生效，个人权限中的额外允许/禁止仍然优先</div>
            </div>
            <div class="perm-actions">
              <el-button size="small" @click="openEditRole">编辑角色</el-button>
              <el-popconfirm v-if="!isBuiltinRole" :title="`确定删除角色「${currentRole.name}」？`" @confirm="removeRole">
                <template #reference><el-button size="small" type="danger">删除</el-button></template>
              </el-popconfirm>
              <el-button size="small" type="primary" :disabled="readonly" :loading="saving" @click="savePermissions">保存权限</el-button>
            </div>
          </div>

          <el-alert v-if="readonly" type="info" :closable="false" show-icon
                    title="超级管理员固定拥有全部权限，不可在此调整" style="margin:10px 0" />

          <div class="perm-toolbar">
            <el-input v-model="filter" size="small" placeholder="搜索权限名称/编码" clearable style="width:240px" />
            <span class="perm-count">已选 {{ checked.length }} / {{ totalPermissions }}</span>
          </div>

          <el-collapse v-model="activeModules">
            <el-collapse-item v-for="m in filteredModules" :key="m.code" :name="m.code">
              <template #title>
                <div class="module-title">
                  <el-checkbox :model-value="moduleState(m)" :indeterminate="moduleIndeterminate(m)" :disabled="readonly"
                               @click.stop @change="(v: any) => toggleModule(m, !!v)" />
                  <span class="module-name">{{ m.name }}</span>
                  <span class="module-count">{{ selectedInModule(m) }}/{{ m.permissions.length }}</span>
                </div>
              </template>
              <div class="perm-grid">
                <el-checkbox v-for="p in m.permissions" :key="p.code" :model-value="has(p.code)" :disabled="readonly"
                             @change="(v: any) => togglePermission(p.code, !!v)">
                  <span class="p-name">{{ p.name }}</span>
                  <span class="p-code">{{ p.code }}</span>
                </el-checkbox>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-empty v-else description="请选择角色" />
      </div>
    </div>

    <el-dialog v-model="roleFormVisible" :title="roleForm.id ? '编辑角色' : '新增角色'" width="440px" append-to-body>
      <el-form :model="roleForm" label-width="84px">
        <el-form-item label="角色编码" required>
          <el-input v-model="roleForm.code" :disabled="!!roleForm.id" placeholder="大写字母/数字/下划线，如 CS_AGENT" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.name" placeholder="如 客服员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { systemRoleApi, type SystemRole, type PermissionModule } from '@/api/system'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'changed'): void }>()

const BUILTIN_ROLE_CODES = new Set(['SUPER_ADMIN', 'SYSTEM_ADMIN', 'DEVICE_VERIFIER', 'APPLICANT', 'VIEWER', 'PROPERTY_MANAGER', 'OPS_PATROL_MANAGER'])

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const roles = ref<SystemRole[]>([])
const currentId = ref<number | null>(null)
const modules = ref<PermissionModule[]>([])
const checked = ref<string[]>([])
const activeModules = ref<string[]>([])
const filter = ref('')
const loading = ref(false)
const saving = ref(false)
const roleFormVisible = ref(false)
const roleForm = ref<any>({ id: 0, code: '', name: '', description: '' })

const currentRole = computed(() => roles.value.find(r => r.id === currentId.value) || null)
const isBuiltinRole = computed(() => !!currentRole.value && BUILTIN_ROLE_CODES.has(currentRole.value.code))
const readonly = computed(() => currentRole.value?.code === 'SUPER_ADMIN')
const totalPermissions = computed(() => modules.value.reduce((n, m) => n + m.permissions.length, 0))
const filteredModules = computed(() => {
  const kw = filter.value.trim().toLowerCase()
  if (!kw) return modules.value
  return modules.value
    .map(m => ({ ...m, permissions: m.permissions.filter(p => p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw)) }))
    .filter(m => m.permissions.length > 0)
})

watch(() => props.modelValue, v => { if (v) init() })

async function init() {
  await loadRoles()
  if (currentId.value) await loadPermissions(currentId.value)
}

async function loadRoles(keep?: number | null) {
  roles.value = (await systemRoleApi.list()) as any
  const target = keep !== undefined ? keep : currentId.value
  currentId.value = roles.value.some(r => r.id === target) ? target as number : (roles.value[0]?.id ?? null)
}

async function loadPermissions(id: number) {
  loading.value = true
  try {
    const profile: any = await systemRoleApi.permissions(id)
    modules.value = profile.modules || []
    checked.value = profile.permissionCodes || []
    activeModules.value = modules.value.map(m => m.code)
  } finally {
    loading.value = false
  }
}

async function selectRole(r: SystemRole) {
  if (r.id === currentId.value) return
  currentId.value = r.id
  filter.value = ''
  await loadPermissions(r.id)
}

function has(code: string) { return checked.value.includes(code) }
function selectedInModule(m: PermissionModule) { return m.permissions.filter(p => has(p.code)).length }
function moduleState(m: PermissionModule) { return m.permissions.length > 0 && m.permissions.every(p => has(p.code)) }
function moduleIndeterminate(m: PermissionModule) {
  const n = selectedInModule(m)
  return n > 0 && n < m.permissions.length
}
function togglePermission(code: string, val: boolean) {
  const next = new Set(checked.value)
  if (val) next.add(code); else next.delete(code)
  checked.value = [...next]
}
function toggleModule(m: PermissionModule, val: boolean) {
  const next = new Set(checked.value)
  for (const p of m.permissions) { if (val) next.add(p.code); else next.delete(p.code) }
  checked.value = [...next]
}

async function savePermissions() {
  if (!currentRole.value) return
  saving.value = true
  try {
    await systemRoleApi.savePermissions(currentRole.value.id, checked.value)
    ElMessage.success('角色默认权限已保存，相关用户重新登录后生效')
    await loadRoles(currentRole.value.id)
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openCreateRole() {
  roleForm.value = { id: 0, code: '', name: '', description: '' }
  roleFormVisible.value = true
}
function openEditRole() {
  if (!currentRole.value) return
  roleForm.value = { id: currentRole.value.id, code: currentRole.value.code, name: currentRole.value.name, description: currentRole.value.description || '' }
  roleFormVisible.value = true
}

async function saveRole() {
  const code = (roleForm.value.code || '').trim().toUpperCase()
  const name = (roleForm.value.name || '').trim()
  if (!code) { ElMessage.warning('请输入角色编码'); return }
  if (!/^[A-Z][A-Z0-9_]*$/.test(code)) { ElMessage.warning('角色编码必须为大写字母/数字/下划线，且以字母开头'); return }
  if (!name) { ElMessage.warning('请输入角色名称'); return }
  saving.value = true
  try {
    let saved: any
    if (roleForm.value.id) {
      saved = await systemRoleApi.update(roleForm.value.id, { code, name, description: roleForm.value.description || undefined })
    } else {
      saved = await systemRoleApi.create({ code, name, description: roleForm.value.description || undefined })
    }
    ElMessage.success('角色已保存')
    roleFormVisible.value = false
    await loadRoles(saved?.id ?? roleForm.value.id)
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRole() {
  if (!currentRole.value) return
  saving.value = true
  try {
    await systemRoleApi.remove(currentRole.value.id)
    ElMessage.success('角色已删除')
    await loadRoles(null)
    if (currentId.value) await loadPermissions(currentId.value)
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.role-layout { display: flex; gap: 16px; height: 100%; }
.role-panel { width: 240px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding-right: 12px; display: flex; flex-direction: column; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-size: 13px; color: #606266; }
.role-list { overflow-y: auto; flex: 1; }
.role-item { padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; cursor: pointer; }
.role-item.active { border-color: #409eff; background: #ecf5ff; }
.role-name { font-weight: 600; font-size: 14px; color: #303133; }
.role-meta { margin-top: 4px; font-size: 12px; color: #909399; }
.permission-panel { flex: 1; min-width: 0; overflow-y: auto; padding-left: 4px; }
.perm-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.perm-title { font-size: 15px; font-weight: 600; color: #303133; }
.perm-sub { margin-top: 4px; font-size: 12px; color: #909399; }
.perm-actions { display: flex; gap: 6px; flex-shrink: 0; }
.perm-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 12px 0 4px; }
.perm-count { font-size: 12px; color: #909399; }
.module-title { display: flex; align-items: center; gap: 8px; width: 100%; }
.module-name { font-weight: 600; }
.module-count { font-size: 12px; color: #909399; }
.perm-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; padding: 4px 0 8px 24px; }
.p-name { font-size: 13px; }
.p-code { margin-left: 6px; font-size: 11px; color: #a8abb2; }
</style>
