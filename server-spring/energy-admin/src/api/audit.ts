import http from './http'
export const auditApi={list:(params:Record<string,any>)=>http.get('/api/v1/audit-logs',{params})}
