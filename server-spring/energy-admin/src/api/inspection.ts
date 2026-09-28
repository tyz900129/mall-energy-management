import request from './http'
export const inspectionApi={
  types:()=>request.get('/api/v1/inspections/types'), saveType:(d:any)=>d.id?request.put(`/api/v1/inspections/types/${d.id}`,d):request.post('/api/v1/inspections/types',d),
  templates:()=>request.get('/api/v1/inspections/templates'), saveTemplate:(d:any)=>d.id?request.put(`/api/v1/inspections/templates/${d.id}`,d):request.post('/api/v1/inspections/templates',d),
  plans:()=>request.get('/api/v1/inspections/plans'), savePlan:(d:any)=>d.id?request.put(`/api/v1/inspections/plans/${d.id}`,d):request.post('/api/v1/inspections/plans',d),
  targets:(keyword='')=>request.get('/api/v1/inspections/targets/devices',{params:{keyword}}), generate:()=>request.post('/api/v1/inspections/tasks/generate'),
  tasks:(params:any)=>request.get('/api/v1/inspections/tasks',{params}), task:(id:any)=>request.get(`/api/v1/inspections/tasks/${id}`), submit:(id:any,d:any)=>request.post(`/api/v1/inspections/tasks/${id}/submit`,d),
  taskByToken:(token:string)=>request.get(`/api/v1/inspections/tasks/token/${token}`), attachments:(id:any)=>request.get(`/api/v1/inspections/tasks/${id}/attachments`),
  issues:(params:any={})=>request.get('/api/v1/inspections/issues',{params}), rectify:(id:any,note:string)=>request.post(`/api/v1/inspections/issues/${id}/rectify`,{note}), review:(id:any,passed:boolean)=>request.post(`/api/v1/inspections/issues/${id}/review`,{passed}),
  dashboard:()=>request.get('/api/v1/inspections/dashboard')
}
