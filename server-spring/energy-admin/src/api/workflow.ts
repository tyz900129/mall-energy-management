import http from './http'

export interface DeviceCheck {
  platformDeviceId: string
  deviceName?: string
  energyType?: string
  commAddress?: string
  serialNumber?: string
  currentAccountNumbers: string[]
  conflict: boolean
}

export interface NewMerchantPrecheck {
  merchantCode: string
  stallId: number
  stallCode: string
  merchantCodeExists: boolean
  allowed: boolean
  reasons: string[]
  devices: DeviceCheck[]
}

export interface BillingChangePrecheck {
  workflowType: string; merchantCode: string; merchantName?: string; merchantExists: boolean
  allowed: boolean; hasWaterMeter: boolean; requiresWaterConfirmation: boolean; reasons: string[]; notices: string[]
  merchantStalls: Array<{stallId:number;stallCode:string;stallName?:string;deviceIds:string[]}>
  devices: Array<DeviceCheck & {gatewayName?:string;channelName?:string;productName?:string;currentAccountNames:string[];included:boolean}>
}

export const workflowApi = {
  list: (params: Record<string, any>) => http.get('/api/v1/workflows', { params }),
  get: (id: number) => http.get(`/api/v1/workflows/${id}`),
  precheckNewMerchant: (data: { merchantCode: string; stallId: number }) =>
    http.post<NewMerchantPrecheck>('/api/v1/workflows/precheck/new-merchant', data),
  createNewMerchant: (data: Record<string, any>) => http.post('/api/v1/workflows', data),
  precheckBillingChange: (data: Record<string, any>) => http.post<BillingChangePrecheck>('/api/v1/workflows/precheck/billing-change', data),
  createBillingChange: (data: Record<string, any>) => http.post('/api/v1/workflows/billing-change', data),
  submit: (id: number) => http.post(`/api/v1/workflows/${id}/submit`),
  cancel: (id: number) => http.post(`/api/v1/workflows/${id}/cancel`),
  reset: (id: number) => http.post(`/api/v1/workflows/${id}/reset`),
  permanentDelete: (id: number) => http.delete(`/api/v1/workflows/${id}/permanent`),
  matchEvidence: (id: number) => http.get(`/api/v1/workflows/${id}/match-evidence`),
  resolveMatch: (id: number, data: Record<string, any>) => http.post(`/api/v1/workflows/${id}/match-resolution`, data),
  updateOaProcessNo: (id: number, value: string) => http.put(`/api/v1/workflows/${id}/oa-process-no`, { value }),
  updateCompletionRemark: (id: number, value: string) => http.put(`/api/v1/workflows/${id}/completion-remark`, { value })
}
