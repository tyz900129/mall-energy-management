import http from './http'

export const exceptionApi = {
  list: (params: Record<string, any>) => http.get('/api/v1/data-exceptions', { params }),
  updateStatus: (id: number, data: { status: string; handleResult?: string }) =>
    http.put(`/api/v1/data-exceptions/${id}/status`, data)
}

