import http from './http'

export const patrolApi = {
  points: (includeDisabled = false) => http.get('/api/v1/patrol/points', { params: { includeDisabled } }),
  savePoint: (data: any) => (data.id ? http.put(`/api/v1/patrol/points/${data.id}`, data) : http.post('/api/v1/patrol/points', data)),
  removePoint: (id: number) => http.delete(`/api/v1/patrol/points/${id}`),
  departments: () => http.get('/api/v1/patrol/departments'),
  updateDepartment: (id: number, data: any) => http.put(`/api/v1/patrol/departments/${id}`, data),
  resetDepartmentToken: (id: number) => http.post(`/api/v1/patrol/departments/${id}/token`),
  webhooks: () => http.get('/api/v1/patrol/webhooks'),
  duties: (month: string) => http.get('/api/v1/patrol/duties', { params: { month } }),
  saveDuty: (data: any) => http.post('/api/v1/patrol/duties', data),
  deleteDuty: (id: number) => http.delete(`/api/v1/patrol/duties/${id}`),
  categories: () => http.get('/api/v1/patrol/categories'),
  saveCategory: (data: any) => (data.id ? http.put(`/api/v1/patrol/categories/${data.id}`, data) : http.post('/api/v1/patrol/categories', data)),
  removeCategory: (id: number) => http.delete(`/api/v1/patrol/categories/${id}`),
  pointTypes: () => http.get('/api/v1/patrol/point-types'),
  savePointType: (data: any) => (data.id ? http.put(`/api/v1/patrol/point-types/${data.id}`, data) : http.post('/api/v1/patrol/point-types', data)),
  removePointType: (id: number) => http.delete(`/api/v1/patrol/point-types/${id}`),
  todayRound: () => http.get('/api/v1/patrol/rounds/today'),
  push: () => http.post('/api/v1/patrol/rounds/push'),
  submissions: (params: any) => http.get('/api/v1/patrol/submissions', { params }),
  removeSubmission: (id: number) => http.delete(`/api/v1/patrol/submissions/${id}`),
  issues: (params: any) => http.get('/api/v1/patrol/issues', { params }),
  removeIssue: (id: number) => http.delete(`/api/v1/patrol/issues/${id}`),
  issue: (id: number) => http.get(`/api/v1/patrol/issues/${id}`),
  assign: (id: number, data: any) => http.post(`/api/v1/patrol/issues/${id}/assign`, data),
  review: (id: number, data: any) => http.post(`/api/v1/patrol/issues/${id}/review`, data),
  overview: () => http.get('/api/v1/patrol/stats/overview'),
  entry: (token: string) => http.get('/api/v1/patrol/entry', { params: { token } }),
  form: (id: number, token: string) => http.get(`/api/v1/patrol/points/${id}/form`, { params: { token } }),
  submit: (token: string, data: Record<string, any>, photos: File[], onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('token', token)
    Object.entries(data).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return
      if (key === 'checkResults') form.append(key, JSON.stringify(value))
      else form.append(key, String(value))
    })
    photos.forEach(file => form.append('photos', file))
    return http.post('/api/v1/patrol/submissions', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
      onUploadProgress: (event: any) => onProgress?.(event.total ? Math.round((event.loaded * 100) / event.total) : 0)
    })
  },
  // 提交后单张补传现场照片
  appendPhoto: (token: string, submissionId: number, file: File) => {
    const form = new FormData()
    form.append('photos', file)
    return http.post(`/api/v1/patrol-public/submissions/${submissionId}/photos`, form, {
      params: { token },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 90000
    })
  },
  publicIssue: (token: string) => http.get(`/api/v1/patrol-public/issues/${encodeURIComponent(token)}`),
  publicFeedback: (token: string, note: string, photos: File[]) => {
    const form = new FormData()
    form.append('note', note)
    photos.forEach(file => form.append('photos', file))
    return http.post(`/api/v1/patrol-public/issues/${encodeURIComponent(token)}/feedback`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  },
  publicDepartment: (token: string, range: string) => http.get(`/api/v1/patrol-public/departments/${encodeURIComponent(token)}`, { params: { range } })
}
