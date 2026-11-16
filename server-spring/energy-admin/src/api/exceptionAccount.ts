import http from './http'

export const exceptionAccountApi = {
  accounts: (params: Record<string, any>) => http.get('/api/v1/energy/exception-accounts/accounts', { params }),
  runs: () => http.get('/api/v1/energy/exception-accounts/runs'),
  sync: () => http.post('/api/v1/energy/exception-accounts/sync'),
  exclusion: (accountNumber: string, excluded: boolean, remark?: string) =>
    http.put(`/api/v1/energy/exception-accounts/accounts/${encodeURIComponent(accountNumber)}/exclusion`, { excluded, remark }),
  settings: () => http.get('/api/v1/energy/exception-accounts/settings'),
  saveSettings: (data: Record<string, any>) => http.put('/api/v1/energy/exception-accounts/settings', data)
}
