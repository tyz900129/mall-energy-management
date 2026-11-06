<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-title">巡场点位管理</div>
        <div class="page-note">配置各区域巡场点位，每个点位代表一个检查区域。</div>
      </div>
      <div class="actions">
        <el-switch v-model="includeDisabled" active-text="显示停用" @change="load" />
        <el-button v-if="canManage" type="primary" @click="edit()">新增点位</el-button>
      </div>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="code" label="点位编码" width="140" />
      <el-table-column prop="name" label="点位名称" min-width="160" />
      <el-table-column prop="area" label="区域" width="120" />
      <el-table-column prop="floor" label="楼层" width="100" />
      <el-table-column prop="locationText" label="位置描述" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="85">
        <template #default="{row}">
          <el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="160" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id?'编辑点位':'新增点位'" width="620px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="点位编码" required>
              <el-input v-model="form.code" placeholder="例如：A-1F-01" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="点位名称" required>
              <el-input v-model="form.name" placeholder="例如：大堂入口" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属区域" required>
              <el-input v-model="form.area" placeholder="例如：A 区" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="楼层">
              <el-input v-model="form.floor" placeholder="例如：1F / B1" maxlength="64" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置描述">
          <el-input v-model="form.locationText" maxlength="255" />
        </el-form-item>
        <el-form-item label="点位类型">
          <el-select v-model="form.pointType" placeholder="请选择" style="width:100%">
            <el-option v-for="item in pointTypes" :key="item.itemLabel" :label="item.itemLabel" :value="item.itemLabel" />
          </el-select>
          <div class="hint">可在「巡场管理 - 分类字典 - 点位类型」中维护</div>
        </el-form-item>
        <el-form-item label="检查要点">
          <div style="display:flex;gap:8px;flex-direction:column">
            <div v-for="(it,idx) in form.checkItems" :key="idx" style="display:flex;gap:8px">
              <el-input v-model="it.label" placeholder="检查要点" />
              <el-button link type="danger" @click="form.checkItems.splice(idx,1)">删除</el-button>
            </div>
            <el-button link type="primary" @click="form.checkItems.push({label:''})">+ 新增要点</el-button>
          </div>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { patrolApi } from '@/api/patrol'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const canManage = computed(() => user.hasPermission('patrol.config'))
const rows = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const includeDisabled = ref(false)
const pointTypes = ref<any[]>([])
const form = reactive<any>({id:null,code:'',name:'',area:'',floor:'',locationText:'',pointType:'',checkItems:[],sortOrder:0,enabled:true})

async function load(){
  loading.value=true
  try{ rows.value = await patrolApi.points(includeDisabled.value) as any||[] }
  finally{ loading.value=false }
}
async function loadTypes(){
  pointTypes.value = await patrolApi.pointTypes() as any || []
  if(!form.pointType) form.pointType = pointTypes.value.find((t:any)=>t.status===1)?.itemLabel || ''
}
function edit(row?:any){
  const firstType = pointTypes.value.find((t:any)=>t.status===1)?.itemLabel || ''
  Object.assign(form, row ? { ...row, checkItems: [...(row.checkItems||[])] }
    : {id:null,code:'',name:'',area:'',floor:'',locationText:'',pointType:firstType,checkItems:[],sortOrder:0,enabled:true})
  if(!form.pointType) form.pointType = firstType
  if(!form.checkItems.length) form.checkItems = [{label:''}]
  visible.value = true
}
async function save(){
  if(!form.code.trim()) return ElMessage.warning('请填写点位编码')
  if(!form.name.trim()) return ElMessage.warning('请填写点位名称')
  if(!form.area.trim()) return ElMessage.warning('请填写所属区域')
  saving.value=true
  try{
    await patrolApi.savePoint(form)
    ElMessage.success('保存成功')
    visible.value=false
    await load()
    await loadTypes()
  }finally{ saving.value=false }
}
async function remove(row:any){
  await ElMessageBox.confirm(`确认删除点位“${row.name}”？如有巡场记录请改用停用。`, '删除确认', {type:'warning'})
  await patrolApi.removePoint(row.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(async()=>{ await Promise.all([load(),loadTypes()]) })
</script>

<style scoped>
.page-note{color:#909399;margin-top:6px;font-size:13px}
.hint{color:#909399;font-size:12px;margin-top:4px}
.actions{display:flex;align-items:center;gap:14px}
.form-row{display:grid;grid-template-columns:1fr 1fr;gap:20px}
@media(max-width:768px){
  .page-header{align-items:flex-start}
  .actions{width:100%;justify-content:space-between}
  .form-row{grid-template-columns:1fr}
  .page{overflow-x:auto}
}
</style>
