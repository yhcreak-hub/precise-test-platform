import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

/** localStorage 中 token 的 key */
const TOKEN_KEY = 'pt_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 未登录跳转登录页（使用 location 而非 router，避免 request -> router -> store -> request 循环依赖） */
function redirectToLogin(): void {
  clearToken()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：自动携带 Authorization: Bearer <token>
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一处理业务码（code !== 200）与 401
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      if (res.code === 401) {
        redirectToLogin()
      }
      return Promise.reject(new Error(res.msg || 'Request Error'))
    }
    // 解包：调用方直接拿到 data 字段
    return res.data as unknown as AxiosResponse
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      redirectToLogin()
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

/**
 * 类型安全的请求封装
 * 示例：const list = await http.get<Project[]>('/projects', { page: 1, size: 10 })
 */
export const http = {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    return request.get(url, { params }) as Promise<T>
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return request.post(url, data) as Promise<T>
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return request.put(url, data) as Promise<T>
  },
  delete<T>(url: string): Promise<T> {
    return request.delete(url) as Promise<T>
  }
}

export default request
