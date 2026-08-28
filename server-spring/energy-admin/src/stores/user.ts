import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi, type UserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('user') || 'null'))

  async function fingerprint() {
    const raw=[navigator.userAgent,navigator.language,screen.width,screen.height,screen.colorDepth,Intl.DateTimeFormat().resolvedOptions().timeZone].join('|')
    const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(raw))
    return Array.from(new Uint8Array(digest)).map(v=>v.toString(16).padStart(2,'0')).join('')
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

  async function fetchMe() {
    const u: any = await authApi.me()
    user.value = u
    localStorage.setItem('user', JSON.stringify(u))
    return u
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('device_login_token')
  }

  return { token, user, login, autoLogin, fetchMe, logout }
})

