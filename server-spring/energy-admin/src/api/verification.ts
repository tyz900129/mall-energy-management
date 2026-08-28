import http from './http'
export const verificationApi={
 byWorkflow:(workflowId:number)=>http.get(`/api/v1/verification/workflow/${workflowId}`),
 open:(token:string)=>http.get(`/api/v1/verification/tasks/${token}`),
 start:(token:string)=>http.post(`/api/v1/verification/tasks/${token}/start`),
 saveDevice:(token:string,id:number,data:Record<string,any>)=>http.put(`/api/v1/verification/tasks/${token}/devices/${id}`,data),
 complete:(token:string,remark:string)=>http.post(`/api/v1/verification/tasks/${token}/complete`,{remark}),
 attachments:(token:string,id:number)=>http.get(`/api/v1/verification/tasks/${token}/devices/${id}/attachments`),
 upload:(token:string,id:number,photoType:string,file:File,onProgress?:(percent:number)=>void)=>{const data=new FormData();data.append('file',file);return http.post(`/api/v1/verification/tasks/${token}/devices/${id}/attachments`,data,{params:{photoType},headers:{'Content-Type':'multipart/form-data'},timeout:120000,onUploadProgress:e=>{if(e.total)onProgress?.(Math.min(99,Math.round(e.loaded*100/e.total)))}})}
}
