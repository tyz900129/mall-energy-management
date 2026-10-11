import http from './http'

export const arrearsApi = {
  accounts: (params: Record<string, any>) => http.get('/api/v1/energy/arrears/accounts', { params }),
  runs: () => http.get('/api/v1/energy/arrears/runs'),
  sync: () => http.post('/api/v1/energy/arrears/sync'),
  exclusion: (accountNumber: string, excluded: boolean, remark?: string) =>
    http.put(`/api/v1/energy/arrears/accounts/${encodeURIComponent(accountNumber)}/exclusion`, { excluded, remark })
}
