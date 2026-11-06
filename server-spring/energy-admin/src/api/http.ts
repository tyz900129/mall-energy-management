import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const http: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 60000
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 公开 H5 页面（巡检、核验、访客申请等）允许匿名访问，401 时不能在页面内强行跳登录
const PUBLIC_PAGE_PREFIXES = ['/patrol/', '/ops-patrol/', '/verify/', '/construction-public/', '/operation/stall/', '/access/visitor-apply', '/warehouse/']

http.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0) return data.data
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(data)
    }
    return data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      const publicPage = PUBLIC_PAGE_PREFIXES.some(prefix => location.pathname.startsWith(prefix))
      if (!publicPage && location.pathname !== '/login') {
        location.href = '/login'
        ElMessage.error('登录已过期')
      }
    } else {
      ElMessage.error(msg || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
