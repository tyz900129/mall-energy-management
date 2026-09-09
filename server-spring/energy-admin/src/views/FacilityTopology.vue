<template>
  <div class="page facility-page" :class="`theme-${systemType.toLowerCase()}`">
    <div class="page-header">
      <div><div class="page-title">{{ config.title }}</div><div class="subtitle">{{ config.subtitle }}</div></div>
      <el-button v-if="canEdit" type="primary" @click="openCreate()">新增根节点</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="层级维护" name="manage">
        <div class="manage-grid">
          <div class="tree-card">
            <el-alert :title="config.guide" type="info" :closable="false" show-icon class="guide"/>
            <el-empty v-if="!tree.length" :description="`暂无${config.shortName}节点`"/>
            <el-tree v-else :data="tree" node-key="id" default-expand-all :expand-on-click-node="false" @node-click="selectNode">
              <template #default="{data}">
                <div class="tree-row">
                  <span><el-tag size="small" :type="nodeTag(data)">{{data.nodeTypeName}}</el-tag> {{data.nodeCode}} · {{data.nodeName}}</span>
                  <span v-if="canEdit" class="actions">
                    <el-button link type="primary" @click.stop="openCreate(data)">添加连接节点</el-button>
                    <el-button link @click.stop="openEdit(data)">编辑</el-button>
                    <el-popconfirm title="只能删除没有连接下级的节点，确认删除？" @confirm="remove(data)"><template #reference><el-button link type="danger" @click.stop>删除</el-button></template></el-popconfirm>
                  </span>
                </div>
              </template>
            </el-tree>
          </div>
          <div class="detail-card">
            <template v-if="selected">
              <div class="detail-title"><h3>{{selected.nodeCode}} · {{selected.nodeName}}</h3><el-button type="primary" plain @click="showQr(selected)">节点二维码</el-button></div>
              <node-detail :node="selected" :config="config" :children="childrenOf(selected)" @select="selectNode"/>
            </template>
            <el-empty v-else description="点击节点查看详细信息"/>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="拓扑图" name="graph">
        <div class="graph-toolbar"><span>节点 {{flat.length}} 个；箭头表示{{config.flowText}}；点击节点查看详情</span><el-button @click="fitGraph">回到起点</el-button></div>
        <div ref="graphScroller" class="graph-scroll">
          <svg :width="graphWidth" :height="graphHeight" class="topology-svg">
            <defs><marker id="flow-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" class="arrow-head"/></marker></defs>
            <g v-for="edge in edges" :key="edge.key"><path :d="edge.path" class="edge" :marker-end="config.reverseFlow?undefined:'url(#flow-arrow)'" :marker-start="config.reverseFlow?'url(#flow-arrow)':undefined"/></g>
            <g v-for="node in positioned" :key="node.id" class="node" :transform="`translate(${node.x},${node.y})`" @click="showNodeDetail(node)">
              <rect width="210" height="88" rx="10" :class="[statusClass(node),{'selected-node':selected?.id===node.id}]"/>
              <text x="12" y="22" class="node-type">{{node.nodeTypeName}}</text>
              <text x="12" y="48" class="node-title">{{short(node.nodeCode+' · '+node.nodeName,24)}}</text>
              <text x="12" y="70" class="node-sub">{{short(nodeSummary(node),29)}}</text>
            </g>
          </svg>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" :title="`${config.shortName}节点详情`" width="700px">
      <template v-if="selected">
        <div class="dialog-node-title"><div><el-tag :type="nodeTag(selected)">{{selected.nodeTypeName}}</el-tag><b>{{selected.nodeCode}} · {{selected.nodeName}}</b></div><el-tag :type="operatingTag(selected.operatingStatus)">{{operatingLabel(selected.operatingStatus)}}</el-tag></div>
        <node-detail :node="selected" :config="config" :children="childrenOf(selected)" @select="showNodeDetail"/>
      </template>
      <template #footer><el-button @click="detailVisible=false">关闭</el-button><el-button v-if="canEdit&&selected" @click="detailVisible=false;openEdit(selected)">编辑节点</el-button><el-button v-if="selected" type="primary" plain @click="showQr(selected)">节点二维码</el-button></template>
    </el-dialog>

    <el-dialog v-model="visible" :title="form.id?`编辑${config.shortName}节点`:`新增${config.shortName}节点`" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="125px">
        <el-form-item :label="config.parentLabel"><el-tree-select v-model="form.parentId" :data="parentOptions" node-key="id" :props="{label:'displayName',children:'children'}" clearable check-strictly style="width:100%" :placeholder="config.parentPlaceholder"/></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="节点编码" prop="nodeCode"><el-input v-model="form.nodeCode" maxlength="64"/></el-form-item></el-col><el-col :span="12"><el-form-item label="节点名称" prop="nodeName"><el-input v-model="form.nodeName" maxlength="120"/></el-form-item></el-col></el-row>
        <el-form-item label="节点类型" prop="nodeType"><el-select v-model="form.nodeType" filterable style="width:100%"><el-option v-for="item in nodeTypes" :key="item.id" :label="item.itemLabel" :value="item.itemValue"/></el-select></el-form-item>
        <el-form-item :label="config.lineSpecLabel"><el-select v-model="form.lineSpecCode" clearable filterable style="width:100%" :placeholder="`选择${config.lineSpecLabel}，可在基础字典中维护`"><el-option v-for="item in lineSpecs" :key="item.id" :label="item.itemLabel" :value="item.itemValue"/></el-select></el-form-item>

        <template v-if="systemType==='WATER_SUPPLY'">
          <el-form-item label="设备规格"><el-input v-model="form.equipmentSpecCode" placeholder="水泵、水箱等设备可填写型号规格"/></el-form-item>
          <el-row :gutter="12"><el-col :span="12"><el-form-item label="设计流量"><el-input-number v-model="form.designFlow" :min="0" :precision="2"/><span class="unit">m³/h</span></el-form-item></el-col><el-col :span="12"><el-form-item label="设计压力"><el-input-number v-model="form.designPressure" :min="0" :precision="3"/><span class="unit">MPa</span></el-form-item></el-col></el-row>
          <el-form-item label="阀门/控制装置"><el-input v-model="form.controlComponent" placeholder="可填写阀门、减压阀、止回阀等"/></el-form-item>
        </template>
        <template v-else-if="systemType==='DRAINAGE'">
          <el-form-item label="排水性质"><el-select v-model="form.mediumType" clearable style="width:100%"><el-option v-for="item in mediums" :key="item.id" :label="item.itemLabel" :value="item.itemValue"/></el-select></el-form-item>
          <el-form-item label="设备规格"><el-input v-model="form.equipmentSpecCode" placeholder="隔油池、提升泵等设备可填写型号规格"/></el-form-item>
          <el-row :gutter="12"><el-col :span="12"><el-form-item label="管道坡度"><el-input-number v-model="form.slopePercent" :min="0" :precision="3"/><span class="unit">%</span></el-form-item></el-col><el-col :span="12"><el-form-item label="安装标高"><el-input-number v-model="form.elevationM" :precision="3"/><span class="unit">m</span></el-form-item></el-col></el-row>
          <el-form-item label="控制/止回装置"><el-input v-model="form.controlComponent"/></el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="config.equipmentLabel"><el-select v-model="form.equipmentSpecCode" clearable filterable style="width:100%"><el-option v-for="item in equipmentSpecs" :key="item.id" :label="item.itemLabel" :value="item.itemValue"/></el-select></el-form-item>
          <el-row :gutter="12"><el-col :span="12"><el-form-item label="设计风量"><el-input-number v-model="form.designFlow" :min="0" :precision="2"/><span class="unit">m³/h</span></el-form-item></el-col><el-col :span="12"><el-form-item label="设计风压"><el-input-number v-model="form.designPressure" :min="0" :precision="2"/><span class="unit">Pa</span></el-form-item></el-col></el-row>
          <el-form-item v-if="systemType==='FRESH_AIR'" label="送风口数量"><el-input-number v-model="form.outletQuantity" :min="0" :precision="0"/><span class="unit">个</span></el-form-item>
          <el-form-item label="风阀/控制装置"><el-input v-model="form.controlComponent" placeholder="可填写防火阀、调节阀等"/></el-form-item>
        </template>

        <el-form-item label="安装位置"><el-input v-model="form.location" maxlength="200"/></el-form-item>
        <el-form-item label="关联铺位"><el-select v-model="form.stallId" clearable filterable style="width:100%"><el-option v-for="stall in stalls" :key="stall.id" :label="`${stall.stallCode} · ${stall.stallName||'未命名'}`" :value="stall.id"/></el-select></el-form-item>
        <el-form-item label="关联设备ID"><el-input v-model="form.platformDeviceId" placeholder="水表等平台设备可填写设备ID"/></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="运行状态"><el-select v-model="form.operatingStatus"><el-option v-for="item in operatingStatuses" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="资料状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用"/></el-form-item></el-col></el-row>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="qrVisible" title="节点二维码" width="460px"><div v-if="selected" class="qr-panel"><b>{{selected.nodeCode}} · {{selected.nodeName}}</b><img v-if="qrUrl" :src="qrUrl" alt="节点二维码"/><div class="qr-tip">扫码登录后查看当前节点、连接关系、专业参数和关联铺位</div></div><template #footer><el-button @click="qrVisible=false">关闭</el-button><el-button type="primary" :disabled="!qrUrl" @click="downloadQr">下载二维码</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, reactive, ref, watch, type PropType } from 'vue'
import { useRoute } from 'vue-router'
import { ElDescriptions, ElDescriptionsItem, ElMessage, ElTag, type FormInstance } from 'element-plus'
import { facilityTopologyApi, type FacilityNode, type FacilitySystemType } from '@/api/facility'
import { dictionaryApi, type DictionaryItem, type DictionaryType } from '@/api/system'
import { stallApi } from '@/api/merchant'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ systemType: FacilitySystemType }>()
const NodeDetail = defineComponent({
  name: 'NodeDetail',
  props: { node: { type: Object as PropType<FacilityNode>, required: true }, config: { type: Object as PropType<any>, required: true }, children: { type: Array as PropType<FacilityNode[]>, default: () => [] } },
  emits: ['select'],
  setup(detailProps, { emit }) {
    const row = (label: string, content: any) => h(ElDescriptionsItem, { label }, () => content ?? '-')
    return () => h(ElDescriptions, { column: 1, border: true, class: 'node-detail-table' }, () => [
      row(detailProps.config.parentLabel, detailProps.node.parentName || detailProps.config.rootLabel),
      row('管道/风管规格', detailProps.node.lineSpecName || '-'),
      ...(detailProps.node.equipmentSpecName || detailProps.node.equipmentSpecCode ? [row(detailProps.config.equipmentLabel || '设备规格', detailProps.node.equipmentSpecName || detailProps.node.equipmentSpecCode)] : []),
      ...(detailProps.node.mediumTypeName ? [row('排水性质', detailProps.node.mediumTypeName)] : []),
      ...(detailProps.node.designFlow != null ? [row(detailProps.config.flowValueLabel, `${detailProps.node.designFlow} ${detailProps.config.flowUnit}`)] : []),
      ...(detailProps.node.designPressure != null ? [row(detailProps.config.pressureLabel, `${detailProps.node.designPressure} ${detailProps.config.pressureUnit}`)] : []),
      ...(detailProps.node.slopePercent != null ? [row('管道坡度', `${detailProps.node.slopePercent}%`)] : []),
      ...(detailProps.node.elevationM != null ? [row('安装标高', `${detailProps.node.elevationM} m`)] : []),
      ...(detailProps.node.outletQuantity != null ? [row('送风口数量', `${detailProps.node.outletQuantity} 个`)] : []),
      ...(detailProps.node.controlComponent ? [row('控制装置', detailProps.node.controlComponent)] : []),
      row('连接节点', detailProps.children.length ? detailProps.children.map(node => h(ElTag, { class: 'child-tag', onClick: () => emit('select', node) }, () => `${node.nodeCode} · ${node.nodeName}`)) : '无'),
      row('安装位置', detailProps.node.location || '-'),
      row('关联铺位', detailProps.node.stallCode ? `${detailProps.node.stallCode} · ${detailProps.node.stallName || ''}` : '-'),
      row('关联设备ID', detailProps.node.platformDeviceId || '-'),
      row('备注', detailProps.node.remark || '-')
    ])
  }
})
const route = useRoute(), userStore = useUserStore()
const systemType = computed(() => props.systemType)
const configs: Record<FacilitySystemType, any> = {
  WATER_SUPPLY: {title:'供水拓扑管理',shortName:'供水',subtitle:'维护水源、水箱、水泵、管网、水表和铺位用水点的供水关系',guide:'供水方向：水源 → 主管/立管 → 水表 → 铺位用水点。',parentLabel:'上游供水节点',parentPlaceholder:'留空表示供水源头',rootLabel:'供水源头',nodeDict:'WATER_SUPPLY_NODE_TYPE',lineDict:'WATER_SUPPLY_PIPE_SPEC',lineSpecLabel:'供水管道规格',flowText:'水流方向',flowValueLabel:'设计流量',flowUnit:'m³/h',pressureLabel:'设计压力',pressureUnit:'MPa',reverseFlow:false},
  DRAINAGE: {title:'排水拓扑管理',shortName:'排水',subtitle:'维护铺位排水点、管网、隔油池、集水坑和排放口的排水关系',guide:'排水树以排放口为根节点；添加连接节点时逐级向铺位排水点展开，箭头指向最终排放口。',parentLabel:'下游排放节点',parentPlaceholder:'留空表示最终排放口',rootLabel:'最终排放口',nodeDict:'DRAINAGE_NODE_TYPE',lineDict:'DRAINAGE_PIPE_CALIBER',lineSpecLabel:'排水管道规格',flowText:'排水方向',flowValueLabel:'设计流量',flowUnit:'m³/h',pressureLabel:'设计压力',pressureUnit:'MPa',reverseFlow:true},
  OIL_EXHAUST: {title:'排油烟拓扑管理',shortName:'排油烟',subtitle:'维护铺位集烟罩、风管、净化设备、排油烟风机和排放口关系',guide:'排油烟树以排放口为根节点；箭头从铺位集烟罩指向净化设备、风机和排放口。',parentLabel:'下游排烟节点',parentPlaceholder:'留空表示最终排放口',rootLabel:'最终排放口',nodeDict:'OIL_EXHAUST_NODE_TYPE',lineDict:'AIR_DUCT_SPEC',equipmentDict:'EXHAUST_FAN',lineSpecLabel:'排油烟风管规格',equipmentLabel:'排油烟风机',flowText:'排油烟方向',flowValueLabel:'设计风量',flowUnit:'m³/h',pressureLabel:'设计风压',pressureUnit:'Pa',reverseFlow:true},
  FRESH_AIR: {title:'补风机拓扑管理',shortName:'补风',subtitle:'维护取风口、补风机、风管、风阀和铺位送风口关系',guide:'本系统中的“新风”和“补风”表达同一业务含义，统一沿用已有补风机资料。',parentLabel:'上游补风节点',parentPlaceholder:'留空表示室外取风口',rootLabel:'室外取风口',nodeDict:'FRESH_AIR_NODE_TYPE',lineDict:'AIR_DUCT_SPEC',equipmentDict:'FRESH_AIR_UNIT',lineSpecLabel:'补风风管规格',equipmentLabel:'补风机',flowText:'补风方向',flowValueLabel:'设计风量',flowUnit:'m³/h',pressureLabel:'设计风压',pressureUnit:'Pa',reverseFlow:false}
}
const config = computed(() => configs[systemType.value])
const tree = ref<FacilityNode[]>([]), nodeTypes = ref<DictionaryItem[]>([]), lineSpecs = ref<DictionaryItem[]>([]), equipmentSpecs = ref<DictionaryItem[]>([]), mediums = ref<DictionaryItem[]>([]), stalls = ref<any[]>([])
const tab = ref('manage'), visible = ref(false), detailVisible = ref(false), qrVisible = ref(false), qrUrl = ref(''), saving = ref(false), selected = ref<FacilityNode>(), graphScroller = ref<HTMLElement>(), formRef = ref<FormInstance>()
const form = reactive<any>({})
const canEdit = computed(() => userStore.user?.roles?.some(role => ['SUPER_ADMIN','SYSTEM_ADMIN'].includes(role)))
const operatingStatuses = [{value:'NORMAL',label:'正常'},{value:'STOPPED',label:'停用'},{value:'FAULT',label:'故障'},{value:'MAINTENANCE',label:'检修'}]
const rules = {nodeCode:[{required:true,message:'请输入节点编码'}],nodeName:[{required:true,message:'请输入节点名称'}],nodeType:[{required:true,message:'请选择节点类型'}]}

const flat = computed(() => { const output:any[]=[]; const walk=(rows:FacilityNode[],depth=0)=>rows.forEach(node=>{output.push({...node,depth});walk(node.children||[],depth+1)}); walk(tree.value); return output })
const parentOptions = computed(() => { const copy=(rows:FacilityNode[]):any[]=>rows.filter(node=>node.id!==form.id).map(node=>({id:node.id,displayName:`${node.nodeCode} · ${node.nodeName}`,children:copy(node.children||[])})); return copy(tree.value) })
const positioned = computed(() => { let leaf=0;const output:any[]=[];const walk=(node:FacilityNode,depth:number):number=>{const childXs=(node.children||[]).map(child=>walk(child,depth+1));const x=childXs.length?childXs.reduce((a,b)=>a+b,0)/childXs.length:55+leaf++*250;output.push({...node,x,y:35+depth*150});return x};tree.value.forEach(node=>walk(node,0));return output })
const positionMap = computed(() => new Map(positioned.value.map(node=>[node.id,node])))
const edges = computed(() => positioned.value.filter(node=>node.parentId&&positionMap.value.has(node.parentId)).map(node=>edgeOf(node.parentId,node)))
const graphWidth = computed(() => Math.max(920,...positioned.value.map(node=>node.x+250)))
const graphHeight = computed(() => Math.max(430,...positioned.value.map(node=>node.y+135)))

function reset(){Object.keys(form).forEach(key=>delete form[key]);Object.assign(form,{id:0,parentId:null,nodeCode:'',nodeName:'',nodeType:'',lineSpecCode:'',equipmentSpecCode:'',mediumType:'',designFlow:null,designPressure:null,slopePercent:null,elevationM:null,outletQuantity:null,controlComponent:'',location:'',stallId:null,platformDeviceId:'',operatingStatus:'NORMAL',sortOrder:0,status:1,remark:''})}
async function reload(){selected.value=undefined;detailVisible.value=false;await Promise.all([load(),loadOptions()])}
async function load(){tree.value=await facilityTopologyApi.tree(systemType.value) as any;const focus=Number(route.query.focus);if(focus){selected.value=flat.value.find(node=>node.id===focus);detailVisible.value=!!selected.value}}
async function loadOptions(){const requests:any[]=[dictionaryApi.list(config.value.nodeDict as DictionaryType),dictionaryApi.list(config.value.lineDict as DictionaryType),stallApi.page({pageNum:1,pageSize:1000})];if(config.value.equipmentDict)requests.push(dictionaryApi.list(config.value.equipmentDict as DictionaryType));if(systemType.value==='DRAINAGE')requests.push(dictionaryApi.list('DRAINAGE_MEDIUM' as DictionaryType));const result:any[]=await Promise.all(requests);nodeTypes.value=result[0];lineSpecs.value=result[1];stalls.value=result[2].records||[];let index=3;equipmentSpecs.value=config.value.equipmentDict?result[index++]:[];mediums.value=systemType.value==='DRAINAGE'?result[index]||[]:[]}
function openCreate(parent?:FacilityNode){reset();form.parentId=parent?.id||null;visible.value=true}
function openEdit(node:FacilityNode){reset();Object.assign(form,node);delete form.children;visible.value=true;selected.value=node}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;saving.value=true;try{const data={...form,parentId:form.parentId||null,lineSpecCode:form.lineSpecCode||null,equipmentSpecCode:form.equipmentSpecCode||null,mediumType:form.mediumType||null,stallId:form.stallId||null,platformDeviceId:form.platformDeviceId?.trim()||null};delete data.id;delete data.parentName;delete data.nodeTypeName;delete data.lineSpecName;delete data.equipmentSpecName;delete data.mediumTypeName;delete data.stallCode;delete data.stallName;if(form.id)await facilityTopologyApi.update(systemType.value,form.id,data);else await facilityTopologyApi.create(systemType.value,data);visible.value=false;ElMessage.success(`${config.value.shortName}节点已保存，拓扑图已更新`);await load();selected.value=flat.value.find(node=>node.id===form.id)}finally{saving.value=false}}
async function remove(node:FacilityNode){await facilityTopologyApi.remove(systemType.value,node.id);if(selected.value?.id===node.id)selected.value=undefined;ElMessage.success('节点已删除');await load()}
function selectNode(node:any){selected.value=flat.value.find(item=>item.id===node.id)||node}
function showNodeDetail(node:any){selectNode(node);detailVisible.value=true}
function childrenOf(node:FacilityNode){return flat.value.filter(item=>item.parentId===node.id)}
function edgeOf(parentId:number,node:any){const parent:any=positionMap.value.get(parentId);return{key:`${parent.id}-${node.id}`,path:`M ${parent.x+105} ${parent.y+88} V ${parent.y+118} H ${node.x+105} V ${node.y}`}}
function fitGraph(){if(graphScroller.value){graphScroller.value.scrollLeft=0;graphScroller.value.scrollTop=0}}
function short(value:string,length:number){return value.length>length?value.slice(0,length)+'…':value}
function nodeSummary(node:any){return [node.lineSpecName,node.equipmentSpecName||node.equipmentSpecCode,node.stallCode].filter(Boolean).join(' · ')||operatingLabel(node.operatingStatus)}
function operatingLabel(value:string){return operatingStatuses.find(item=>item.value===value)?.label||value||'正常'}
function operatingTag(value:string){return value==='NORMAL'?'success':value==='FAULT'?'danger':value==='MAINTENANCE'?'warning':'info'}
function statusClass(node:FacilityNode){return node.status!==1?'disabled':node.operatingStatus==='FAULT'?'fault':node.operatingStatus==='MAINTENANCE'?'maintenance':node.operatingStatus==='STOPPED'?'stopped':''}
function nodeTag(node:FacilityNode){return node.operatingStatus==='FAULT'?'danger':node.operatingStatus==='MAINTENANCE'?'warning':node.status===1?'primary':'info'}
async function showQr(node:FacilityNode){selectNode(node);const blob:any=await facilityTopologyApi.qr(systemType.value,node.id);if(qrUrl.value)URL.revokeObjectURL(qrUrl.value);qrUrl.value=URL.createObjectURL(blob);qrVisible.value=true}
function downloadQr(){if(!qrUrl.value||!selected.value)return;const anchor=document.createElement('a');anchor.href=qrUrl.value;anchor.download=`${selected.value.nodeCode}-${config.value.shortName}节点二维码.png`;anchor.click()}

watch([systemType,()=>route.query.focus],reload,{immediate:true})
onBeforeUnmount(()=>{if(qrUrl.value)URL.revokeObjectURL(qrUrl.value)})
</script>

<style scoped>
.subtitle{font-size:13px;color:#909399;margin-top:4px}.manage-grid{display:grid;grid-template-columns:minmax(560px,2fr) minmax(320px,1fr);gap:16px}.tree-card,.detail-card,.graph-scroll{background:#fff;border-radius:8px;padding:16px;min-height:430px}.guide{margin-bottom:14px}.tree-row,.detail-title,.dialog-node-title{width:100%;display:flex;justify-content:space-between;align-items:center;gap:10px}.detail-title h3{margin:0 0 14px}.actions{white-space:nowrap}.graph-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;color:#606266}.graph-scroll{overflow:auto;padding:0}.topology-svg{background-image:radial-gradient(#d9e1ec 1px,transparent 1px);background-size:18px 18px}.edge{fill:none;stroke:#409eff;stroke-width:2}.arrow-head{fill:#409eff}.node{cursor:pointer}.node rect{fill:#fff;stroke:#409eff;stroke-width:2;filter:drop-shadow(0 3px 4px #0002)}.node rect.selected-node{stroke:#f56c6c;stroke-width:3}.node rect.disabled,.node rect.stopped{fill:#f2f3f5;stroke:#909399}.node rect.fault{fill:#fef0f0;stroke:#f56c6c}.node rect.maintenance{fill:#fdf6ec;stroke:#e6a23c}.node-type{font-size:12px;fill:#409eff}.node-title{font-size:14px;font-weight:600;fill:#303133}.node-sub{font-size:11px;fill:#606266}.dialog-node-title{margin-bottom:16px}.dialog-node-title>div{display:flex;align-items:center;gap:9px}.dialog-node-title b{font-size:17px}.child-tag{margin:2px 6px 2px 0;cursor:pointer}.qr-panel{display:flex;flex-direction:column;align-items:center;gap:10px;text-align:center}.qr-panel img{width:min(100%,360px);height:auto}.qr-tip{font-size:13px;color:#909399}.unit{margin-left:7px;color:#606266}.theme-drainage .edge,.theme-drainage .node rect{stroke:#17a2a4}.theme-drainage .arrow-head{fill:#17a2a4}.theme-oil_exhaust .edge,.theme-oil_exhaust .node rect{stroke:#e67e22}.theme-oil_exhaust .arrow-head{fill:#e67e22}.theme-fresh_air .edge,.theme-fresh_air .node rect{stroke:#38a169}.theme-fresh_air .arrow-head{fill:#38a169}
@media(max-width:768px){.manage-grid{grid-template-columns:1fr}.tree-card{padding:8px;overflow:auto}.detail-card{min-height:260px}.tree-row{min-width:600px}.facility-page :deep(.el-dialog){width:94%!important}.facility-page :deep(.el-col){max-width:100%;flex:0 0 100%}.dialog-node-title{align-items:flex-start}.dialog-node-title>div{align-items:flex-start;flex-direction:column}.graph-toolbar{align-items:flex-start;gap:8px}.node-detail-table :deep(.el-descriptions__label){width:94px}}
</style>
