import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type UserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('user') || 'null'))

  async function fingerprint() {
    const raw=[navigator.userAgent,navigator.language,screen.width,screen.height,screen.colorDepth,Intl.DateTimeFormat().resolvedOptions().timeZone].join('|')
    if (window.isSecureContext && crypto?.subtle) {
      try {
        const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(raw))
        return Array.from(new Uint8Array(digest)).map(v=>v.toString(16).padStart(2,'0')).join('')
      } catch {}
    }
    let id=localStorage.getItem('device_fingerprint_fallback')
    if(!id){id=[Date.now().toString(36),Math.random().toString(36).slice(2,12)].join('-');localStorage.setItem('device_fingerprint_fallback',id)}
    let hash=2166136261
    for(let i=0;i<raw.length;i++){hash^=raw.charCodeAt(i);hash=Math.imul(hash,16777619)}
    return `fallback-${(hash>>>0).toString(16)}-${id}`
  }

  function saveSession(resp:any){token.value=resp.token;user.value=resp.user;localStorage.setItem('token',resp.token);localStorage.setItem('user',JSON.stringify(resp.user))}

  async function login(username: string, password: string, rememberDevice=true) {
    const fp=await fingerprint()
    const resp: any = await authApi.login({ username, password, rememberDevice, fingerprint:fp, deviceName:navigator.platform||'浏览器设备' })
    saveSession(resp)
    if(resp.deviceToken)localStorage.setItem('device_login_token',resp.deviceToken)
    return resp
  }

  async function autoLogin(){const deviceToken=localStorage.getItem('device_login_token');if(!deviceToken)return false;try{const resp:any=await authApi.deviceLogin({deviceToken,fingerprint:await fingerprint()});saveSession(resp);return true}catch{localStorage.removeItem('device_login_token');return false}}

  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  /**
   * 恢复登录态：先校验现有 JWT，失效时用本机设备凭据静默换新 token。
   * 公开 H5 页面（如巡检填报）打开时调用，用于自动带出填报人信息。
   */
  async function restoreSession(): Promise<UserInfo | null> {
    if (token.value || localStorage.getItem('token')) {
      try { return await fetchMe() } catch { clearSession() }
    }
    if (await autoLogin()) return user.value
    return null
  }

  async function fetchMe() {
    const u: any = await authApi.me()
    user.value = u
    localStorage.setItem('user', JSON.stringify(u))
    return u
  }

  function logout() {
    clearSession()
    localStorage.removeItem('device_login_token')
  }

  /** 权限判定只看后端返回的有效权限码（后端已按「角色权限 ∪ ALLOW − DENY」计算）。 */
  function hasPermission(code: string) {
    return !!user.value?.permissions?.includes(code)
  }

  return { token, user, login, autoLogin, fetchMe, restoreSession, logout, hasPermission }
})
