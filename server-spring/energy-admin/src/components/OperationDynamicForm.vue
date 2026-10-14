<template>
  <el-form label-position="top">
    <el-form-item v-for="field in fields" :key="field.code" :label="field.label" :required="field.required">
      <el-switch v-if="field.type==='BOOLEAN'" :model-value="modelValue[field.code]" active-text="正常" inactive-text="异常" @update:model-value="set(field.code,$event)"/>
      <el-input-number v-else-if="field.type==='NUMBER'" :model-value="modelValue[field.code]" @update:model-value="set(field.code,$event)"/>
      <div v-else-if="field.type==='IMAGE'" class="photo-field">
        <input :id="`photo-${formType}-${field.code}`" class="file-input" type="file" accept="image/*" capture="environment" @change="upload(field,$event)"/>
        <label class="pick-button" :class="{disabled:uploading[field.code]}" :for="`photo-${formType}-${field.code}`">{{uploading[field.code]?'压缩上传中…':modelValue[field.code]?'重新拍照':'拍照或选择图片'}}</label>
        <span v-if="progress[field.code]" class="progress">{{progress[field.code]}}%</span>
        <img v-if="previews[field.code]" class="preview" :src="previews[field.code]" alt="已上传图片"/>
        <div v-if="modelValue[field.code]" class="uploaded">已压缩上传（{{sizeText(modelValue[field.code].fileSize)}}）</div>
      </div>
      <el-input v-else :model-value="modelValue[field.code]" :type="field.code==='exceptionDescription'?'textarea':'text'" @update:model-value="set(field.code,$event)"/>
    </el-form-item>
  </el-form>
</template>
<script setup lang="ts">
import {reactive} from 'vue'
import {ElMessage} from 'element-plus'
import {operationFormImageApi} from '@/api/operation'
import {compressImage} from '@/utils/image'
const props=defineProps<{fields:any[],modelValue:Record<string,any>,qrToken:string,formType:'CLEARANCE'|'PROPERTY'}>()
const emit=defineEmits<{(e:'update:modelValue',value:Record<string,any>):void}>()
const uploading=reactive<Record<string,boolean>>({}),progress=reactive<Record<string,number>>({}),previews=reactive<Record<string,string>>({})
function set(code:string,value:any){emit('update:modelValue',{...props.modelValue,[code]:value})}
function sizeText(value:number){return value?`${Math.max(1,Math.round(value/1024))} KB`:'-'}
async function upload(field:any,event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;if(!file.type.startsWith('image/'))return ElMessage.warning('请选择图片文件');uploading[field.code]=true;progress[field.code]=5;try{const compressed=await compressImage(file);progress[field.code]=35;const result:any=await operationFormImageApi.upload(props.qrToken,props.formType,field.code,compressed,(p:number)=>progress[field.code]=35+Math.round(p*.65));if(previews[field.code])URL.revokeObjectURL(previews[field.code]);previews[field.code]=URL.createObjectURL(compressed);set(field.code,result);progress[field.code]=100;ElMessage.success('图片已压缩并上传')}catch(e:any){ElMessage.error(e?.message||'图片上传失败')}finally{uploading[field.code]=false;setTimeout(()=>progress[field.code]=0,800)}}
</script>
<style scoped>.photo-field{width:100%}.file-input{position:absolute;width:1px;height:1px;opacity:0}.pick-button{display:inline-flex;align-items:center;justify-content:center;padding:9px 15px;color:#fff;background:#409eff;border-radius:4px;cursor:pointer}.pick-button.disabled{pointer-events:none;opacity:.65}.progress{margin-left:10px;color:#409eff}.preview{display:block;width:150px;height:110px;object-fit:cover;margin-top:10px;border-radius:6px;border:1px solid #dcdfe6}.uploaded{margin-top:6px;color:#67c23a;font-size:13px}</style>
