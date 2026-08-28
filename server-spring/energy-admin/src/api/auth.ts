import http from './http'

export interface LoginRequest { username: string; password: string; rememberDevice?: boolean; fingerprint?: string; deviceName?: string }
export interface UserInfo {
  id: number
  username: string
  displayName?: string
  email?: string
  roles: string[]
}
export interface LoginResponse {
  token: string
  expiresInMinutes: number
  user: UserInfo
  deviceToken?: string
}

export const authApi = {
  login: (data: LoginRequest) => http.post<LoginResponse>('/auth/login', data),
  register: (data: {username:string;password:string;displayName?:string;phone?:string}) => http.post<string>('/auth/register',data),
  deviceLogin: (data:{deviceToken:string;fingerprint:string}) => http.post<LoginResponse>('/auth/device-login',data),
  me: () => http.get<UserInfo>('/auth/me'),
  logout: () => http.post('/auth/logout').catch(() => null)
}

