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
 submit:(token:string,data:Record<string,any>)=>http.post(`/api/v1/operations/clearances/stall/${encodeURIComponent(token)}`,data),
 page:(params:Record<string,any>)=>http.get('/api/v1/operations/clearances',{params})
}
export const operationTemplateApi={list:()=>http.get('/api/v1/operations/clearance-templates'),stall:(token:string)=>http.get(`/api/v1/operations/clearance-templates/stall/${encodeURIComponent(token)}`),save:(data:any)=>http.post('/api/v1/operations/clearance-templates',data),enable:(id:number)=>http.put(`/api/v1/operations/clearance-templates/${id}/enable`)}
export const operationDashboardApi={get:(periodDate:string)=>http.get('/api/v1/operations/dashboard',{params:{periodDate}})}
export const operationPropertyApi={records:(periodDate='')=>http.get('/api/v1/operations/property-inspections',{params:{periodDate}}),template:(token:string)=>http.get(`/api/v1/operations/property-inspections/templates/stall/${encodeURIComponent(token)}`),submit:(token:string,answers:any)=>http.post(`/api/v1/operations/property-inspections/stall/${encodeURIComponent(token)}`,{answers}),templates:()=>http.get('/api/v1/operations/property-inspections/templates'),saveTemplate:(data:any)=>http.post('/api/v1/operations/property-inspections/templates',data),enableTemplate:(id:number)=>http.put(`/api/v1/operations/property-inspections/templates/${id}/enable`)}
export const operationAccountabilityApi={list:(params:any)=>http.get('/api/v1/operations/accountability-orders',{params}),detail:(id:number)=>http.get(`/api/v1/operations/accountability-orders/${id}`),create:(inspectionId:number,data:any)=>http.post(`/api/v1/operations/accountability-orders/from-inspection/${inspectionId}`,data),update:(id:number,data:any)=>http.put(`/api/v1/operations/accountability-orders/${id}`,data)}
export const operationFormImageApi={upload:(qrToken:string,formType:string,fieldCode:string,file:File,onProgress?:(percent:number)=>void)=>{const data=new FormData();data.append('file',file);return http.post('/api/v1/operations/form-images',data,{params:{qrToken,formType,fieldCode},headers:{'Content-Type':'multipart/form-data'},timeout:120000,onUploadProgress:(e:any)=>onProgress?.(e.total?Math.round(e.loaded*100/e.total):0)})}}
