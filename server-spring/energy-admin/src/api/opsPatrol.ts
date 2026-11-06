import http from './http'

export const opsPatrolApi = {
  // 处室与群
  depts: () => http.get('/api/v1/ops-patrol/depts'),
  saveDept: (id: number, data: any) => http.put(`/api/v1/ops-patrol/depts/${id}`, data),
  webhooks: () => http.get('/api/v1/ops-patrol/webhooks'),
  floorOptions: () => http.get('/api/v1/ops-patrol/floor-options'),
  // 时段
  slots: () => http.get('/api/v1/ops-patrol/slots'),
  updateSlot: (id: number, data: any) => http.put(`/api/v1/ops-patrol/slots/${id}`, data),
  // 检查项
  checkItems: (formType?: string) => http.get('/api/v1/ops-patrol/check-items', { params: { formType } }),
  saveCheckItem: (data: any) => (data.id ? http.put(`/api/v1/ops-patrol/check-items/${data.id}`, data) : http.post('/api/v1/ops-patrol/check-items', data)),
  removeCheckItem: (id: number) => http.delete(`/api/v1/ops-patrol/check-items/${id}`),
  // 排班
  duties: (month: string) => http.get('/api/v1/ops-patrol/duties', { params: { month } }),
  saveDuty: (data: any) => http.post('/api/v1/ops-patrol/duties', data),
  deleteDuty: (id: number) => http.delete(`/api/v1/ops-patrol/duties/${id}`),
  // 店铺联想
  stores: (keyword: string, deptId?: number) => http.get('/api/v1/ops-patrol/stores', { params: { keyword, deptId } }),
  // 任务看板
  taskBoard: (date?: string) => http.get('/api/v1/ops-patrol/tasks', { params: { date } }),
  taskDetail: (id: number) => http.get(`/api/v1/ops-patrol/tasks/${id}`),
  pushTask: (id: number) => http.post(`/api/v1/ops-patrol/tasks/${id}/push`),
  exemptTask: (id: number, reason: string) => http.post(`/api/v1/ops-patrol/tasks/${id}/exempt`, { reason }),
  removeTask: (id: number) => http.delete(`/api/v1/ops-patrol/tasks/${id}`),
  // 问题
  issues: (params: any) => http.get('/api/v1/ops-patrol/issues', { params }),
  issueDetail: (id: number) => http.get(`/api/v1/ops-patrol/issues/${id}`),
  removeIssue: (id: number) => http.delete(`/api/v1/ops-patrol/issues/${id}`),
  assignIssue: (id: number, data: any) => http.put(`/api/v1/ops-patrol/issues/${id}/assign`, data),
  rectifyIssue: (id: number, note: string, photos: File[]) => {
    const form = new FormData()
    form.append('note', note)
    photos.forEach(file => form.append('photos', file))
    return http.post(`/api/v1/ops-patrol/issues/${id}/rectify`, form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
  },
  reviewIssue: (id: number, data: any) => http.post(`/api/v1/ops-patrol/issues/${id}/review`, data),
  // 交接
  handovers: (date?: string) => http.get('/api/v1/ops-patrol/handovers', { params: { date } }),
  // 报表
  dailyReport: (date?: string) => http.get('/api/v1/ops-patrol/reports/daily', { params: { date } }),
}

// H5 免登录接口（token 鉴权）
export const opsPatrolPublicApi = {
  entry: (token: string) => http.get('/api/v1/ops-patrol-public/entry', { params: { token } }),
  task: (token: string, taskId: number) => http.get(`/api/v1/ops-patrol-public/tasks/${taskId}`, { params: { token } }),
  saveItems: (token: string, taskId: number, data: any) => http.post(`/api/v1/ops-patrol-public/tasks/${taskId}/items`, data, { params: { token } }),
  submit: (token: string, taskId: number, data: any) => http.post(`/api/v1/ops-patrol-public/tasks/${taskId}/submit`, data, { params: { token } }),
  confirmHandover: (token: string, taskId: number, data: any) => http.post(`/api/v1/ops-patrol-public/tasks/${taskId}/confirm-handover`, data, { params: { token } }),
  stores: (token: string, keyword: string) => http.get('/api/v1/ops-patrol-public/stores', { params: { token, keyword } }),
  // 单张上传：避免一次 multipart 体过大导致网关/代理超时
  uploadPhoto: (token: string, issueId: number, file: File) => {
    const form = new FormData()
    form.append('photos', file)
    return http.post(`/api/v1/ops-patrol-public/issues/${issueId}/photos`, form, {
      params: { token },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 90000
    })
  },
}
