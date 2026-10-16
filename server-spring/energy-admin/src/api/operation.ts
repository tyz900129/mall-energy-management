import http from './http'
export const operationMerchantApi={
 page:(params:Record<string,any>)=>http.get('/api/v1/operations/merchant-responsibilities',{params}),
 managers:(keyword='')=>http.get('/api/v1/operations/merchant-responsibilities/managers',{params:{keyword}}),
 history:(stallId:number)=>http.get(`/api/v1/operations/merchant-responsibilities/${stallId}/history`),
 assign:(data:Record<string,any>)=>http.post('/api/v1/operations/merchant-responsibilities',data),
 qr:(stallId:number)=>http.get(`/api/v1/operations/merchant-responsibilities/${stallId}/qr`,{responseType:'blob'}),
 detail:(token:string)=>http.get(`/api/v1/operations/merchant-responsibilities/by-token/${encodeURIComponent(token)}`),
 publicStall:(token:string)=>http.get(`/api/v1/operation-public/stalls/${encodeURIComponent(token)}`)
}
export const operationClearanceApi={
 period:()=>http.get('/api/v1/operations/clearances/period'),
 schedule:()=>http.get('/api/v1/operations/clearances/schedule'),
 settings:()=>http.get('/api/v1/operations/clearances/settings'),
 updateSettings:(data:{startTime:string;endTime:string})=>http.put('/api/v1/operations/clearances/settings',data),
 submit:(token:string,data:Record<string,any>)=>http.post(`/api/v1/operations/clearances/stall/${encodeURIComponent(token)}`,data),
 today:(token:string)=>http.get(`/api/v1/operations/clearances/stall/${encodeURIComponent(token)}/today`),
 page:(params:Record<string,any>)=>http.get('/api/v1/operations/clearances',{params})
}
export const operationTemplateApi={list:()=>http.get('/api/v1/operations/clearance-templates'),stall:(token:string)=>http.get(`/api/v1/operations/clearance-templates/stall/${encodeURIComponent(token)}`),save:(data:any)=>http.post('/api/v1/operations/clearance-templates',data),update:(id:number,data:any)=>http.put(`/api/v1/operations/clearance-templates/${id}`,data),remove:(id:number)=>http.delete(`/api/v1/operations/clearance-templates/${id}`)}
export const operationDashboardApi={get:(periodDate:string)=>http.get('/api/v1/operations/dashboard',{params:{periodDate}})}
export const operationPropertyApi={records:(periodDate='')=>http.get('/api/v1/operations/property-inspections',{params:{periodDate}}),form:(token:string)=>http.get(`/api/v1/operations/property-inspections/form/stall/${encodeURIComponent(token)}`),submit:(token:string,data:any)=>http.post(`/api/v1/operations/property-inspections/stall/${encodeURIComponent(token)}`,data),templates:()=>http.get('/api/v1/operations/property-inspections/templates'),saveTemplate:(data:any)=>http.post('/api/v1/operations/property-inspections/templates',data),updateTemplate:(id:number,data:any)=>http.put(`/api/v1/operations/property-inspections/templates/${id}`,data),removeTemplate:(id:number)=>http.delete(`/api/v1/operations/property-inspections/templates/${id}`)}
export const operationViolationApi={list:(includeDisabled=false)=>http.get('/api/v1/operations/violation-items',{params:{includeDisabled}}),save:(data:any)=>http.post('/api/v1/operations/violation-items',data),update:(id:number,data:any)=>http.put(`/api/v1/operations/violation-items/${id}`,data),remove:(id:number)=>http.delete(`/api/v1/operations/violation-items/${id}`)}
export const operationAccountabilityApi={list:(params:any)=>http.get('/api/v1/operations/accountability-orders',{params}),detail:(id:number)=>http.get(`/api/v1/operations/accountability-orders/${id}`),create:(inspectionId:number,data:any)=>http.post(`/api/v1/operations/accountability-orders/from-inspection/${inspectionId}`,data),update:(id:number,data:any)=>http.put(`/api/v1/operations/accountability-orders/${id}`,data)}
export const operationFormImageApi={upload:(qrToken:string,formType:string,fieldCode:string,file:File,onProgress?:(percent:number)=>void)=>{const data=new FormData();data.append('file',file);return http.post('/api/v1/operations/form-images',data,{params:{qrToken,formType,fieldCode},headers:{'Content-Type':'multipart/form-data'},timeout:120000,onUploadProgress:(e:any)=>onProgress?.(e.total?Math.round(e.loaded*100/e.total):0)})},image:(id:string)=>http.get(`/api/v1/operations/form-images/${id}`,{responseType:'blob'})}
