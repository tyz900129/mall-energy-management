<template>
  <div class="page">
    <div class="page-header"><div class="page-title">用户管理</div><el-button type="primary" @click="openCreate">新增用户</el-button></div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="用户名/姓名/手机/邮箱" clearable style="width:260px" @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px;margin-left:8px"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select>
      <el-button style="margin-left:8px" @click="search">查询</el-button>
    </div>
    <div class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="username" label="用户名" min-width="120" /><el-table-column prop="displayName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机" min-width="130" /><el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
        <el-table-column label="角色" min-width="180"><template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" size="small" style="margin-right:4px">{{ roleName(role) }}</el-tag><span v-if="!row.roles?.length">—</span></template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="170"><template #default="{row}">{{ formatDateTime(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="操作" width="260" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="primary" @click="openRoles(row)">分配角色</el-button><el-button link type="warning" @click="openPassword(row)">重置密码</el-button>
          <el-popconfirm v-if="row.username !== 'admin'" title="确定删除该用户？" @confirm="remove(row)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" style="margin-top:12px;justify-content:flex-end" @current-change="load" @size-change="sizeChanged" />
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item><el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item v-if="!form.id" label="角色" prop="roleId"><el-select v-model="form.roleId" placeholder="请选择角色" style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="rolesVisible" title="设置角色" width="460px"><div style="margin-bottom:14px">用户：{{ current?.username }}</div><el-radio-group v-model="selectedRoleId" class="role-list"><el-radio v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</el-radio></el-radio-group><template #footer><el-button @click="rolesVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button></template></el-dialog>
    <el-dialog v-model="passwordVisible" title="重置密码" width="440px"><el-alert type="warning" :closable="false" :title="`正在重置用户 ${current?.username} 的密码`" /><el-form style="margin-top:18px" label-width="90px"><el-form-item label="临时密码"><el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item></el-form><template #footer><el-button @click="passwordVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="savePassword">确认重置</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { systemRoleApi, systemUserApi, type SystemRole, type SystemUser } from '@/api/system'
import { formatDateTime } from '@/utils/format'
const rows=ref<SystemUser[]>([]),roles=ref<SystemRole[]>([]),total=ref(0),loading=ref(false),saving=ref(false)
const editVisible=ref(false),rolesVisible=ref(false),passwordVisible=ref(false),formRef=ref<FormInstance>()
const query=reactive<{keyword:string;status:number|undefined}>({keyword:'',status:undefined}),page=reactive({pageNum:1,pageSize:20})
const form=reactive<any>({id:0,username:'',displayName:'',phone:'',email:'',password:'',status:1,roleId:undefined})
const current=ref<SystemUser>(),selectedRoleId=ref<number>(),newPassword=ref('')
const rules:FormRules={username:[{required:true,message:'请输入用户名'},{pattern:/^[A-Za-z0-9_.-]{3,64}$/,message:'3-64位字母、数字或 _ . -'}],password:[{required:true,message:'请输入初始密码'},{min:6,max:64,message:'密码长度为6-64位'}],roleId:[{required:true,message:'请选择角色'}],email:[{type:'email',message:'邮箱格式不正确'}]}
const roleName=(code:string)=>roles.value.find(r=>r.code===code)?.name||code
async function load(){loading.value=true;try{const r:any=await systemUserApi.page({...page,keyword:query.keyword||undefined,status:query.status});rows.value=r.records||[];total.value=Number(r.total)||0}finally{loading.value=false}}
function search(){page.pageNum=1;load()} function sizeChanged(){page.pageNum=1;load()}
function reset(){Object.assign(form,{id:0,username:'',displayName:'',phone:'',email:'',password:'',status:1,roleId:undefined});formRef.value?.clearValidate()}
function openCreate(){reset();editVisible.value=true} function openEdit(row:any){reset();Object.assign(form,{...row,password:''});editVisible.value=true}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;saving.value=true;try{const payload={username:form.username,displayName:form.displayName||undefined,phone:form.phone||undefined,email:form.email||undefined,status:form.status,password:form.id?undefined:form.password,roleIds:form.id?undefined:[form.roleId]};if(form.id)await systemUserApi.update(form.id,payload);else await systemUserApi.create(payload);ElMessage.success('保存成功');editVisible.value=false;await load()}finally{saving.value=false}}
function openRoles(row:any){current.value=row;selectedRoleId.value=row.roleIds?.[0];rolesVisible.value=true} async function saveRoles(){if(!current.value)return;if(!selectedRoleId.value){ElMessage.warning('请选择角色');return}saving.value=true;try{await systemUserApi.assignRoles(current.value.id,[selectedRoleId.value]);ElMessage.success('角色已更新');rolesVisible.value=false;await load()}finally{saving.value=false}}
function openPassword(row:any){current.value=row;newPassword.value='';passwordVisible.value=true} async function savePassword(){if(!current.value)return;if(newPassword.value.length<6||newPassword.value.length>64){ElMessage.warning('密码长度必须为6-64位');return}saving.value=true;try{await systemUserApi.resetPassword(current.value.id,newPassword.value);ElMessage.success('密码重置成功');passwordVisible.value=false}finally{saving.value=false}}
async function remove(row:any){await systemUserApi.remove(row.id);ElMessage.success('用户已删除');if(rows.value.length===1&&page.pageNum>1)page.pageNum--;await load()}
onMounted(async()=>{roles.value=await systemRoleApi.list() as any;await load()})
</script>

<style scoped>
.role-list{display:flex;flex-direction:column;align-items:flex-start;gap:12px}
</style>
