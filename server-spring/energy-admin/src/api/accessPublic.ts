import http from './http'
const base='/api/v1/access-public'
export const accessPublicApi={
  stalls:(keyword:string)=>http.get(`${base}/stalls`,{params:{keyword}}),
  create:(data:any)=>http.post(`${base}/appointments`,data),
  upload:(token:string,photoType:string,file:File)=>{const form=new FormData();form.append('photoType',photoType);form.append('file',file);return http.post(`${base}/appointments/${token}/attachments`,form)},
  checkFaceQuality:(file:File)=>{const form=new FormData();form.append('file',file);return http.post(`${base}/face-quality-check`,form,{timeout:120000})}
}
