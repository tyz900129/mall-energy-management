<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">检查项库</div>
        <div class="page-note">按表单类型维护检查项，零售（一处/二处）与餐饮（三处/四处）各一套。修改次日生效，已生成任务不受影响。</div>
      </div>
      <div class="actions">
        <el-radio-group v-model="formType" @change="load">
          <el-radio-button value="RETAIL">零售表单</el-radio-button>
          <el-radio-button value="FOOD">餐饮表单</el-radio-button>
        </el-radio-group>
        <el-button v-if="canManage" type="primary" @click="edit()">新增检查项</el-button>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="编码" width="70" />
      <el-table-column prop="groupName" label="分组" width="100" />
      <el-table-column prop="content" label="检查项内容" min-width="260" show-overflow-tooltip />
      <el-table-column label="适用时段" min-width="150">
        <template #default="{row}">{{slotNames(row.slotIds)}}</template>
      </el-table-column>
      <el-table-column prop="category" label="问题分类" :width="isMobile ? 84 : 90" />
      <el-table-column v-if="!isMobile" label="时限" width="80"><template #default="{row}">{{row.dueHours}}h</template></el-table-column>
      <el-table-column v-if="!isMobile" label="紧急" width="70">
        <template #default="{row}"><el-tag v-if="row.urgency==='URGENT'" type="danger" size="small">紧急</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="必拍" width="65">
        <template #default="{row}"><el-tag v-if="row.photoRequired" type="warning" size="small">必拍</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id?'编辑检查项':'新增检查项'" width="560px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="表单类型" required>
              <el-radio-group v-model="form.formType" :disabled="!!form.id">
                <el-radio-button value="RETAIL">零售</el-radio-button>
                <el-radio-button value="FOOD">餐饮</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="编码（留空自动生成）"><el-input v-model="form.code" :disabled="!!form.id" maxlength="16" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="分组" required>
          <el-select v-model="form.groupName" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查项内容" required><el-input v-model="form.content" type="textarea" :rows="2" maxlength="255" /></el-form-item>
        <el-form-item label="适用时段" required>
          <el-select v-model="form.slotIds" multiple style="width:100%">
            <el-option v-for="s in slots.filter((x:any)=>!x.isHandover)" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="问题分类">
              <el-select v-model="form.category" style="width:100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="整改时限（小时）"><el-input-number v-model="form.dueHours" :min="1" :max="720" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="紧急"><el-switch v-model="form.urgent" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="不合格必拍照"><el-switch v-model="form.photoRequired" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsPatrolApi } from '@/api/opsPatrol'
import { useUserStore } from '@/stores/user'
import { useIsMobile } from '@/composables/useIsMobile'
const user = useUserStore()
const isMobile = useIsMobile()
const canManage = computed(() => user.hasPermission('ops.patrol.config'))
const formType = ref('RETAIL')
const rows = ref<any[]>([])
const slots = ref<any[]>([])
const categories = ref<string[]>(['设施设备','环境卫生','消防安全','秩序安全','商户管理','其他'])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const form = reactive<any>({id:null,code:'',formType:'RETAIL',groupName:'',content:'',slotIds:[],category:'商户管理',dueHours:24,urgent:false,photoRequired:false,enabled:true})

const groupOptions = computed(()=>[...new Set(rows.value.map((r:any)=>r.groupName))])
function slotNames(ids:number[]){ return (ids||[]).map(id=>{ const s=slots.value.find((x:any)=>x.id===id); return s?`${s.code}`:'' }).filter(Boolean).join('、') }

async function load(){ loading.value=true; try{ rows.value = await opsPatrolApi.checkItems(formType.value) as any || [] } finally{ loading.value=false } }
function edit(row?:any){
  Object.assign(form, row ? { id:row.id, code:row.code, formType:row.formType, groupName:row.groupName, content:row.content,
      slotIds:[...(row.slotIds||[])], category:row.category, dueHours:row.dueHours,
      urgent:row.urgency==='URGENT', photoRequired:!!row.photoRequired, enabled:!!row.enabled }
    : { id:null, code:'', formType:formType.value, groupName:'', content:'', slotIds:[], category:'商户管理', dueHours:24, urgent:false, photoRequired:false, enabled:true })
  visible.value=true
}
async function save(){
  if(!form.groupName) return ElMessage.warning('请填写分组')
  if(!form.content.trim()) return ElMessage.warning('请填写检查项内容')
  if(!form.slotIds.length) return ElMessage.warning('请选择适用时段')
  saving.value=true
  try{
    await opsPatrolApi.saveCheckItem({ ...form, urgency: form.urgent?'URGENT':'NORMAL' })
    ElMessage.success('保存成功')
    visible.value=false
    await load()
  }finally{ saving.value=false }
}
async function remove(row:any){
  await ElMessageBox.confirm(`确认删除检查项「${row.content}」？已生成任务不受影响。`,'删除确认',{type:'warning'})
  await opsPatrolApi.removeCheckItem(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(async()=>{ slots.value = await opsPatrolApi.slots() as any || []; await load() })
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.actions{display:flex;align-items:center;gap:14px}
</style>
