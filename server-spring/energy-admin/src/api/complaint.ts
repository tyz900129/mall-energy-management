import http from './http'

const base = '/api/v1/complaints'

export const complaintApi = {
  page: (params: Record<string, any>) => http.get(base, { params }),
  detail: (id: number) => http.get(`${base}/${id}`),
  create: (data: Record<string, any>) => http.post(base, data),
  update: (id: number, data: Record<string, any>) => http.put(`${base}/${id}`, data),
  changeStatus: (id: number, status: string, note = '') => http.post(`${base}/${id}/status`, { status, note }),
  close: (id: number, satisfaction: string, note = '') => http.post(`${base}/${id}/close`, { satisfaction, note }),
  remove: (id: number) => http.delete(`${base}/${id}`),
  brands: (keyword = '', businessType = '') => http.get(`${base}/options/brands`, { params: { keyword, businessType } }),
  brandByToken: (token: string) => http.get(`${base}/options/brands/by-token/${encodeURIComponent(token)}`),
  createBrand: (data: Record<string, any>) => http.post(`${base}/options/brands`, data),
  users: (keyword = '', roleCodes = '') => http.get(`${base}/options/users`, { params: { keyword, roleCodes } }),
  staffOptions: (type = '', keyword = '') => http.get(`${base}/options/staff`, { params: { type, keyword } }),
  staffByUsers: (userIds: number[], type = 'MANAGER') =>
    http.get(`${base}/options/staff/by-users`, { params: { userIds: userIds.join(','), type } }),
  staffList: (params: Record<string, any>) => http.get(`${base}/staff`, { params }),
  createStaff: (data: Record<string, any>) => http.post(`${base}/staff`, data),
  updateStaff: (id: number, data: Record<string, any>) => http.put(`${base}/staff/${id}`, data),
  removeStaff: (id: number) => http.delete(`${base}/staff/${id}`),
  dicts: (type = '') => http.get(`${base}/options/dicts`, { params: { type } }),
  businessTypes: () => http.get(`${base}/options/business-types`),
  attachments: (id: number) => http.get(`${base}/${id}/attachments`),
  uploadAttachments: (id: number, files: File[]) => {
    const data = new FormData()
    files.forEach(file => data.append('photos', file))
    return http.post(`${base}/${id}/attachments`, data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
  },
  attachmentFile: (id: number, attachmentId: number) => http.get(`${base}/${id}/attachments/${attachmentId}/file`, { responseType: 'blob' }),
  removeAttachment: (id: number, attachmentId: number) => http.delete(`${base}/${id}/attachments/${attachmentId}`),
  template: () => http.get(`${base}/import/template`, { responseType: 'blob' }),
  importExcel: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return http.post(`${base}/import`, data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000 })
  },
  updateImportExcel: (file: File, dryRun: boolean) => {
    const data = new FormData()
    data.append('file', file)
    return http.post(`${base}/import/update`, data, {
      params: { dryRun },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 180000
    })
  },
  importErrors: (token: string) => http.get(`${base}/import/errors/${token}`, { responseType: 'blob' }),
  export: (params: Record<string, any>) => http.get(`${base}/export`, { params, responseType: 'blob' }),
  statsOverview: (params: Record<string, any>) => http.get(`${base}/stats/overview`, { params }),
  statsDistribution: (params: Record<string, any>) => http.get(`${base}/stats/distribution`, { params }),
  statsTrend: (params: Record<string, any>) => http.get(`${base}/stats/trend`, { params }),
  statsPeriodCompare: (params: Record<string, any>) => http.get(`${base}/stats/period-compare`, { params }),
  statsCross: (params: Record<string, any>) => http.get(`${base}/stats/cross`, { params }),
  statsRanking: (params: Record<string, any>) => http.get(`${base}/stats/ranking`, { params }),
  statsEfficiency: (params: Record<string, any>) => http.get(`${base}/stats/efficiency`, { params }),
  statsCost: (params: Record<string, any>) => http.get(`${base}/stats/cost`, { params }),
  statsFilters: () => http.get(`${base}/stats/filters`),
  statsExport: (params: Record<string, any>) => http.get(`${base}/stats/export`, { params, responseType: 'blob' })
}

/** 下载 blob 响应。 */
export function saveBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
