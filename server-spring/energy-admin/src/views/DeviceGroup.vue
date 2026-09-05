<template>
  <div class="page">
    <div class="page-header"><div><div class="page-title">设备与分组</div><div class="hint">分组仅保存在本系统，平台同步不会覆盖</div></div><el-button type="primary" @click="openGroup()">新建分组</el-button></div>
    <el-row :gutter="14">
      <el-col :xs="24" :md="6">
        <el-card class="group-card">
          <template #header><b>本地分组</b></template>
          <div class="group-item" :class="{active:filters.groupId===undefined&&!filters.ungrouped}" @click="chooseAll">全部设备</div>
          <div class="group-item" :class="{active:filters.ungrouped}" @click="chooseUngrouped"><span>未分组</span></div>
          <div v-for="g in groups" :key="g.id" class="group-item" :class="{active:filters.groupId===g.id}" @click="chooseGroup(g.id)">
            <span><i :style="{background:g.color}" />{{ g.group_name }}</span><span>{{ g.device_count || 0 }}</span>
            <el-dropdown trigger="click" @command="(c:string)=>groupCommand(c,g)" @click.stop><span>•••</span><template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">编辑</el-dropdown-item><el-dropdown-item command="delete" divided>删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="18">
        <el-card>
          <div class="toolbar">
            <el-input v-model="filters.keyword" placeholder="设备名称、编号、表号或通讯地址" clearable @keyup.enter="loadDevices" />
            <el-select v-model="filters.energyType" placeholder="能源类型" clearable><el-option label="电" value="ELECTRIC"/><el-option label="水" value="WATER"/><el-option label="气" value="GAS"/></el-select>
            <el-button @click="loadDevices">查询</el-button>
            <el-select v-model="assignGroupId" placeholder="批量设置分组"><el-option v-for="g in groups" :key="g.id" :label="g.group_name" :value="g.id"/></el-select>
            <el-button type="primary" :disabled="!selected.length||!assignGroupId" @click="assign">应用到已选 {{ selected.length }} 台</el-button>
          </div>
          <el-table :data="devices" v-loading="loading" border row-key="platform_device_id" @selection-change="selected=$event">
            <el-table-column type="selection" width="44"/><el-table-column prop="device_name" label="设备" min-width="180" show-overflow-tooltip/>
            <el-table-column prop="comm_address" label="通讯地址" width="120"/><el-table-column prop="serial_number" label="表号" width="145"/>
            <el-table-column prop="energy_type_name" label="类型" width="100"/><el-table-column prop="account_numbers" label="平台账户" min-width="150" show-overflow-tooltip/>
            <el-table-column label="本地分组" width="120"><template #default="{row}"><el-tag v-if="row.group_name">{{ row.group_name }}</el-tag><span v-else class="muted">未分组</span></template></el-table-column>
            <el-table-column prop="last_usage_date" label="用量同步至" width="120"/>
          </el-table>
          <div class="count">共 {{ devices.length }} 台设备</div>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="dialog.visible" :title="dialog.id?'编辑分组':'新建分组'" width="420px"><el-form label-width="80px"><el-form-item label="名称"><el-input v-model="dialog.form.groupName"/></el-form-item><el-form-item label="颜色"><el-color-picker v-model="dialog.form.color"/></el-form-item><el-form-item label="排序"><el-input-number v-model="dialog.form.sortOrder" :min="0"/></el-form-item><el-form-item label="备注"><el-input v-model="dialog.form.remark" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="dialog.visible=false">取消</el-button><el-button type="primary" @click="saveGroup">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'; import { ElMessage, ElMessageBox } from 'element-plus'; import { energyApi } from '@/api/energy'
const groups=ref<any[]>([]),devices=ref<any[]>([]),selected=ref<any[]>([]),assignGroupId=ref<number>(),loading=ref(false)
const filters=reactive<any>({keyword:'',energyType:'',groupId:undefined,ungrouped:false}); const dialog=reactive<any>({visible:false,id:null,form:{groupName:'',color:'#409EFF',sortOrder:0,remark:''}})
async function loadGroups(){groups.value=await energyApi.groups() as any}
async function loadDevices(){loading.value=true;try{devices.value=await energyApi.devices(filters) as any}finally{loading.value=false}}
function chooseAll(){filters.groupId=undefined;filters.ungrouped=false;loadDevices()} function chooseUngrouped(){filters.groupId=undefined;filters.ungrouped=true;loadDevices()} function chooseGroup(id:number){filters.groupId=id;filters.ungrouped=false;loadDevices()}
function openGroup(g?:any){dialog.id=g?.id||null;dialog.form={groupName:g?.group_name||'',color:g?.color||'#409EFF',sortOrder:g?.sort_order||0,remark:g?.remark||''};dialog.visible=true}
async function saveGroup(){if(!dialog.form.groupName.trim())return ElMessage.warning('请填写分组名称');dialog.id?await energyApi.updateGroup(dialog.id,dialog.form):await energyApi.createGroup(dialog.form);dialog.visible=false;ElMessage.success('已保存');await loadGroups();await loadDevices()}
async function groupCommand(c:string,g:any){if(c==='edit')return openGroup(g);await ElMessageBox.confirm(`删除分组“${g.group_name}”？分组关系也会删除，用量数据不受影响。`,'确认删除',{type:'warning'});await energyApi.deleteGroup(g.id);await loadGroups();await loadDevices()}
async function assign(){await energyApi.assignDevices(assignGroupId.value!,selected.value.map(v=>v.platform_device_id));ElMessage.success('设备分组已更新');await loadGroups();await loadDevices()}
onMounted(async()=>{await loadGroups();await loadDevices()})
</script>
<style scoped>.page{padding:18px}.page-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-title{font-size:22px;font-weight:700}.hint,.muted,.count{color:#909399;font-size:13px}.group-card{margin-bottom:14px}.group-item{display:flex;align-items:center;justify-content:space-between;padding:10px;border-radius:6px;cursor:pointer}.group-item.active{background:#ecf5ff;color:#409eff}.group-item i{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:7px}.toolbar{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}.toolbar .el-input{width:280px}.toolbar .el-select{width:150px}.count{text-align:right;margin-top:10px}</style>
