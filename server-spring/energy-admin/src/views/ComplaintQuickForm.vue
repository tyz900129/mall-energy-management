<template>
  <div class="quick-page">
    <header class="quick-header">
      <span>客诉快速登记</span>
      <span class="sub">万悦城商业综合体</span>
    </header>

    <main class="quick-body">
      <div v-if="successNo" class="success-card">
        <el-result icon="success" title="登记成功" :sub-title="`客诉编号：${successNo}`">
          <template #extra>
            <el-button type="primary" @click="restart">继续登记</el-button>
          </template>
        </el-result>
      </div>

      <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" class="quick-form">
        <el-form-item label="发生日期" prop="occurDate">
          <el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>

        <el-form-item label="品牌 / 铺位" prop="brandName">
          <el-autocomplete v-model="form.brandName" :fetch-suggestions="searchBrands" value-key="brandName"
                           style="width:100%" placeholder="输入品牌或铺位名称" @select="onBrandSelect" @input="clearBrand">
            <template #default="{ item }">
              <div class="suggest">
                <div>{{ item.brandName }}</div>
                <small>{{ item.businessTypeLabel }} · {{ item.floorLabel || '-' }}</small>
              </div>
            </template>
          </el-autocomplete>
          <div class="hint">也可用手机相机扫描铺位二维码自动带入品牌</div>
        </el-form-item>

        <el-form-item label="业态" prop="businessType">
          <el-select v-model="form.businessType" style="width:100%" placeholder="选择业态">
            <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="客诉类别" prop="category">
          <div class="chips">
            <span v-for="item in dicts.CATEGORY" :key="item.itemValue" class="chip"
                  :class="{ active: form.category === item.itemValue }" @click="form.category = item.itemValue">
              {{ item.itemLabel }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="案例内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="1000" show-word-limit
                    placeholder="顾客诉求、现场情况、已采取措施" />
        </el-form-item>

        <el-form-item label="现场照片">
          <el-upload v-model:file-list="fileList" :auto-upload="false" accept="image/*" capture="environment"
                     list-type="picture-card" :limit="6" :on-exceed="onExceed">
            <el-icon><Camera /></el-icon>
          </el-upload>
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="saving" @click="submit">提交登记</el-button>
      </el-form>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { complaintApi } from '@/api/complaint'

const route = useRoute()
const formRef = ref<FormInstance>()
const saving = ref(false)
const successNo = ref('')
const fileList = ref<any[]>([])
const businessTypes = ref<any[]>([])
const dicts = reactive<Record<string, any[]>>({ CATEGORY: [], CHANNEL: [], SATISFACTION: [], TAG: [], STATUS: [] })

const form = reactive({
  occurDate: new Date().toISOString().slice(0, 10),
  brandName: '',
  stallId: null as number | null,
  brandId: null as number | null,
  merchantId: null as number | null,
  merchantName: '',
  businessType: '',
  floorCode: '',
  businessCategory: '',
  category: '',
  content: '',
  managerStaffIds: null as number[] | null
})

const rules: FormRules = {
  occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
  brandName: [{ required: true, message: '请选择品牌/铺位', trigger: 'change' }],
  businessType: [{ required: true, message: '请选择业态', trigger: 'change' }],
  category: [{ required: true, message: '请选择客诉类别', trigger: 'change' }],
  content: [{ required: true, message: '请填写案例内容', trigger: 'blur' }]
}

onMounted(async () => {
  const [biz, dic] = await Promise.all([
    complaintApi.businessTypes() as Promise<any>,
    complaintApi.dicts() as Promise<any>
  ])
  businessTypes.value = biz.businessTypes || []
  dicts.CATEGORY = (dic || []).filter((d: any) => d.dictType === 'CATEGORY')
  const token = (route.query.token as string) || ''
  if (token) await loadByToken(token)
})

async function loadByToken(token: string) {
  try {
    const data: any = await complaintApi.brandByToken(token)
    applyBrand(data)
    ElMessage.success('已识别铺位：' + data.brandName)
  } catch {
    ElMessage.warning('二维码无效，请手动选择品牌')
  }
}

let timer: any = null
function searchBrands(keyword: string, callback: (items: any[]) => void) {
  clearTimeout(timer)
  timer = setTimeout(async () => {
    const rows: any = await complaintApi.brands(keyword, form.businessType)
    callback((rows || []).map((r: any) => ({ ...r, value: r.brandName })))
  }, 200)
}

function clearBrand() {
  form.stallId = null
  form.brandId = null
}

function onBrandSelect(item: any) { applyBrand(item) }

function applyBrand(item: any) {
  form.brandName = item.brandName
  form.stallId = item.stallId || null
  form.brandId = item.brandId || null
  form.merchantId = item.merchantId || null
  form.merchantName = item.merchantName || ''
  form.businessType = item.businessType || form.businessType
  form.floorCode = item.floorCode || ''
  form.businessCategory = item.businessCategory || ''
}

function onExceed() { ElMessage.warning('最多上传 6 张照片') }

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      occurDate: form.occurDate,
      stallId: form.stallId,
      brandId: form.brandId,
      brandName: form.brandName,
      merchantId: form.merchantId,
      merchantName: form.merchantName || null,
      businessType: form.businessType,
      businessCategory: form.businessCategory || null,
      floorCode: form.floorCode || null,
      category: form.category,
      content: form.content,
      managerStaffIds: form.managerStaffIds
    }
    const created: any = await complaintApi.create(payload)
    const files = fileList.value.map((f: any) => f.raw).filter(Boolean)
    if (files.length) await complaintApi.uploadAttachments(created.id, files)
    successNo.value = created.complaintNo
  } finally { saving.value = false }
}

function restart() {
  successNo.value = ''
  fileList.value = []
  Object.assign(form, {
    occurDate: new Date().toISOString().slice(0, 10),
    brandName: '', stallId: null, brandId: null, merchantId: null, merchantName: '',
    businessType: '', floorCode: '', businessCategory: '', category: '', content: ''
  })
}
</script>

<style scoped>
.quick-page { min-height: 100vh; background: #f4f6f9; }
.quick-header { background: linear-gradient(135deg, #1f6feb, #3b8cff); color: #fff; padding: 18px 20px; font-size: 20px; font-weight: 600; }
.quick-header .sub { display: block; font-size: 12px; font-weight: 400; opacity: .85; margin-top: 4px; }
.quick-body { padding: 14px; }
.quick-form { background: #fff; border-radius: 10px; padding: 16px; }
.success-card { background: #fff; border-radius: 10px; padding: 30px 10px; }
.hint { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 4px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { padding: 6px 12px; border-radius: 16px; background: #f2f3f5; font-size: 13px; cursor: pointer; }
.chip.active { background: #1f6feb; color: #fff; }
.suggest { display: flex; flex-direction: column; line-height: 1.4; }
.suggest small { color: #909399; }
.submit-btn { width: 100%; margin-top: 8px; }
</style>
