<template>
  <div class="page construction-form-page">
    <div class="page-header">
      <div>
        <div class="page-title">商户施工申请</div>
        <div class="page-subtitle">提交后立即生成施工许可证，施工期间可扫码巡检，完工后由申请人发起验收。</div>
      </div>
      <el-button @click="router.push('/construction')">返回列表</el-button>
    </div>

    <el-alert :title="statusHint" type="info" show-icon :closable="false" class="status-hint" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="form-card">
      <section>
        <h3>申请事项</h3>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="流程类型" prop="workflowType">
              <el-select v-model="form.workflowType" style="width:100%">
                <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="施工位置" prop="stallId">
              <el-select v-model="form.stallId" filterable style="width:100%" placeholder="请选择楼层和铺位号" no-data-text="没有符合当前流程状态的铺位" @change="onStallChange">
                <el-option v-for="stall in stalls" :key="stall.id" :label="stallLabel(stall)" :value="stall.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="施工开始时间" prop="constructionStartTime"><el-date-picker v-model="form.constructionStartTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="施工结束时间" prop="constructionEndTime"><el-date-picker v-model="form.constructionEndTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item></el-col>
        </el-row>
      </section>

      <section>
        <h3>商户信息</h3>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8"><el-form-item label="商户名称" prop="merchantName"><el-input v-model="form.merchantName" maxlength="128" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="商户负责人姓名" prop="merchantPrincipalName"><el-input v-model="form.merchantPrincipalName" maxlength="64" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="联系电话" prop="merchantPrincipalPhone"><el-input v-model="form.merchantPrincipalPhone" maxlength="32" inputmode="tel" /></el-form-item></el-col>
        </el-row>
      </section>

      <section>
        <h3>施工方信息</h3>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8"><el-form-item label="施工公司名称" prop="constructionCompanyName"><el-input v-model="form.constructionCompanyName" maxlength="128" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="施工负责人姓名" prop="constructionPrincipalName"><el-input v-model="form.constructionPrincipalName" maxlength="64" /></el-form-item></el-col>
          <el-col :xs="24" :sm="8"><el-form-item label="施工负责人联系电话" prop="constructionPrincipalPhone"><el-input v-model="form.constructionPrincipalPhone" maxlength="32" inputmode="tel" /></el-form-item></el-col>
        </el-row>
      </section>

      <section>
        <h3>内部负责人</h3>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="6"><el-form-item label="物业部工程专员姓名" prop="propertyEngineerName"><el-input v-model="form.propertyEngineerName" :disabled="isVerifier" maxlength="64" /></el-form-item></el-col>
          <el-col :xs="24" :sm="6"><el-form-item label="工程专员联系电话" prop="propertyEngineerPhone"><el-input v-model="form.propertyEngineerPhone" :disabled="isVerifier" maxlength="32" inputmode="tel" /></el-form-item></el-col>
          <el-col :xs="24" :sm="6"><el-form-item label="商管负责人" prop="commercialManagerName"><el-input v-model="form.commercialManagerName" :disabled="!isVerifier" maxlength="64" /></el-form-item></el-col>
          <el-col :xs="24" :sm="6"><el-form-item label="商管负责人电话" prop="commercialManagerPhone"><el-input v-model="form.commercialManagerPhone" :disabled="!isVerifier" maxlength="32" inputmode="tel" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </section>

      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="save">提交申请并生成许可证</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { constructionApi, type ConstructionWorkflowType } from '@/api/construction'
import { stallApi, type Stall } from '@/api/merchant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const allStalls = ref<Stall[]>([])
const saving = ref(false)
const requestKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`
const typeOptions = [
  { value: 'MERCHANT_CONSTRUCTION_ENTRY', label: '施工入场申请' },
  { value: 'MERCHANT_CONSTRUCTION_EXIT', label: '撤场施工申请' }
]
const isVerifier = computed(() => !!userStore.user?.roles?.includes('DEVICE_VERIFIER'))
const form = reactive({
  requestKey,
  workflowType: 'MERCHANT_CONSTRUCTION_ENTRY' as ConstructionWorkflowType,
  stallId: null as number | null,
  constructionStartTime: '', constructionEndTime: '',
  merchantName: '', merchantPrincipalName: '', merchantPrincipalPhone: '',
  constructionCompanyName: '', constructionPrincipalName: '', constructionPrincipalPhone: '',
  propertyEngineerName: '', propertyEngineerPhone: '', commercialManagerName: '', commercialManagerPhone: '', remark: ''
})
const stalls = computed(() => allStalls.value.filter(item => form.workflowType === 'MERCHANT_CONSTRUCTION_ENTRY'
  ? item.status === 'AVAILABLE'
  : item.status === 'OCCUPIED'))
const required = (message: string) => [{ required: true, message, trigger: 'blur' }]
const rules: FormRules = {
  workflowType: required('请选择流程类型'), stallId: required('请选择施工铺位'),
  constructionStartTime: required('请选择施工开始时间'), constructionEndTime: required('请选择施工结束时间'),
  merchantName: required('请输入商户名称'), merchantPrincipalName: required('请输入商户负责人姓名'), merchantPrincipalPhone: required('请输入商户负责人电话'),
  constructionCompanyName: required('请输入施工公司名称'), constructionPrincipalName: required('请输入施工负责人姓名'), constructionPrincipalPhone: required('请输入施工负责人电话'),
  propertyEngineerName: required('请输入物业部工程专员姓名'), propertyEngineerPhone: required('请输入工程专员电话'),
  commercialManagerName: required('请输入商管负责人'), commercialManagerPhone: required('请输入商管负责人电话')
}
const statusHint = computed(() => ({
  MERCHANT_CONSTRUCTION_ENTRY: '仅可选择“空置”铺位；提交后进入“装修中”，验收合格后自动变为“在用”。',
  MERCHANT_CONSTRUCTION_EXIT: '仅可选择“在用”铺位；提交后进入“撤场施工中”，验收合格后自动变为“空置”。'
}[form.workflowType]))

watch(() => form.workflowType, () => {
  if (form.stallId && !stalls.value.some(item => item.id === form.stallId)) {
    form.stallId = null
    form.merchantName = ''
  }
})

function stallLabel(stall: Stall) { return `${stall.floorName || stall.floorCode || '未设置楼层'} · ${stall.stallCode}` }
function onStallChange(id: number) {
  const stall = stalls.value.find(item => item.id === id)
  if (stall && !form.merchantName) form.merchantName = stall.currentMerchantName || ''
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  if (form.constructionEndTime <= form.constructionStartTime) { ElMessage.warning('施工结束时间必须晚于开始时间'); return }
  saving.value = true
  try {
    const created: any = await constructionApi.create({ ...form })
    ElMessage.success('施工申请已提交，许可证已生成')
    router.replace(`/construction/${created.id}`)
  } finally { saving.value = false }
}
onMounted(async () => {
  await userStore.fetchMe()
  const name=userStore.user?.displayName||userStore.user?.username||''
  const phone=userStore.user?.phone||''
  if(isVerifier.value){form.propertyEngineerName=name;form.propertyEngineerPhone=phone}else{form.commercialManagerName=name;form.commercialManagerPhone=phone}
  const result: any = await stallApi.page({ pageNum: 1, pageSize: 500 })
  allStalls.value = result.records || []
})
</script>

<style scoped>
.construction-form-page { max-width: 1180px; margin: 0 auto; }
.status-hint { margin-bottom: 14px; }
.form-card { background:#fff; border-radius:8px; padding:20px; }
section + section { border-top:1px solid #ebeef5; margin-top:8px; padding-top:8px; }
h3 { margin:4px 0 16px; font-size:16px; color:#303133; }
.form-actions { display:flex; justify-content:flex-end; gap:10px; padding-top:12px; }
@media (max-width: 768px) {
  .construction-form-page { padding:12px; }
  .form-card { padding:14px; }
  .form-actions { position:sticky; bottom:0; z-index:3; margin:0 -14px -14px; padding:12px 14px; background:#fff; box-shadow:0 -4px 12px #0000000d; }
  .form-actions :deep(.el-button) { flex:1; margin:0; min-height:42px; }
}
</style>
