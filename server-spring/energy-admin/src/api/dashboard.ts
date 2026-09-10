import http from './http'

export interface DashboardStats {
  billingAccounts: number
  stalls: number
  warehouses: number
  boundStalls: number
  monthlySubmittedWorkflows: number
}

export const dashboardApi = {
  stats: () => http.get<DashboardStats>('/api/v1/dashboard/stats')
}
