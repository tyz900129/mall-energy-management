<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">用户与权限</div><div class="page-subtitle">角色作为默认权限模板，个人权限用于精细调整</div></div><div><el-button v-if="canManageRoles" style="margin-right:8px" @click="roleDrawerVisible = true">角色管理</el-button><el-button v-if="canUserManage" style="margin-right:8px" @click="downloadTemplate">下载模板</el-button><el-button v-if="canUserManage" style="margin-right:8px" @click="openImport">导入用户</el-button><el-button v-if="canUserView" style="margin-right:8px" :loading="exporting" @click="exportUsers">导出</el-button><el-button v-if="canUserManage" type="primary" @click="openCreate">新增用户</el-button></div></div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="用户名/姓名/手机/邮箱" clearable style="width:260px" @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width:120px;margin-left:8px"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select>
      <el-button style="margin-left:8px" @click="search">查询</el-button>
    </div>
    <div class="table-card desktop-table">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="username" label="用户名" min-width="120" /><el-table-column prop="displayName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机" min-width="130" /><el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
        <el-table-column label="角色" min-width="180"><template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" size="small" style="margin-right:4px">{{ roleName(role) }}</el-tag><span v-if="!row.roles?.length">—</span></template></el-table-column>
        <el-table-column label="部门" min-width="110"><template #default="{ row }">{{ deptName(row.departmentId) }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="170"><template #default="{row}">{{ formatDateTime(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="操作" width="330" fixed="right"><template #default="{ row }">
          <el-button v-if="canUserManage" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="canUserManage" link type="primary" @click="openRoles(row)">分配角色</el-button><el-button v-if="canPermissionManage" link type="success" @click="openPermissions(row)">个人权限</el-button><el-button v-if="canUserManage" link type="warning" @click="openPassword(row)">重置密码</el-button>
          <el-popconfirm v-if="canUserManage && row.username !== 'admin'" title="确定删除该用户？" @confirm="remove(row)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" style="margin-top:12px;justify-content:flex-end" @current-change="load" @size-change="sizeChanged" />
    </div>

    <div class="mobile-cards" v-loading="loading">
      <el-card v-for="row in rows" :key="row.id" shadow="never">
        <div class="card-head">
          <div class="card-name"><b>{{ row.displayName || row.username }}</b><small>{{ row.username }}</small></div>
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </div>
        <div class="card-contact">
          <span v-if="row.phone">{{ row.phone }}</span>
          <span v-if="row.email">{{ row.email }}</span>
          <span v-if="!row.phone && !row.email" class="muted">未填写联系方式</span>
        </div>
        <div class="card-roles">
          <el-tag v-for="role in row.roles" :key="role" size="small">{{ roleName(role) }}</el-tag>
          <span v-if="!row.roles?.length" class="muted">未分配角色</span>
          <span class="muted">部门：{{ deptName(row.departmentId) }}</span>
        </div>
        <div class="muted card-time">最后登录：{{ formatDateTime(row.lastLoginAt) }}</div>
        <div class="op-toggle" @click="toggleOps(row.id)">
          <span>操作</span>
          <el-icon><component :is="isOpsOpen(row.id) ? ArrowUp : ArrowDown" /></el-icon>
        </div>
        <div v-show="isOpsOpen(row.id)" class="op-actions">
          <el-button v-if="canUserManage" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canUserManage" @click="openRoles(row)">分配角色</el-button>
          <el-button v-if="canPermissionManage" @click="openPermissions(row)">个人权限</el-button>
          <el-button v-if="canUserManage" @click="openPassword(row)">重置密码</el-button>
          <el-popconfirm v-if="canUserManage && row.username !== 'admin'" title="确定删除该用户？" @confirm="remove(row)"><template #reference><el-button type="danger" plain>删除</el-button></template></el-popconfirm>
        </div>
      </el-card>
      <el-empty v-if="!loading && !rows.length" description="暂无用户" />
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item><el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item v-if="!form.id" label="角色" prop="roleId"><el-select v-model="form.roleId" placeholder="请选择角色" style="width:100%"><el-option v-for="r in assignableRoles" :key="r.id" :label="r.name" :value="r.id" /></el-select></el-form-item>
        <el-form-item label="部门"><el-select v-model="form.departmentId" placeholder="未设置" clearable style="width:100%"><el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="rolesVisible" title="设置角色" width="460px"><div style="margin-bottom:14px">用户：{{ current?.username }}（部门：{{ deptName(current?.departmentId) }}）</div><el-radio-group v-model="selectedRoleId" class="role-list"><el-radio v-for="r in assignableRoles" :key="r.id" :value="r.id">{{ r.name }}<span v-if="r.deptName" class="role-dept">{{ r.deptName }}</span></el-radio></el-radio-group><template #footer><el-button @click="rolesVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button></template></el-dialog>
    <el-dialog v-model="passwordVisible" title="重置密码" width="440px"><el-alert type="warning" :closable="false" :title="`正在重置用户 ${current?.username} 的密码`" /><el-form style="margin-top:18px" label-width="90px"><el-form-item label="临时密码"><el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item></el-form><template #footer><el-button @click="passwordVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="savePassword">确认重置</el-button></template></el-dialog>
    <el-dialog v-model="permissionVisible" title="个人权限配置" width="min(860px, 94vw)" top="5vh" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>角色提供默认权限；“允许”用于额外授权，“禁止”会覆盖角色默认权限。未设置表示继续跟随角色。</template>
      </el-alert>
      <div class="permission-user">用户：<b>{{ permissionProfile?.username }}</b><span>当前角色：{{ (current?.roles||[]).map(roleName).join('、') || '未分配' }}</span></div>
      <el-scrollbar max-height="62vh" v-loading="permissionLoading">
        <el-collapse v-if="permissionProfile" v-model="activeModules">
          <el-collapse-item v-for="module in permissionProfile.modules" :key="module.code" :name="module.code">
            <template #title><b>{{ module.name }}</b><el-tag size="small" style="margin-left:10px">{{ module.permissions.length }} 项</el-tag></template>
            <div v-for="item in module.permissions" :key="item.code" class="permission-row">
              <div class="permission-name"><span>{{ item.name }}</span><small>{{ item.code }}</small></div>
              <el-tag size="small" :type="actionTag(item.actionType)">{{ actionName(item.actionType) }}</el-tag>
              <el-select v-model="permissionEffects[item.code]" style="width:128px">
                <el-option label="跟随角色" value="INHERIT" /><el-option label="额外允许" value="ALLOW" /><el-option label="明确禁止" value="DENY" />
              </el-select>
              <span class="effective" :class="{on:isEffective(item.code)}">{{ isEffective(item.code) ? '当前有效' : '当前无权限' }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-scrollbar>
      <template #footer><el-button @click="permissionVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="current?.username==='admin'" @click="savePermissions">保存个人权限</el-button></template>
    </el-dialog>
    <el-dialog v-model="importVisible" title="批量导入用户" width="min(920px, 96vw)" top="5vh" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>请先下载模板填写；导入默认先预览，确认后才会写库。单次最多 1000 行，已有用户默认跳过。</template>
      </el-alert>
      <div class="import-toolbar">
        <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls" :file-list="importFileList"
                   :on-change="onImportFileChange" :on-remove="onImportFileRemove">
          <el-button>选择 Excel 文件</el-button>
        </el-upload>
        <el-button type="primary" plain :loading="importLoading" :disabled="!importFile" @click="previewImport">预览</el-button>
      </div>
      <template v-if="importResult">
        <div class="import-summary">
          <el-tag>共 {{ importResult.totalRows }} 行</el-tag>
          <el-tag type="success">新建 {{ importResult.createCount }}</el-tag>
          <el-tag type="warning">更新 {{ importResult.updateCount }}</el-tag>
          <el-tag type="info">跳过 {{ importResult.skipCount }}</el-tag>
          <el-tag type="danger">失败 {{ importResult.failCount }}</el-tag>
        </div>
        <el-table :data="importResult.preview" border max-height="42vh" size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="username" label="用户名" min-width="110" />
          <el-table-column prop="displayName" label="姓名" min-width="90" />
          <el-table-column label="动作" width="80"><template #default="{ row }"><el-tag size="small" :type="row.action === '新建' ? 'success' : row.action === '更新' ? 'warning' : 'info'">{{ row.action }}</el-tag></template></el-table-column>
          <el-table-column prop="roleCode" label="角色编码" min-width="110" />
          <el-table-column prop="departmentName" label="部门" min-width="110" />
          <el-table-column prop="note" label="说明" min-width="200" show-overflow-tooltip />
        </el-table>
        <div v-if="importResult.errors?.length" class="import-issues">
          <el-alert type="error" :closable="false" :title="`失败 ${importResult.failCount} 行（最多展示 ${importResult.errors.length} 条）`" />
          <div v-for="item in importResult.errors" :key="`e-${item.row}-${item.username}`" class="issue-line">第 {{ item.row }} 行{{ item.username ? `（${item.username}）` : '' }}：{{ item.reason }}</div>
          <el-button v-if="importResult.errorToken" link type="danger" @click="downloadErrors">下载错误明细</el-button>
        </div>
        <div v-if="importResult.warnings?.length" class="import-issues">
          <el-alert type="warning" :closable="false" title="提示" />
          <div v-for="item in importResult.warnings" :key="`w-${item.row}-${item.username}`" class="issue-line">第 {{ item.row }} 行{{ item.username ? `（${item.username}）` : '' }}：{{ item.reason }}</div>
        </div>
      </template>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button v-if="importResult?.credentialsToken" type="success" @click="downloadCredentials">下载初始密码清单</el-button>
        <el-button v-if="importResult?.dryRun" type="primary" :loading="importLoading" :disabled="!importResult.totalRows" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>
    <RoleManagerDrawer v-model="roleDrawerVisible" @changed="reloadRoles" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import RoleManagerDrawer from './components/RoleManagerDrawer.vue'
import { systemDepartmentApi, systemRoleApi, systemUserApi, type SystemRole, type SystemDepartment, type SystemUser, type SysUserImportResult, type UserPermissionProfile, type UserPermissionOverride } from '@/api/system'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
const userStore=useUserStore()
const canUserManage=userStore.hasPermission('system.user.manage')
const canUserView=userStore.hasPermission('system.user.view')
const canPermissionManage=userStore.hasPermission('system.user.permission.manage')
const canManageRoles=computed(()=>userStore.hasPermission('system.user.permission.manage'))
const assignableRoles=computed(()=>roles.value.filter((r:any)=>r.isAssignable!==0))
const roleDrawerVisible=ref(false)
async function reloadRoles(){roles.value=await systemRoleApi.list() as any}
const rows=ref<SystemUser[]>([]),roles=ref<SystemRole[]>([]),departments=ref<SystemDepartment[]>([]),total=ref(0),loading=ref(false),saving=ref(false)
const editVisible=ref(false),rolesVisible=ref(false),passwordVisible=ref(false),permissionVisible=ref(false),formRef=ref<FormInstance>()
const query=reactive<{keyword:string;status:number|undefined}>({keyword:'',status:undefined}),page=reactive({pageNum:1,pageSize:20})
const form=reactive<any>({id:0,username:'',displayName:'',phone:'',email:'',password:'',status:1,roleId:undefined,departmentId:undefined})
const current=ref<SystemUser>(),selectedRoleId=ref<number>(),newPassword=ref('')
const importVisible=ref(false),importLoading=ref(false),exporting=ref(false)
const importFile=ref<File|null>(null),importFileList=ref<any[]>([]),importResult=ref<SysUserImportResult>()
const permissionProfile=ref<UserPermissionProfile>(),permissionLoading=ref(false),activeModules=ref<string[]>([])
const permissionEffects=reactive<Record<string,'INHERIT'|'ALLOW'|'DENY'>>({})
const expandedOps=ref<number[]>([])
function isOpsOpen(id:number){return expandedOps.value.includes(id)}
function toggleOps(id:number){expandedOps.value=isOpsOpen(id)?expandedOps.value.filter(item=>item!==id):[...expandedOps.value,id]}
const rules:FormRules={username:[{required:true,message:'请输入用户名'},{pattern:/^[A-Za-z0-9_.-]{3,64}$/,message:'3-64位字母、数字或 _ . -'}],password:[{required:true,message:'请输入初始密码'},{min:6,max:64,message:'密码长度为6-64位'}],roleId:[{required:true,message:'请选择角色'}],email:[{type:'email',message:'邮箱格式不正确'}]}
const roleName=(code:string)=>roles.value.find(r=>r.code===code)?.name||code
const deptName=(id?:number)=>departments.value.find(d=>d.id===id)?.name||'—'
async function load(){loading.value=true;try{const r:any=await systemUserApi.page({...page,keyword:query.keyword||undefined,status:query.status});rows.value=r.records||[];total.value=Number(r.total)||0}finally{loading.value=false}}
function search(){page.pageNum=1;load()} function sizeChanged(){page.pageNum=1;load()}
function reset(){Object.assign(form,{id:0,username:'',displayName:'',phone:'',email:'',password:'',status:1,roleId:undefined,departmentId:undefined});formRef.value?.clearValidate()}
function openCreate(){reset();editVisible.value=true} function openEdit(row:any){reset();Object.assign(form,{...row,password:''});editVisible.value=true}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;saving.value=true;try{const payload={username:form.username,displayName:form.displayName||undefined,phone:form.phone||undefined,email:form.email||undefined,status:form.status,password:form.id?undefined:form.password,roleIds:form.id?undefined:[form.roleId],departmentId:form.departmentId||undefined};if(form.id)await systemUserApi.update(form.id,payload);else await systemUserApi.create(payload);ElMessage.success('保存成功');editVisible.value=false;await load()}finally{saving.value=false}}
function openRoles(row:any){current.value=row;selectedRoleId.value=row.roleIds?.[0];rolesVisible.value=true} async function saveRoles(){if(!current.value)return;if(!selectedRoleId.value){ElMessage.warning('请选择角色');return}saving.value=true;try{await systemUserApi.assignRoles(current.value.id,[selectedRoleId.value]);const role=roles.value.find(r=>r.id===selectedRoleId.value);const userDept=current.value.departmentId;if(role?.deptId&&userDept&&role.deptId!==userDept)ElMessage.warning(`岗位「${role.name}」属于「${role.deptName||''}」，与用户当前部门「${deptName(userDept)}」不一致，请确认`);else ElMessage.success('角色已更新');rolesVisible.value=false;await load()}finally{saving.value=false}}
function openPassword(row:any){current.value=row;newPassword.value='';passwordVisible.value=true} async function savePassword(){if(!current.value)return;if(newPassword.value.length<6||newPassword.value.length>64){ElMessage.warning('密码长度必须为6-64位');return}saving.value=true;try{await systemUserApi.resetPassword(current.value.id,newPassword.value);ElMessage.success('密码重置成功');passwordVisible.value=false}finally{saving.value=false}}
async function remove(row:any){await systemUserApi.remove(row.id);ElMessage.success('用户已删除');if(rows.value.length===1&&page.pageNum>1)page.pageNum--;await load()}
async function openPermissions(row:any){current.value=row as SystemUser;permissionVisible.value=true;permissionLoading.value=true;permissionProfile.value=undefined;Object.keys(permissionEffects).forEach(k=>delete permissionEffects[k]);try{const profile:any=await systemUserApi.permissions(row.id);permissionProfile.value=profile;activeModules.value=profile.modules.map((m:any)=>m.code);for(const m of profile.modules)for(const p of m.permissions)permissionEffects[p.code]='INHERIT';for(const o of profile.overrides)permissionEffects[o.permissionCode]=o.effect}finally{permissionLoading.value=false}}
async function savePermissions(){if(!current.value)return;const overrides:UserPermissionOverride[]=Object.entries(permissionEffects).filter(([,effect])=>effect!=='INHERIT').map(([permissionCode,effect])=>({permissionCode,effect:effect as 'ALLOW'|'DENY'}));saving.value=true;try{permissionProfile.value=await systemUserApi.updatePermissions(current.value.id,overrides) as any;ElMessage.success('个人权限已保存，用户下一次操作立即生效');permissionVisible.value=false}finally{saving.value=false}}
function isEffective(code:string){const effect=permissionEffects[code];if(effect==='ALLOW')return true;if(effect==='DENY')return false;return permissionProfile.value?.rolePermissionCodes.includes(code)||false}
function actionName(type:string){return ({VIEW:'查看',EDIT:'编辑',MANAGE:'管理',DANGER:'高风险'} as any)[type]||type}
function actionTag(type:string){return type==='DANGER'?'danger':type==='VIEW'?'info':type==='EDIT'?'warning':'success'}
function saveBlob(blob:Blob,name:string){const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=name;document.body.appendChild(anchor);anchor.click();anchor.remove();URL.revokeObjectURL(url)}
async function downloadTemplate(){const blob:any=await systemUserApi.importTemplate();saveBlob(blob,'用户导入模板.xlsx')}
async function exportUsers(){exporting.value=true;try{const blob:any=await systemUserApi.exportUsers({keyword:query.keyword||undefined,status:query.status});saveBlob(blob,`用户清单_${new Date().toISOString().slice(0,10)}.xlsx`)}finally{exporting.value=false}}
function openImport(){importFile.value=null;importFileList.value=[];importResult.value=undefined;importVisible.value=true}
function onImportFileChange(file:any){importFile.value=file.raw as File;importFileList.value=[file];importResult.value=undefined}
function onImportFileRemove(){importFile.value=null;importFileList.value=[];importResult.value=undefined}
async function previewImport(){if(!importFile.value)return;importLoading.value=true;try{importResult.value=await systemUserApi.importUsers(importFile.value,true) as any;if(!importResult.value?.totalRows)ElMessage.warning('文件中没有可导入的数据行')}finally{importLoading.value=false}}
async function confirmImport(){if(!importFile.value)return;importLoading.value=true;try{const result:any=await systemUserApi.importUsers(importFile.value,false);importResult.value=result;ElMessage.success(`导入完成：新建 ${result.createCount}，更新 ${result.updateCount}，跳过 ${result.skipCount}，失败 ${result.failCount}`);await load()}finally{importLoading.value=false}}
async function downloadErrors(){const token=importResult.value?.errorToken;if(!token)return;const blob:any=await systemUserApi.importErrors(token);saveBlob(blob,'用户导入错误明细.xlsx')}
async function downloadCredentials(){const token=importResult.value?.credentialsToken;if(!token)return;const blob:any=await systemUserApi.importCredentials(token);saveBlob(blob,'用户初始密码清单.xlsx');if(importResult.value)importResult.value.credentialsToken=undefined}
onMounted(async()=>{roles.value=await systemRoleApi.list() as any;departments.value=await systemDepartmentApi.list() as any;await load()})
</script>

<style scoped>
.role-list{display:flex;flex-direction:column;align-items:flex-start;gap:12px}
.role-dept{margin-left:6px;font-size:12px;color:#909399}
.mobile-cards{display:none}
.permission-user{display:flex;gap:24px;align-items:center;margin:16px 0 10px}.permission-user span{color:#606266}.permission-row{display:grid;grid-template-columns:minmax(230px,1fr) 70px 128px 80px;gap:12px;align-items:center;padding:8px 4px;border-bottom:1px solid #ebeef5}.permission-name{display:flex;flex-direction:column;gap:3px}.permission-name small{color:#909399}.effective{font-size:12px;color:#909399}.effective.on{color:#67c23a}
.import-toolbar{display:flex;align-items:center;gap:12px;margin:16px 0}
.import-summary{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:10px}
.import-issues{margin-top:12px}.import-issues .issue-line{font-size:12px;color:#606266;line-height:1.8;word-break:break-all}
@media(max-width:700px){
  .desktop-table{display:none}
  .mobile-cards{display:grid;gap:10px}
  .mobile-cards :deep(.el-card__body){padding:14px}
  .card-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px}
  .card-name{display:flex;flex-direction:column;gap:3px;min-width:0}
  .card-name small{color:#909399;font-size:12px;word-break:break-all}
  .card-contact{display:flex;flex-wrap:wrap;gap:4px 12px;margin-top:10px;font-size:13px;color:#303133;word-break:break-all}
  .card-roles{display:flex;flex-wrap:wrap;gap:4px;margin-top:8px}
  .card-time{margin-top:8px;font-size:12px}
  .muted{color:#909399;font-size:12px}
  .op-toggle{display:flex;justify-content:space-between;align-items:center;margin-top:12px;padding-top:10px;border-top:1px solid #ebeef5;font-size:13px;color:#409eff}
  .op-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}
  .op-actions :deep(.el-button){width:100%;margin-left:0}
  .permission-row{grid-template-columns:minmax(150px,1fr) 118px}.permission-row>.el-tag{display:none}.effective{display:none}.permission-user{display:block}.permission-user span{display:block;margin-top:5px}
}
</style>
