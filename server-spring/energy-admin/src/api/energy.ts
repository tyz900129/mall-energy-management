import http from './http'

export const energyApi = {
  dimensions: () => http.get<any[]>('/api/v1/energy/dimensions'),
  createDimension: (data: any) => http.post('/api/v1/energy/dimensions', data),
  updateDimension: (id: number, data: any) => http.put(`/api/v1/energy/dimensions/${id}`, data),
  deleteDimension: (id: number) => http.delete(`/api/v1/energy/dimensions/${id}`),
  createDimensionValue: (dimensionId: number, data: any) => http.post(`/api/v1/energy/dimensions/${dimensionId}/values`, data),
  updateDimensionValue: (id: number, data: any) => http.put(`/api/v1/energy/dimension-values/${id}`, data),
  deleteDimensionValue: (id: number) => http.delete(`/api/v1/energy/dimension-values/${id}`),
  dimensionDevices: (dimensionId: number, params: any = {}) => http.get<any[]>(`/api/v1/energy/dimensions/${dimensionId}/devices`, { params }),
  dimensionHistory: (dimensionId: number, deviceId: string) => http.get<any[]>(`/api/v1/energy/dimensions/${dimensionId}/devices/${encodeURIComponent(deviceId)}/history`),
  assignDimension: (data: any) => http.post('/api/v1/energy/dimensions/assign', data),
  groups: () => http.get<any[]>('/api/v1/energy/groups'),
  createGroup: (data: any) => http.post('/api/v1/energy/groups', data),
  updateGroup: (id: number, data: any) => http.put(`/api/v1/energy/groups/${id}`, data),
  deleteGroup: (id: number) => http.delete(`/api/v1/energy/groups/${id}`),
  devices: (params: any = {}) => http.get<any[]>('/api/v1/energy/devices', { params }),
  assignDevices: (groupId: number, deviceIds: string[]) => http.post('/api/v1/energy/devices/assign', { groupId, deviceIds }),
  metrics: () => http.get<any[]>('/api/v1/energy/metrics'),
  saveMetric: (data: any) => http.put('/api/v1/energy/metrics', data),
  syncUsage: (data: any) => http.post<any>('/api/v1/energy/usage/sync', data, { timeout: 10 * 60 * 1000 }),
  syncBatches: () => http.get<any[]>('/api/v1/energy/usage/sync-batches'),
  analysis: (params: any) => http.get<any>('/api/v1/energy/analysis', { params })
}
