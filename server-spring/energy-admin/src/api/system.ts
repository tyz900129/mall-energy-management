import http from './http'

export interface SystemRole { id: number; code: string; name: string; description?: string; menuIds?: number[] }
export interface SystemUser {
  id: number; username: string; displayName?: string; email?: string; phone?: string; status: number
  roles: string[]; roleIds: number[]; lastLoginAt?: string; createdAt?: string; updatedAt?: string
}
export interface SystemUserPayload {
  username: string; displayName?: string; email?: string; phone?: string; status: number; password?: string; roleIds?: number[]
}

export const systemUserApi = {
  page: (params: { keyword?: string; status?: number; pageNum?: number; pageSize?: number }) =>
    http.get<{ records: SystemUser[]; total: number; size: number; current: number }>('/api/v1/users', { params }),
  create: (data: SystemUserPayload) => http.post<SystemUser>('/api/v1/users', data),
  update: (id: number, data: SystemUserPayload) => http.put<SystemUser>(`/api/v1/users/${id}`, data),
  remove: (id: number) => http.delete(`/api/v1/users/${id}`),
  resetPassword: (id: number, newPassword: string) => http.put(`/api/v1/users/${id}/password`, { newPassword }),
  assignRoles: (id: number, roleIds: number[]) => http.put<SystemUser>(`/api/v1/users/${id}/roles`, { roleIds })
}
export const systemRoleApi = { list: () => http.get<SystemRole[]>('/api/v1/roles') }

export type DictionaryType = 'FLOOR'|'AREA'|'BUSINESS_TYPE'|'BUSINESS_CATEGORY'|'METER_LOCATION'|'WATER_METER_CALIBER'|'DRAINAGE_PIPE_CALIBER'|'EXHAUST_FAN'|'FRESH_AIR_UNIT'|'AIR_CONDITIONER_SPEC'|'NATURAL_GAS_CALIBER'|'GAS_SHUTOFF_LOCATION'|'POWER_NODE_TYPE'|'WATER_SUPPLY_NODE_TYPE'|'DRAINAGE_NODE_TYPE'|'OIL_EXHAUST_NODE_TYPE'|'FRESH_AIR_NODE_TYPE'|'WATER_SUPPLY_PIPE_SPEC'|'AIR_DUCT_SPEC'|'DRAINAGE_MEDIUM'
export interface DictionaryItem { id:number; dictType:DictionaryType; itemValue:string; itemLabel:string; sortOrder:number; status:number; remark?:string; updatedAt?:string }
export const dictionaryApi = {
  list: (type:DictionaryType, enabledOnly=true) => http.get<DictionaryItem[]>('/api/v1/dictionaries',{params:{type,enabledOnly}}),
  create: (data:Partial<DictionaryItem>) => http.post('/api/v1/dictionaries',data),
  update: (id:number,data:Partial<DictionaryItem>) => http.put(`/api/v1/dictionaries/${id}`,data),
  remove: (id:number) => http.delete(`/api/v1/dictionaries/${id}`)
}
export interface CableSpecification { id:number;specCode:string;cableModel:string;specification:string;ratedCurrentA:number;ratedPowerKw:number;status:number;sortOrder:number;remark?:string }
export const cableSpecificationApi = {
  list:(enabledOnly=true)=>http.get<CableSpecification[]>('/api/v1/cable-specifications',{params:{enabledOnly}}),
  create:(data:Partial<CableSpecification>)=>http.post('/api/v1/cable-specifications',data),
  update:(id:number,data:Partial<CableSpecification>)=>http.put(`/api/v1/cable-specifications/${id}`,data),
  remove:(id:number)=>http.delete(`/api/v1/cable-specifications/${id}`)
}
export interface PowerSwitchSpecification { id:number;specCode:string;brand?:string;switchModel:string;poleCount:string;ratedCurrentA:number;breakingCapacityKa?:number;switchType?:string;status:number;sortOrder:number;remark?:string }
export const powerSwitchSpecificationApi = {
  list:(enabledOnly=true)=>http.get<PowerSwitchSpecification[]>('/api/v1/power-switch-specifications',{params:{enabledOnly}}),
  create:(data:Partial<PowerSwitchSpecification>)=>http.post('/api/v1/power-switch-specifications',data),
  update:(id:number,data:Partial<PowerSwitchSpecification>)=>http.put(`/api/v1/power-switch-specifications/${id}`,data),
  remove:(id:number)=>http.delete(`/api/v1/power-switch-specifications/${id}`)
}
export interface AirConditionerSpecification { id:number;specCode:string;model:string;equipmentType:'FAN_COIL'|'HANGING_CABINET'|'OTHER';ratedPowerKw:number;airflowCmh:number;status:number;sortOrder:number;remark?:string }
export const airConditionerSpecificationApi = {
  list:(enabledOnly=true)=>http.get<AirConditionerSpecification[]>('/api/v1/air-conditioner-specifications',{params:{enabledOnly}}),
  create:(data:Partial<AirConditionerSpecification>)=>http.post('/api/v1/air-conditioner-specifications',data),
  update:(id:number,data:Partial<AirConditionerSpecification>)=>http.put(`/api/v1/air-conditioner-specifications/${id}`,data),
  remove:(id:number)=>http.delete(`/api/v1/air-conditioner-specifications/${id}`)
}
