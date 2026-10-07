import http from './http'
const base='/api/v1/access-public'
export const accessPublicApi={
  stalls:(keyword:string)=>http.get(`${base}/stalls`,{params:{keyword}}),
  create:(data:any)=>http.post(`${base}/appointments`,data),
  upload:(token:string,photoType:string,file:File,onProgress?:(percent:number)=>void)=>{const form=new FormData();form.append('photoType',photoType);form.append('file',file);return http.post(`${base}/appointments/${token}/attachments`,form,{timeout:180000,onUploadProgress:event=>{if(event.total)onProgress?.(Math.min(99,Math.round(event.loaded*100/event.total)))}})},
  checkFaceQuality:(file:File)=>{const form=new FormData();form.append('file',file);return http.post(`${base}/face-quality-check`,form,{timeout:120000})}
}
