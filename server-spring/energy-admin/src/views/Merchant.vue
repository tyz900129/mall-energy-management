<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">商户账户管理</div><div class="page-note">账户来自源平台同步；商户代码可本地维护，关联铺位按账户设备的铺位绑定自动识别。</div></div></div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索账户编号/名称/手机号" style="width:280px" clearable @keyup.enter="load" />
      <el-switch v-model="hideDisabled" active-text="隐藏停用账户" style="margin-left:12px" @change="load"/>
      <el-button type="primary" @click="load" style="margin-left:8px">查询</el-button>
    </div>
    <div class="table-card">
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="accountNumber" label="账户编号" min-width="150"/>
        <el-table-column label="商户代码" width="145"><template #default="{row}"><span>{{row.merchantCode||'待补充'}}</span><el-button v-if="isAdmin" link type="primary" size="small" @click="editCode(row)">编辑</el-button></template></el-table-column>
        <el-table-column prop="accountName" label="账户名称（当前商户）" min-width="220"/>
        <el-table-column prop="userName" label="用户名称" min-width="180"/>
        <el-table-column prop="mobilePhone" label="手机号" width="135"><template #default="{row}">{{row.mobilePhone||'-'}}</template></el-table-column>
        <el-table-column label="关联铺位" min-width="150"><template #default="{row}"><el-tag v-for="code in row.stallCodes" :key="code" size="small" class="stall-tag">{{code}}</el-tag><span v-if="!row.stallCodes?.length">未匹配</span></template></el-table-column>
        <el-table-column prop="balance" label="余额" width="100"/>
        <el-table-column prop="arrears" label="欠费" width="100"/>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="enabled(row.platformStatus)?'success':'info'" size="small">{{enabled(row.platformStatus)?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column prop="lastSyncedAt" label="同步时间" width="180"><template #default="{row}">{{formatDateTime(row.lastSyncedAt)}}</template></el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load"/>
    </div>
    <el-dialog v-model="codeDialog" title="维护商户代码" width="420px"><el-form label-width="90px"><el-form-item label="账户名称"><el-input :model-value="editing?.accountName" disabled/></el-form-item><el-form-item label="商户代码"><el-input v-model="merchantCode" maxlength="6" placeholder="请输入连续六位数字"/></el-form-item></el-form><template #footer><el-button @click="codeDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCode">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup lang="ts">
import {computed,ref,reactive,onMounted} from 'vue'
import {accountApi} from '@/api/merchant'
import {formatDateTime} from '@/utils/format'
import {ElMessage} from 'element-plus'
import {useUserStore} from '@/stores/user'
const userStore=useUserStore(),isAdmin=computed(()=>userStore.user?.roles?.some(r=>['SUPER_ADMIN','SYSTEM_ADMIN'].includes(r)))
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),hideDisabled=ref(true)
const codeDialog=ref(false),saving=ref(false),editing=ref<any>(null),merchantCode=ref('')
const page=reactive({pageNum:1,pageSize:20}),query=reactive({keyword:''})
const enabled=(s:any)=>String(s)==='1'||s==='ACTIVE'||s==='ENABLED'
async function load(){loading.value=true;try{const r:any=await accountApi.page({...page,keyword:query.keyword||undefined,showDisabled:!hideDisabled.value});rows.value=r.records||[];total.value=Number(r.total)||0}finally{loading.value=false}}
function editCode(row:any){editing.value=row;merchantCode.value=row.merchantCode||'';codeDialog.value=true}
async function saveCode(){if(merchantCode.value&&!/^\d{6}$/.test(merchantCode.value)){ElMessage.warning('商户代码必须是连续六位数字');return}saving.value=true;try{await accountApi.updateMerchantCode(editing.value.accountNumber,merchantCode.value);ElMessage.success('商户代码已保存');codeDialog.value=false;await load()}finally{saving.value=false}}
onMounted(load)
</script>
<style scoped>.page-note{margin-top:6px;color:#909399;font-size:13px}.stall-tag{margin:2px 4px 2px 0}.pager{margin-top:12px;display:flex;justify-content:flex-end}@media(max-width:768px){.toolbar{display:flex;flex-wrap:wrap;gap:8px}.toolbar>*{margin-left:0!important;max-width:100%}}</style>

