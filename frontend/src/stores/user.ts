import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, type LoginParams } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/request'

/** 用户信息本地持久化的 key */
const USERNAME_KEY = 'pt_username'
const ROLE_KEY = 'pt_role'

/**
 * 用户 Store
 * token / username / role 持久化到 localStorage，刷新页面不丢失登录态
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const username = ref(localStorage.getItem(USERNAME_KEY) || '')
  const role = ref(localStorage.getItem(ROLE_KEY) || '')

  /** 登录：成功后持久化 token 与用户信息 */
  async function login(params: LoginParams): Promise<void> {
    const res = await loginApi(params)
    token.value = res.token
    username.value = res.username
    role.value = res.role
    setToken(res.token)
    localStorage.setItem(USERNAME_KEY, res.username)
    localStorage.setItem(ROLE_KEY, res.role)
  }

  /** 退出登录：清理本地状态 */
  function logout(): void {
    token.value = ''
    username.value = ''
    role.value = ''
    clearToken()
    localStorage.removeItem(USERNAME_KEY)
    localStorage.removeItem(ROLE_KEY)
  }

  return { token, username, role, login, logout }
})
