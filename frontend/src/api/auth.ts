import { http } from './request'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
}

/** 登录响应：token + 用户信息 */
export interface LoginResult {
  token: string
  username: string
  role: string
}

/** 登录：POST /api/auth/login */
export function login(data: LoginParams): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', data)
}
