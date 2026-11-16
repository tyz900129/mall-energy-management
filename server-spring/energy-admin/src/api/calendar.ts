import request from './http'

export const calendarApi = {
  tasks: (params: any) => request.get('/api/v1/calendar/tasks', { params }),
  generationLogs: (params: any = {}) => request.get('/api/v1/calendar/generation-logs', { params }),
  ownerOptions: () => request.get('/api/v1/calendar/owner-options'),
  plans: (params: any = {}) => request.get('/api/v1/calendar/plans', { params }),
  plan: (id: number) => request.get(`/api/v1/calendar/plans/${id}`),
  createPlan: (data: any) => request.post('/api/v1/calendar/plans', data),
  updatePlan: (id: number, data: any) => request.put(`/api/v1/calendar/plans/${id}`, data),
  togglePlan: (id: number, enabled: boolean) =>
    request.post(`/api/v1/calendar/plans/${id}/toggle`, null, { params: { enabled } }),
  generatePlan: (id: number, date?: string) =>
    request.post(`/api/v1/calendar/plans/${id}/generate`, null, { params: date ? { date } : {} })
}
