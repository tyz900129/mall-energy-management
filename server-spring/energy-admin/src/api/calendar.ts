import request from './http'
export const calendarApi = {
  tasks: (params: any) => request.get('/api/v1/calendar/tasks', { params }),
  generationLogs: (params: any = {}) => request.get('/api/v1/calendar/generation-logs', { params })
}
