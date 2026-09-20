<template>
  <div class="page utility-bill-page">
    <div class="page-header">
      <div><div class="page-title">水电账单录入</div><div class="hint">每个月份保存一条水电费账单，账单数据与设备同步用量分开存储</div></div>
      <el-button v-if="canEdit" type="primary" @click="openForm()">新增账单</el-button>
    </div>

    <el-card class="filter-card">
      <div class="filters">
        <el-date-picker v-model="monthRange" type="monthrange" value-format="YYYY-MM" range-separator="至" start-placeholder="开始月份" end-placeholder="结束月份" :disabled-date="futureMonth" />
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="monthRange=null;load()">全部</el-button>
      </div>
    </el-card>

    <el-alert class="storage-note" type="info" :closable="false" show-icon title="账单用量仅用于账单同比图表，不会覆盖设备采集的月度用量。" />

    <el-card>
      <el-table class="desktop-table" :data="rows" v-loading="loading" border>
        <el-table-column prop="bill_month" label="年月份" width="110" fixed />
        <el-table-column label="电费账单金额" min-width="145"><template #default="{row}">¥ {{ money(row.electric_amount) }}</template></el-table-column>
        <el-table-column label="电费账单用量" min-width="155"><template #default="{row}">{{ number(row.electric_usage) }} kWh</template></el-table-column>
        <el-table-column label="水费账单金额" min-width="145"><template #default="{row}">¥ {{ money(row.water_amount) }}</template></el-table-column>
        <el-table-column label="水费账单用量" min-width="155"><template #default="{row}">{{ number(row.water_usage) }} m³</template></el-table-column>
        <el-table-column label="最后更新" width="175"><template #default="{row}">{{ formatDateTime(row.updated_at) }}</template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="125" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openForm(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="mobile-list" v-loading="loading">
        <div v-for="row in rows" :key="row.id" class="bill-card">
          <div class="bill-card-head"><b>{{ row.bill_month }}</b><div v-if="canEdit"><el-button link type="primary" @click="openForm(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></div></div>
          <div class="bill-grid"><span>电费金额<strong>¥ {{ money(row.electric_amount) }}</strong></span><span>电费用量<strong>{{ number(row.electric_usage) }} kWh</strong></span><span>水费金额<strong>¥ {{ money(row.water_amount) }}</strong></span><span>水费用量<strong>{{ number(row.water_usage) }} m³</strong></span></div>
          <small>更新：{{ formatDateTime(row.updated_at) }}</small>
        </div>
      </div>
      <el-empty v-if="!loading&&!rows.length" description="暂无账单记录" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId?'编辑月度账单':'新增月度账单'" width="560px" destroy-on-close>
      <el-form label-width="145px" class="bill-form">
        <el-form-item label="年月份" required><el-date-picker v-model="form.billMonth" type="month" value-format="YYYY-MM" placeholder="选择账单月份" :disabled-date="futureMonth" /></el-form-item>
        <el-form-item label="电费账单金额" required><el-input-number v-model="form.electricAmount" :min="0" :precision="2" :step="100" controls-position="right"/><span class="suffix">元</span></el-form-item>
        <el-form-item label="电费账单用量" required><el-input-number v-model="form.electricUsage" :min="0" :precision="6" :step="100" controls-position="right"/><span class="suffix">kWh</span></el-form-item>
        <el-form-item label="水费账单金额" required><el-input-number v-model="form.waterAmount" :min="0" :precision="2" :step="100" controls-position="right"/><span class="suffix">元</span></el-form-item>
        <el-form-item label="水费账单用量" required><el-input-number v-model="form.waterUsage" :min="0" :precision="6" :step="10" controls-position="right"/><span class="suffix">m³</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { energyApi } from '@/api/energy'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const userStore=useUserStore(),canEdit=computed(()=>userStore.user?.roles?.some(role=>['SUPER_ADMIN','SYSTEM_ADMIN'].includes(role)))
const rows=ref<any[]>([]),loading=ref(false),saving=ref(false),dialogVisible=ref(false),editingId=ref<number|null>(null),monthRange=ref<string[]|null>(defaultRange())
const form=reactive<any>({billMonth:'',electricAmount:null,electricUsage:null,waterAmount:null,waterUsage:null})

function monthKey(date:Date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`}
function defaultRange(){const end=new Date(),start=new Date(end.getFullYear(),end.getMonth()-23,1);return[monthKey(start),monthKey(end)]}
function futureMonth(date:Date){return monthKey(date)>monthKey(new Date())}
function number(value:any){return Number(value||0).toLocaleString('zh-CN',{maximumFractionDigits:6})}
function money(value:any){return Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}
async function load(){loading.value=true;try{const params=monthRange.value?.length===2?{startMonth:monthRange.value[0],endMonth:monthRange.value[1]}:{};rows.value=await energyApi.bills(params) as any}finally{loading.value=false}}
function openForm(row?:any){editingId.value=row?.id??null;Object.assign(form,{billMonth:row?.bill_month||'',electricAmount:row?Number(row.electric_amount):null,electricUsage:row?Number(row.electric_usage):null,waterAmount:row?Number(row.water_amount):null,waterUsage:row?Number(row.water_usage):null});dialogVisible.value=true}
async function save(){if(!form.billMonth||[form.electricAmount,form.electricUsage,form.waterAmount,form.waterUsage].some(value=>value===null||value===undefined||Number(value)<0))return ElMessage.warning('请完整填写年月份、账单金额和用量');saving.value=true;try{const payload={...form};editingId.value?await energyApi.updateBill(editingId.value,payload):await energyApi.createBill(payload);ElMessage.success('账单已保存');dialogVisible.value=false;await load()}finally{saving.value=false}}
async function remove(row:any){await ElMessageBox.confirm(`确认删除 ${row.bill_month} 的水电账单？`,'删除账单',{type:'warning'});await energyApi.deleteBill(row.id);ElMessage.success('账单已删除');await load()}
onMounted(load)
</script>

<style scoped>
.page{padding:18px}.page-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-title{font-size:22px;font-weight:700}.hint{color:#909399;font-size:13px;margin-top:5px}.filter-card,.storage-note{margin-bottom:14px}.filters{display:flex;gap:10px;align-items:center}.filters :deep(.el-date-editor){width:320px}.mobile-list{display:none}.bill-form :deep(.el-input-number){width:260px}.suffix{margin-left:10px;color:#909399}.bill-card{padding:14px 0;border-bottom:1px solid #ebeef5}.bill-card-head{display:flex;align-items:center;justify-content:space-between}.bill-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:12px 0}.bill-grid span{font-size:12px;color:#909399}.bill-grid strong{display:block;color:#303133;font-size:14px;margin-top:5px}.bill-card small{color:#909399}@media(max-width:768px){.page{padding:10px}.page-header{align-items:flex-start;gap:10px}.page-title{font-size:20px}.filters{flex-direction:column;align-items:stretch}.filters>*{width:100%!important}.desktop-table{display:none}.mobile-list{display:block}.bill-form :deep(.el-form-item){display:block}.bill-form :deep(.el-form-item__label){display:block;width:100%!important;text-align:left}.bill-form :deep(.el-form-item__content){margin-left:0!important}.bill-form :deep(.el-date-editor),.bill-form :deep(.el-input-number){width:calc(100% - 45px)}:deep(.el-dialog){width:calc(100% - 24px)!important}.bill-grid{grid-template-columns:1fr}}
</style>
