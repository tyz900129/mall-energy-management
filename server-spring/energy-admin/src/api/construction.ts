import http from './http'

export type ConstructionWorkflowType =
  | 'MERCHANT_CONSTRUCTION_ENTRY'
  | 'MERCHANT_CONSTRUCTION_EXIT'

export interface ConstructionWorkflow {
  id: number
  workflowNo: string
  workflowType: ConstructionWorkflowType
  permitNo?: string
  publicToken?: string
  merchantName: string
  stallId: number
  stallCode?: string
  stallName?: string
  floorCode?: string
  floorName?: string
  currentStallStatus?: string
  constructionStartTime: string
  constructionEndTime: string
  merchantPrincipalName: string
  merchantPrincipalPhone: string
  constructionCompanyName: string
  constructionPrincipalName: string
  constructionPrincipalPhone: string
  propertyEngineerName: string
  propertyEngineerPhone: string
  commercialManagerName: string
  commercialManagerPhone: string
  relatedWorkflowId?: number
  relatedWorkflowNo?: string
  acceptanceRequestedAt?: string
  inspections?: any[]
  acceptances?: any[]
  status: string
  applicantName?: string
  remark?: string
  submittedAt?: string
  stallStatusBefore?: string
  stallStatusAfter?: string
  completedBy?: string
  completedAt?: string
  archivedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface ConstructionInspectionField {
  key: string
  label: string
  controlType: 'NORMAL_ABNORMAL'|'BOOLEAN'|'SELECT'|'MULTISELECT'|'NUMBER'|'TEXT'
  required?: boolean
  normalValue?: any
  options?: string[]
  requirePhotoOnAbnormal?: boolean
  enabled?: boolean
  sortOrder?: number
}

export interface ConstructionInspectionTemplate {
  id: number
  inspectionType: 'SECURITY'|'ENGINEERING'|'CLEANING'
  name: string
  fieldsJson: string
  enabled: boolean|number
  updatedAt?: string
}

export const constructionApi = {
  list: (params: Record<string, any>) => http.get('/api/v1/construction-workflows', { params }),
  get: (id: number) => http.get<ConstructionWorkflow>(`/api/v1/construction-workflows/${id}`),
  create: (data: Record<string, any>) => http.post<ConstructionWorkflow>('/api/v1/construction-workflows', data),
  submit: (id: number) => http.post<ConstructionWorkflow>(`/api/v1/construction-workflows/${id}/submit`),
  cancel: (id: number) => http.post<ConstructionWorkflow>(`/api/v1/construction-workflows/${id}/cancel`),
  complete: (id: number) => http.post<ConstructionWorkflow>(`/api/v1/construction-workflows/${id}/complete`),
  reset: (id: number) => http.post<ConstructionWorkflow>(`/api/v1/construction-workflows/${id}/reset`),
  permanentDelete: (id: number) => http.delete(`/api/v1/construction-workflows/${id}/permanent`)
  ,inspect: (id:number,type:'security'|'engineering'|'cleaning',data:Record<string,any>) => http.post(`/api/v1/construction-workflows/${id}/inspections/${type}`,{...data,inspectionType:type})
  ,closeInspection: (id:number,inspectionId:number) => http.post(`/api/v1/construction-workflows/${id}/inspections/${inspectionId}/close`)
  ,requestAcceptance: (id:number,data:Record<string,any>) => http.post(`/api/v1/construction-workflows/${id}/acceptance-request`,data)
  ,decideAcceptance: (id:number,data:Record<string,any>) => http.post(`/api/v1/construction-workflows/${id}/acceptance-result`,data)
  ,publicGet: (token:string) => http.get(`/api/v1/construction-public/${token}`)
  ,attachments: (id:number) => http.get(`/api/v1/construction-workflows/${id}/attachments`)
  ,uploadAttachment: (id:number,targetType:'INSPECTION'|'ACCEPTANCE',targetId:number,photoType:string,file:File,onProgress?:(value:number)=>void) => {
    const data=new FormData();data.append('file',file)
    return http.post(`/api/v1/construction-workflows/${id}/attachments`,data,{params:{targetType,targetId,photoType},headers:{'Content-Type':'multipart/form-data'},timeout:120000,onUploadProgress:e=>{if(e.total)onProgress?.(Math.min(99,Math.round(e.loaded*100/e.total)))}})
  }
}

export const constructionInspectionTemplateApi = {
  list: (enabledOnly=false) => http.get<ConstructionInspectionTemplate[]>('/api/v1/construction-inspection-templates',{params:{enabledOnly}}),
  save: (type:string,data:{name:string,fieldsJson:string,enabled:boolean}) => http.put<ConstructionInspectionTemplate>(`/api/v1/construction-inspection-templates/${type}`,data)
}
