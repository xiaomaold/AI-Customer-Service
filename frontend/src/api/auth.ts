import { request } from '@/utils/request'
import type { ApiResponse, LoginResponse, UserInfo } from '@/types'

export const authApi = {
  login(data: { username: string; password: string }): Promise<ApiResponse<LoginResponse>> {
    return request.post('/auth/login', data)
  },

  me(): Promise<ApiResponse<UserInfo>> {
    return request.get('/auth/me')
  }
}
