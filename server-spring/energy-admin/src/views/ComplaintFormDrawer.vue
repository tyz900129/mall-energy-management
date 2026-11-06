<template>
  <el-drawer :model-value="modelValue" :title="complaintId ? '客诉处理' : '客诉登记'" :size="drawerSize" destroy-on-close
             @update:model-value="close">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="complaint-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="发生日期" prop="occurDate">
            <el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择日期" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客诉类别" prop="category">
            <el-select v-model="form.category" style="width:100%" placeholder="选择类别">
              <el-option v-for="item in dicts.CATEGORY" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="品牌/铺位" prop="brandName">
        <div class="brand-row">
          <el-autocomplete v-model="form.brandName" :fetch-suggestions="searchBrands" value-key="brandName" clearable
                           style="flex:1" placeholder="输入品牌、铺位名称或编码搜索" @select="onBrandSelect" @input="onBrandInput">
            <template #default="{ item }">
              <div class="brand-suggest">
                <span>{{ item.brandName }}</span>
                <span class="muted">{{ item.businessTypeLabel }} · {{ item.floorLabel || '-' }} · {{ item.stallCode || '非铺位' }}</span>
              </div>
            </template>
          </el-autocomplete>
          <el-button v-if="canCreateBrand" plain @click="brandDialogVisible = true">+ 新增品牌</el-button>
        </div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="业态" prop="businessType">
            <el-select v-model="form.businessType" style="width:100%" placeholder="选择业态">
              <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="楼层">
            <el-select v-model="form.floorCode" style="width:100%" clearable placeholder="选择楼层">
              <el-option v-for="item in floors" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="细分品类">
            <el-input v-model="form.businessCategory" placeholder="如 服装/轻餐" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="客诉渠道">
            <el-select v-model="form.channel" style="width:100%" clearable placeholder="选择渠道">
              <el-option v-for="item in dicts.CHANNEL" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="案例内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="描述顾客诉求、现场情况" />
      </el-form-item>
      <el-form-item label="解决方案">
        <el-input v-model="form.solution" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="处理过程与结果" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="公司承担">
            <el-input-number v-model="form.companyCost" :min="0" :precision="2" :controls="false" :disabled="form.noCost" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="商户承担">
            <el-input-number v-model="form.merchantCost" :min="0" :precision="2" :controls="false" :disabled="form.noCost" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="费用标记">
            <el-checkbox v-model="form.noCost">无需承担费用</el-checkbox>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="现场解决">
        <el-switch v-model="form.onSiteHandle" />
        <el-select v-model="form.satisfaction" clearable placeholder="满意度（关闭时填写）" style="width:220px;margin-left:12px">
          <el-option v-for="item in dicts.SATISFACTION" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
        </el-select>
      </el-form-item>
      <el-form-item label="责任商管">
        <el-select v-model="form.managerStaffIds" multiple filterable style="width:100%"
                   placeholder="选中品牌后自动带出铺位责任商管，可增删">
          <el-option v-for="item in managerStaff" :key="item.id" :label="item.staffName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="经办人">
        <el-select v-model="form.handlerStaffIds" multiple filterable style="width:100%"
                   placeholder="从客服员名单选择，可多选">
          <el-option v-for="item in handlerStaff" :key="item.id" :label="item.staffName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="现场照片">
        <div class="attachment-box">
          <div v-for="item in attachments" :key="item.id" class="attachment-item">
            <el-image v-if="attachmentUrls[item.id]" :src="attachmentUrls[item.id]" fit="cover" class="thumb" @click="preview(item)" />
            <el-button link type="danger" @click="removeAttachment(item)">删除</el-button>
          </div>
          <el-upload v-model:file-list="fileList" :auto-upload="false" accept="image/*" list-type="picture-card"
                     :limit="6 - attachments.length" :on-exceed="() => ElMessage.warning('每条客诉最多 6 张照片')">
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="muted">支持 JPEG/PNG，单张不超过 8MB，最多 6 张</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close(false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>

  <el-dialog v-model="brandDialogVisible" title="新增非铺位品牌" width="420px" append-to-body>
    <el-form label-width="88px">
      <el-form-item label="品牌/品类" required><el-input v-model="brandForm.name" placeholder="如 果蔬区、物业、自营主力" /></el-form-item>
      <el-form-item label="业态">
        <el-select v-model="brandForm.businessType" clearable style="width:100%">
          <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="楼层">
        <el-select v-model="brandForm.floorCode" clearable style="width:100%">
          <el-option v-for="item in floors" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="brandDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="brandSaving" @click="saveBrand">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { complaintApi } from '@/api/complaint'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ modelValue: boolean; complaintId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved'): void }>()
const viewportWidth = ref(window.innerWidth)
const drawerSize = computed(() => (viewportWidth.value < 768 ? '100%' : '720px'))
window.addEventListener('resize', () => { viewportWidth.value = window.innerWidth })

const formRef = ref<FormInstance>()
const saving = ref(false)
const brandSaving = ref(false)
const brandDialogVisible = ref(false)
const brandKeyword = ref('')
const fileList = ref<any[]>([])
const attachments = ref<any[]>([])
const handlerStaff = ref<any[]>([])
const managerStaff = ref<any[]>([])
const businessTypes = ref<any[]>([])
const floors = ref<any[]>([])
const dicts = reactive<Record<string, any[]>>({ CATEGORY: [], CHANNEL: [], SATISFACTION: [], STATUS: [] })
const attachmentUrls = reactive<Record<number, string>>({})
const userStore = useUserStore()
const canCreateBrand = computed(() => userStore.hasPermission('complaint.brand.manage') || userStore.hasPermission('complaint.create'))

const emptyForm = () => ({
  occurDate: new Date().toISOString().slice(0, 10),
  stallId: null as number | null,
  brandId: null as number | null,
  brandName: '',
  businessType: '',
  businessCategory: '',
  floorCode: '',
  category: '',
  channel: '',
  content: '',
  solution: '',
  companyCost: 0,
  merchantCost: 0,
  noCost: false,
  onSiteHandle: false,
  satisfaction: '',
  handlerStaffIds: [] as number[],
  managerStaffIds: [] as number[]
})
const form = reactive(emptyForm())
const brandForm = reactive({ name: '', businessType: '', floorCode: '' })

const rules: FormRules = {
  occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
  brandName: [{ required: true, message: '请选择品牌/铺位', trigger: 'change' }],
  businessType: [{ required: true, message: '请选择业态', trigger: 'change' }],
  category: [{ required: true, message: '请选择客诉类别', trigger: 'change' }],
  content: [{ required: true, message: '请填写案例内容', trigger: 'blur' }]
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  reset()
  await loadOptions()
  if (props.complaintId) await loadDetail(props.complaintId)
})

function close(value = false) { emit('update:modelValue', value) }
function reset() {
  Object.assign(form, emptyForm())
  fileList.value = []
  attachments.value = []
  Object.keys(attachmentUrls).forEach(key => delete attachmentUrls[Number(key)])
  brandForm.name = ''
  brandForm.businessType = ''
  brandForm.floorCode = ''
}

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
}

async function loadDetail(id: number) {
  const data: any = await complaintApi.detail(id)
  Object.assign(form, {
    occurDate: data.occurDate,
    stallId: data.stallId,
    brandId: data.brandId,
    brandName: data.brandName,
    businessType: data.businessType,
    businessCategory: data.businessCategory || '',
    floorCode: data.floorCode || '',
    category: data.category,
    channel: data.channel || '',
    content: data.content,
    solution: data.solution || '',
    companyCost: Number(data.companyCost || 0),
    merchantCost: Number(data.merchantCost || 0),
    noCost: data.noCost === true || data.noCost === 1,
    onSiteHandle: data.onSiteHandle === true || data.onSiteHandle === 1,
    satisfaction: data.satisfaction || '',
    handlerStaffIds: (data.handlers || []).map((u: any) => u.staffId),
    managerStaffIds: (data.managers || []).map((u: any) => u.staffId)
  })
  attachments.value = data.attachments || []
  mergeStaff(managerStaff, data.managers)
  mergeStaff(handlerStaff, data.handlers)
  for (const item of attachments.value) {
    const blob: any = await complaintApi.attachmentFile(id, item.id)
    attachmentUrls[item.id] = window.URL.createObjectURL(blob)
  }
}

/** 历史参与人补充到下拉选项，保证回显姓名（名单已停用时也能正常显示）。 */
function mergeStaff(target: any, rows: any[]) {
  const list: any[] = target.value
  (rows || []).forEach((u: any) => {
    if (u && u.staffId && !list.some((s: any) => s.id === u.staffId)) {
      list.push({ id: u.staffId, staffName: u.userName })
    }
  })
}

let brandTimer: any = null
function searchBrands(keyword: string, callback: (items: any[]) => void) {
  brandKeyword.value = keyword
  clearTimeout(brandTimer)
  brandTimer = setTimeout(async () => {
    const rows: any = await complaintApi.brands(keyword, form.businessType)
    callback((rows || []).map((r: any) => ({ ...r, value: r.brandName })))
  }, 200)
}

function onBrandInput(value: string | number) {
  brandKeyword.value = String(value)
  form.stallId = null
  form.brandId = null
}

async function onBrandSelect(item: any) {
  form.brandName = item.brandName
  form.stallId = item.stallId || null
  form.brandId = item.brandId || null
  form.businessType = item.businessType || form.businessType
  form.businessCategory = item.businessCategory || ''
  form.floorCode = item.floorCode || ''
  if (item.responsibleUserId) {
    const rows: any = await complaintApi.staffByUsers([item.responsibleUserId], 'MANAGER')
    if (rows?.length) {
      form.managerStaffIds = rows.map((s: any) => s.id)
      mergeStaff(managerStaff, rows.map((s: any) => ({ staffId: s.id, userName: s.staffName })))
    }
  }
}

async function saveBrand() {
  if (!brandForm.name.trim()) return ElMessage.warning('请填写品牌/品类名称')
  brandSaving.value = true
  try {
    const data: any = await complaintApi.createBrand({ ...brandForm })
    form.brandId = data.brandId
    form.stallId = null
    form.brandName = data.brandName
    form.businessType = data.businessType || form.businessType
    form.floorCode = data.floorCode || ''
    brandDialogVisible.value = false
    ElMessage.success('品牌已新增并选中')
  } finally { brandSaving.value = false }
}

async function preview(item: any) {
  const blob: any = await complaintApi.attachmentFile(props.complaintId as number, item.id)
  const url = window.URL.createObjectURL(blob)
  window.open(url, '_blank')
}

async function removeAttachment(item: any) {
  await complaintApi.removeAttachment(props.complaintId as number, item.id)
  attachments.value = attachments.value.filter((a: any) => a.id !== item.id)
  ElMessage.success('照片已删除')
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      occurDate: form.occurDate,
      stallId: form.stallId,
      brandId: form.brandId,
      brandName: form.brandName,
      businessType: form.businessType,
      businessCategory: form.businessCategory || null,
      floorCode: form.floorCode || null,
      category: form.category,
      channel: form.channel || null,
      content: form.content,
      solution: form.solution || null,
      companyCost: form.noCost ? 0 : Number(form.companyCost || 0),
      merchantCost: form.noCost ? 0 : Number(form.merchantCost || 0),
      noCost: form.noCost,
      onSiteHandle: form.onSiteHandle,
      satisfaction: form.satisfaction || null,
      handlerStaffIds: form.handlerStaffIds,
      managerStaffIds: form.managerStaffIds
    }
    let id = props.complaintId
    if (id) {
      await complaintApi.update(id, payload)
    } else {
      const created: any = await complaintApi.create(payload)
      id = created.id
    }
    const files = fileList.value.map((f: any) => f.raw).filter(Boolean)
    if (files.length) await complaintApi.uploadAttachments(id as number, files)
    ElMessage.success(props.complaintId ? '客诉已更新' : '客诉登记成功')
    emit('saved')
    close(false)
  } finally { saving.value = false }
}
</script>

<style scoped>
.complaint-form { padding-right: 12px; }
.brand-row { display: flex; gap: 8px; width: 100%; }
.brand-suggest { display: flex; justify-content: space-between; gap: 16px; }
.muted { color: #909399; font-size: 12px; }
.attachment-box { width: 100%; }
.attachment-item { display: inline-block; margin-right: 10px; text-align: center; vertical-align: top; }
.thumb { width: 96px; height: 96px; border-radius: 6px; cursor: pointer; }
</style>
