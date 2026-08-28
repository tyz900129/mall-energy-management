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

export type DictionaryType = 'FLOOR' | 'AREA' | 'BUSINESS_TYPE' | 'BUSINESS_CATEGORY'
export interface DictionaryItem { id:number; dictType:DictionaryType; itemValue:string; itemLabel:string; sortOrder:number; status:number; remark?:string; updatedAt?:string }
export const dictionaryApi = {
  list: (type:DictionaryType, enabledOnly=true) => http.get<DictionaryItem[]>('/api/v1/dictionaries',{params:{type,enabledOnly}}),
  create: (data:Partial<DictionaryItem>) => http.post('/api/v1/dictionaries',data),
  update: (id:number,data:Partial<DictionaryItem>) => http.put(`/api/v1/dictionaries/${id}`,data),
  remove: (id:number) => http.delete(`/api/v1/dictionaries/${id}`)
}

