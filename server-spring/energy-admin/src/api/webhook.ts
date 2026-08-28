import http from './http'

export interface WebhookConfig {
  id: number
  name: string
  provider: 'WECOM' | 'FEISHU' | 'DINGTALK'
  webhookUrlConfigured: boolean
  enabled: number
  eventTypes: string
  updatedAt?: string
}

export interface WebhookConfigInput {
  name: string
  provider: 'WECOM' | 'FEISHU' | 'DINGTALK'
  webhookUrl?: string
  enabled: boolean
  eventTypes: string
}

export const webhookApi = {
  list: () => http.get<any, WebhookConfig[]>('/api/v1/webhooks/config'),
  create: (data: WebhookConfigInput) => http.post('/api/v1/webhooks/config', data),
  update: (id: number, data: WebhookConfigInput) => http.put(`/api/v1/webhooks/config/${id}`, data),
  delete: (id: number) => http.delete(`/api/v1/webhooks/config/${id}`)
}
