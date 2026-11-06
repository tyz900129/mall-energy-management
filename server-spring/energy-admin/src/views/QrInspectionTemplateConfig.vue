<template>
  <div class="page">
    <div class="page-header">
      <h2>巡检模板配置</h2>
      <div class="tabs">
        <button v-for="s in scenes" :key="s.value" class="tab" :class="{active: currentScene===s.value}" @click="currentScene=s.value">{{s.label}}</button>
      </div>
    </div>

    <div class="toolbar">
      <div class="left">
        <span class="hint">模板：</span>
        <el-select v-model="currentTemplateId" size="small" style="width:240px" @change="selectTemplate">
          <el-option v-for="t in templateList" :key="t.id" :value="t.id" :label="t.name" />
        </el-select>
        <el-button size="small" style="margin-left:8px" @click="newTemplate">新建模板</el-button>
        <span class="hint" style="margin-left:8px">直接修改并保存，已提交的巡检记录保留当时的表单快照</span>
      </div>
      <el-button type="primary" size="small" @click="saveCurrent" :loading="saving">保存</el-button>
    </div>
    <div class="hint" style="margin-bottom:10px">
      整改责任部门：留空时默认推送给铺位所属楼层的商管处室；若某项属于物业责任（如基础设施），可在此指定固定部门（如设备课 / 保洁课 / 安保课）。
    </div>

    <div v-if="!current" class="empty">请先创建模板</div>
    <div v-else class="tpl-editor">
      <el-table :data="fields" border size="small" row-key="code" style="width:100%">
        <el-table-column label="#" width="48" align="center">
          <template #default="{ $index }"><input v-model.number="fields[$index].sortOrder" style="width:40px;text-align:center;border:none;background:transparent"></template>
        </el-table-column>
        <el-table-column label="字段编码" width="140">
          <template #default="{ row, $index }"><input v-model="row.code" class="cell-input"></template>
        </el-table-column>
        <el-table-column label="检查项名称" min-width="180">
          <template #default="{ row }"><input v-model="row.label" class="cell-input"></template>
        </el-table-column>
        <el-table-column label="题型" width="140">
          <template #default="{ row }">
            <select v-model="row.controlType" class="cell-select">
              <option value="NORMAL_ABNORMAL">正常-异常</option>
              <option value="BOOLEAN">布尔</option>
              <option value="SELECT">单选</option>
              <option value="MULTISELECT">多选</option>
              <option value="NUMBER">数字</option>
              <option value="TEXT">文本</option>
              <option value="IMAGE">图片</option>
            </select>
          </template>
        </el-table-column>
        <el-table-column label="正常值" width="110">
          <template #default="{ row }"><input v-model="row.normalValue" class="cell-input" placeholder="如 NORMAL / true"></template>
        </el-table-column>
        <el-table-column label="问题分类" width="120">
          <template #default="{ row }"><input v-model="row.category" class="cell-input" placeholder="如 用电安全"></template>
        </el-table-column>
        <el-table-column label="整改责任部门" width="130">
          <template #default="{ row }">
            <select v-model="row.responsibleDeptCode" class="cell-select">
              <option value="">—</option>
              <option v-for="d in departments" :key="d.code" :value="d.code">{{ d.name }}</option>
            </select>
          </template>
        </el-table-column>
        <el-table-column label="必填" width="60" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.required" /></template>
        </el-table-column>
        <el-table-column label="异常必拍" width="80" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.requirePhotoOnAbnormal" /></template>
        </el-table-column>
        <el-table-column label="启用" width="60" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column label="" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="fields.splice($index,1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" @click="addField" style="margin-top:10px">+ 新增检查项</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const scenes = [
  { value: 'WAREHOUSE_COMMERCIAL', label: '仓库商管检查表' },
  { value: 'WAREHOUSE_PROPERTY',  label: '仓库物业巡检表' },
  { value: 'JOINT',               label: '联合巡检统一表' },
]
const currentScene = ref(scenes[0].value)
const current = ref<any>(null)
const templateList = ref<any[]>([])
const currentTemplateId = ref<number | null>(null)
const fields = ref<any[]>([])
const saving = ref(false)
const departments = ref<any[]>([])

onMounted(async () => {
  try {
    const res: any = await http.get('/api/v1/qr-inspection/departments')
    departments.value = res || []
  } catch {}
  loadCurrent()
})

watch(currentScene, loadCurrent)

async function loadCurrent() {
  try {
    const res: any = await http.get('/api/v1/qr-inspection/templates', { params: { scene: currentScene.value } })
    templateList.value = res || []
    if (templateList.value.length === 0) {
      const sceneDefaults: Record<string, any> = { WAREHOUSE_COMMERCIAL: 'COMMERCIAL', WAREHOUSE_PROPERTY: 'PROPERTY', JOINT: null }
      current.value = { scene: currentScene.value, name: scenes.find(s => s.value===currentScene.value)?.label, roleGroup: sceneDefaults[currentScene.value], fieldsJson: '[]', enabled: 1 }
      currentTemplateId.value = null
      fields.value = []
      return
    }
    const chosen = templateList.value.find((t: any) => t.id === currentTemplateId.value) || templateList.value[0]
    currentTemplateId.value = chosen.id
    current.value = chosen
    try { fields.value = JSON.parse(chosen.fieldsJson || '[]') } catch { fields.value = [] }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  }
}

function selectTemplate(id: number) {
  const t = templateList.value.find((x: any) => x.id === id)
  if (!t) return
  current.value = t
  try { fields.value = JSON.parse(t.fieldsJson || '[]') } catch { fields.value = [] }
}

function newTemplate() {
  const sceneDefaults: Record<string, any> = { WAREHOUSE_COMMERCIAL: 'COMMERCIAL', WAREHOUSE_PROPERTY: 'PROPERTY', JOINT: null }
  current.value = { scene: currentScene.value, name: scenes.find(s => s.value===currentScene.value)?.label, roleGroup: sceneDefaults[currentScene.value], fieldsJson: '[]', enabled: 1 }
  currentTemplateId.value = null
  fields.value = []
}

function addField() {
  fields.value.push({
    code: '', label: '', controlType: 'NORMAL_ABNORMAL', normalValue: 'NORMAL',
    category: '', responsibleDeptCode: '', required: true, requirePhotoOnAbnormal: true, enabled: true,
    sortOrder: (fields.value.length + 1) * 10
  })
}

async function saveCurrent() {
  if (!current.value) return
  saving.value = true
  try {
    const payload = { ...current.value, fieldsJson: JSON.stringify(fields.value), enabled: current.value.enabled ?? 1 }
    const saved: any = await http.post('/api/v1/qr-inspection/templates', payload)
    if (saved?.id) currentTemplateId.value = saved.id
    ElMessage.success('模板已保存（直接修改，历史快照保留）')
    loadCurrent()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}
</script>

<style scoped>
.page { padding: 16px; }
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.tabs { display:flex; gap:4px; }
.tab { padding:6px 14px; border:1px solid #d1d5db; border-radius:16px; background:#fff; cursor:pointer; font-size:13px; }
.tab.active { background:#111827; color:#fff; border-color:#111827; }
.toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.toolbar .left { display:flex; align-items:center; }
.hint { color:#6b7280; font-size:13px; }
.cell-input, .cell-select { width:100%; border:none; background:transparent; outline:none; padding:4px 0; font-size:13px; }
.cell-input:focus, .cell-select:focus { border-bottom:1px solid #111827; background:#fff; }
</style>
