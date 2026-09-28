import http from './http'
export const inspectionEquipmentApi={
 categories:()=>http.get('/api/v1/inspection-equipment/categories'),
 saveCategory:(d:any)=>d.id?http.put(`/api/v1/inspection-equipment/categories/${d.id}`,d):http.post('/api/v1/inspection-equipment/categories',d),
 list:(params:any={})=>http.get('/api/v1/inspection-equipment',{params}),
 get:(id:any)=>http.get(`/api/v1/inspection-equipment/${id}`),
 save:(d:any)=>d.id?http.put(`/api/v1/inspection-equipment/${d.id}`,d):http.post('/api/v1/inspection-equipment',d),
 scan:(token:string)=>http.get(`/api/v1/inspection-equipment/scan/${token}`)
}
