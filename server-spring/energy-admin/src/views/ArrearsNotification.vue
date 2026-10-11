<template>
  <div class="page">
    <div class="page-header">
      <div><div class="page-title">欠费通知</div><div class="page-note">每天 08:00、08:25 自动获取源平台欠费账户，并向指定 Webhook 群组推送。</div></div>
      <el-button type="primary" :loading="syncing" @click="syncNow">立即同步并通知</el-button>
    </div>
    <el-card shadow="never" class="summary">
      <el-form inline><el-form-item label="账户搜索"><el-input v-model="query.keyword" clearable placeholder="账户编号、名称或手机号" @keyup.enter="search" /></el-form-item><el-form-item label="推送设置"><el-select v-model="query.excluded" clearable placeholder="全部"><el-option label="正常推送" :value="false"/><el-option label="不推送" :value="true"/></el-select></el-form-item><el-form-item><el-button @click="search">查询</el-button></el-form-item></el-form>
      <el-table :data="rows" v-loading="loading" border empty-text="暂无欠费数据，请先执行同步">
        <el-table-column prop="accountNumber" label="账户编号" min-width="155"/>
        <el-table-column label="账户/商户名称" min-width="210"><template #default="{row}">{{row.userName||row.accountName||'-'}}</template></el-table-column>
        <el-table-column prop="mobilePhone" label="手机号" width="135"><template #default="{row}">{{row.mobilePhone||'-'}}</template></el-table-column>
        <el-table-column prop="balance" label="余额" width="110"><template #default="{row}">{{money(row.balance)}}</template></el-table-column>
        <el-table-column prop="arrears" label="欠费金额" width="120"><template #default="{row}"><b class="arrears">{{money(row.arrears)}}</b></template></el-table-column>
        <el-table-column label="通知状态" width="120"><template #default="{row}"><el-tag :type="row.excluded?'info':'danger'">{{row.excluded?'不推送':'正常推送'}}</el-tag></template></el-table-column>
        <el-table-column prop="exclusionRemark" label="不推送原因" min-width="160"><template #default="{row}">{{row.exclusionRemark||'-'}}</template></el-table-column>
        <el-table-column prop="lastSeenAt" label="获取时间" width="180"><template #default="{row}">{{formatDateTime(row.lastSeenAt)}}</template></el-table-column>
        <el-table-column label="操作" width="110" fixed="right"><template #default="{row}"><el-button link :type="row.excluded?'primary':'warning'" @click="toggle(row)">{{row.excluded?'恢复推送':'设为不推送'}}</el-button></template></el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search"/>
    </el-card>
    <el-card shadow="never" class="runs"><template #header>最近同步记录</template><el-table :data="runs" size="small" border><el-table-column prop="triggerType" label="触发方式" width="130"><template #default="{row}">{{triggerLabel(row.triggerType)}}</template></el-table-column><el-table-column prop="status" label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':row.status==='RUNNING'?'warning':'danger'">{{statusLabel(row.status)}}</el-tag></template></el-table-column><el-table-column prop="fetchedCount" label="获取" width="80"/><el-table-column prop="excludedCount" label="不推送" width="90"/><el-table-column prop="notifiedCount" label="已通知" width="90"/><el-table-column prop="totalArrears" label="通知欠费合计" width="130"><template #default="{row}">{{money(row.totalArrears)}}</template></el-table-column><el-table-column prop="startedAt" label="开始时间" width="180"><template #default="{row}">{{formatDateTime(row.startedAt)}}</template></el-table-column><el-table-column prop="errorMessage" label="结果" min-width="180"><template #default="{row}">{{row.errorMessage||'完成'}}</template></el-table-column></el-table></el-card>
  </div>
</template>
<script setup lang="ts">
import{onMounted,reactive,ref}from'vue';import{ElMessage,ElMessageBox}from'element-plus';import{arrearsApi}from'@/api/arrears';import{formatDateTime}from'@/utils/format';
const rows=ref<any[]>([]),runs=ref<any[]>([]),total=ref(0),loading=ref(false),syncing=ref(false);const page=reactive({pageNum:1,pageSize:20}),query=reactive<{keyword:string;excluded?:boolean}>({keyword:''});
const money=(v:any)=>v==null?'-':`${Number(v).toFixed(2)} 元`;const triggerLabel=(v:string)=>({MANUAL:'手动',SCHEDULED_0800:'08:00 自动',SCHEDULED_0825:'08:25 自动'}as any)[v]||v;const statusLabel=(v:string)=>({RUNNING:'执行中',SUCCESS:'成功',FAILED:'失败'}as any)[v]||v;
async function load(){loading.value=true;try{const r:any=await arrearsApi.accounts({...page,keyword:query.keyword||undefined,excluded:query.excluded});rows.value=r.records||[];total.value=Number(r.total||0);runs.value=await arrearsApi.runs() as any}finally{loading.value=false}}
function search(){page.pageNum=1;load()}
async function syncNow(){await ElMessageBox.confirm('将立即从源平台获取欠费账户，并向已配置的欠费通知 Webhook 发送消息，是否继续？','立即同步');syncing.value=true;try{await arrearsApi.sync();ElMessage.success('欠费账户同步和通知已完成');await load()}finally{syncing.value=false}}
async function toggle(row:any){if(row.excluded){await arrearsApi.exclusion(row.accountNumber,false);ElMessage.success('已恢复该账户推送')}else{const p=await ElMessageBox.prompt('请输入不推送原因，后续可随时恢复','设置不推送账户',{inputValidator:v=>!!v?.trim()||'请填写原因'});await arrearsApi.exclusion(row.accountNumber,true,p.value.trim());ElMessage.success('已设为不推送账户')}await load()}
onMounted(load)
</script>
<style scoped>.page-note{margin-top:6px;color:#909399;font-size:13px}.summary{margin-top:12px}.runs{margin-top:14px}.arrears{color:#f56c6c}.pager{margin-top:12px;display:flex;justify-content:flex-end}@media(max-width:768px){.page-header{align-items:flex-start;gap:10px}.page-header>.el-button{flex:none}.el-form{display:flex;flex-direction:column}.el-form-item{margin-right:0}.pager{overflow-x:auto;justify-content:flex-start}}</style>
