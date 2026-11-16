import http from './http'

export const deviceAlertApi = {
  settings: () => http.get('/api/v1/energy/device-alerts/settings'),
  saveSettings: (data: Record<string, any>) => http.put('/api/v1/energy/device-alerts/settings', data),
  exclusions: (params: Record<string, any>) => http.get('/api/v1/energy/device-alerts/exclusions', { params }),
  addExclusion: (data: Record<string, any>) => http.post('/api/v1/energy/device-alerts/exclusions', data),
  addExclusionBatch: (data: Record<string, any>) => http.post('/api/v1/energy/device-alerts/exclusions/batch', data),
  offlineDevices: () => http.get('/api/v1/energy/device-alerts/offline-devices'),
  deleteExclusion: (id: number) => http.delete(`/api/v1/energy/device-alerts/exclusions/${id}`),
  events: (params: Record<string, any>) => http.get('/api/v1/energy/device-alerts/events', { params }),
  runs: () => http.get('/api/v1/energy/device-alerts/runs'),
  run: () => http.post('/api/v1/energy/device-alerts/run')
}
