import http from './http'

export interface Merchant {
  id: number
  merchantCode: string
  merchantName: string
  contactName?: string
  contactPhone?: string
  status: number
  createdAt?: string
  sourcePlatformAccountId?: string
  [k: string]: any
}

export interface Stall {
  id: number
  stallCode: string
  stallName: string
  floorCode?: string
  floorName?: string
  areaName?: string
  area?: number
  businessType?: string
  businessCategory?: string
  status: string
  currentMerchantId?: number
  currentMerchantCode?: string
  currentMerchantName?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
  [k: string]: any
}

export interface StallDevice {
  id?: number
  stallId: number
  stallCode?: string
  stallName?: string
  platformDeviceId: string
  deviceName?: string
  commAddress?: string
  serialNumber?: string
  energyType?: string
  energyTypeName?: string
  bindType: string
  boundAt?: string
  effectiveFrom?: string
  effectiveTo?: string
  unboundAt?: string
  boundBy?: string
  unboundBy?: string
  status: number | string
  [k: string]: any
}

export interface PlatformDevice {
  platformDeviceId: string
  deviceName?: string
  commAddress?: string
  serialNumber?: string
  gatewayId?: string
  gatewayName?: string
  channelName?: string
  productName?: string
  energyType?: string
  energyTypeName?: string
  commStatus?: number
  lastAcqTime?: string
  lastSyncedAt?: string
  syncBatchId?: number
}

export const merchantApi = {
  page: (params: { keyword?: string; status?: number | string; pageNum?: number; pageSize?: number }) =>
    http.get<{ records: Merchant[]; total: number; size: number; current: number }>('/api/v1/merchants', { params }),
  get: (id: number) => http.get<Merchant>(`/api/v1/merchants/${id}`),
  getByCode: (code: string) => http.get<Merchant>(`/api/v1/merchants/code/${code}`),
  create: (data: Partial<Merchant>) => http.post('/api/v1/merchants', data),
  update: (id: number, data: Partial<Merchant>) => http.put(`/api/v1/merchants/${id}`, data),
  previewCode: (name: string) => http.get('/api/v1/merchants/code-preview', { params: { name } })
}

export const accountApi = {
  page: (params: { keyword?: string; showDisabled?: boolean; pageNum?: number; pageSize?: number }) =>
    http.get('/api/v1/accounts', { params }),
  updateMerchantCode: (accountNumber:string, merchantCode:string) =>
    http.put(`/api/v1/accounts/${encodeURIComponent(accountNumber)}/merchant-code`, { merchantCode })
}

export const stallApi = {
  page: (params: {
    keyword?: string
    status?: number | string
    floorCode?: string
    businessType?: string
    pageNum?: number
    pageSize?: number
  }) =>
    http.get<{ records: Stall[]; total: number; size: number; current: number }>('/api/v1/stalls', { params }),
  get: (id: number) => http.get<Stall>(`/api/v1/stalls/${id}`),
  create: (data: Partial<Stall>) => http.post('/api/v1/stalls', data),
  update: (id: number, data: Partial<Stall>) => http.put(`/api/v1/stalls/${id}`, data),
  softDelete: (id: number) => http.delete(`/api/v1/stalls/${id}`),
  permanentDelete: (id: number) => http.delete(`/api/v1/stalls/${id}/permanent`),
  exportExcel: (params: Record<string, any>) => http.get('/api/v1/stalls/export', { params, responseType: 'blob' }),
  importExcel: (file: File) => { const data=new FormData();data.append('file',file);return http.post('/api/v1/stalls/import',data,{headers:{'Content-Type':'multipart/form-data'}}) }
}

export const stallDeviceApi = {
  listByStall: (stallId: number) => http.get<StallDevice[]>(`/api/v1/stalls/${stallId}/devices`),
  bind: (stallId: number, data: { platformDeviceId: string; remark?: string }) =>
    http.post(`/api/v1/stalls/${stallId}/devices`, data),
  unbind: (stallId: number, deviceId: string) =>
    http.delete(`/api/v1/stalls/${stallId}/devices/${deviceId}`),
  historyByDevice: (deviceId: string) => http.get<StallDevice[]>(`/api/v1/devices/${deviceId}/history`),
  listByDevice: (deviceId: string) => http.get<StallDevice[]>(`/api/v1/devices/${deviceId}/stall`)
}

export const deviceApi = {
  search: (params: { keyword?: string; energyType?: string; bound?: boolean; pageNum?: number; pageSize?: number }) =>
    http.get<{ records: PlatformDevice[]; total: number; size: number; current: number }>('/api/v1/devices/search', { params }),
  get: (deviceId: string) => http.get<PlatformDevice>(`/api/v1/devices/${deviceId}`)
}
