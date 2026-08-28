import http from './http'

export interface PlatformConfig {
  id?: number
  platformName?: string
  baseUrl: string
  accountUsername: string
  passwordConfigured?: boolean
  spaceId: string
  enabled: boolean
  autoSync: boolean
  syncCron: string
  connectTimeoutMs: number
  lastConnectAt?: string
  lastConnectStatus?: string
  lastSyncAt?: string
  lastSyncStatus?: string
  remark?: string
  [k: string]: any
}

export const platformApi = {
  getConfig: () => http.get<PlatformConfig>('/api/v1/platform/config'),
  updateConfig: (data: Partial<PlatformConfig> & { password?: string }) =>
    http.put<PlatformConfig>('/api/v1/platform/config', data),
  testConnection: () => http.post<{ ok: boolean; message: string }>('/api/v1/platform/config/test-connection'),
  triggerSync: () => http.post<{ batchNo: string; status: string }>('/api/v1/platform/sync'),
  listBatches: (params: { current?: number; size?: number; pageNum?: number; pageSize?: number }) =>
    http.get<{ records: any[]; total: number; size: number; current: number }>('/api/v1/platform/sync/batches', { params }),
  latestBatch: () => http.get<any>('/api/v1/platform/sync/latest')
}

