import { request } from '@/utils/request'
import type {
  AdminConfigVO,
  AdminMissedQuestionDashboardVO,
  AdminMissedQuestionVO,
  AdminRoleVO,
  AdminUserVO,
  ApiResponse
} from '@/types'

export const adminApi = {
  listUsers(params?: {
    keyword?: string
    status?: string
    roleCode?: string
  }): Promise<ApiResponse<AdminUserVO[]>> {
    return request.get('/admin/users', { params })
  },

  createUser(data: {
    username: string
    password: string
    nickname?: string
    realName?: string
    mobile?: string
    email?: string
    status?: string
    roleIds?: string[]
  }): Promise<ApiResponse<AdminUserVO>> {
    return request.post('/admin/users', data)
  },

  updateUser(
    userId: string,
    data: {
      nickname?: string
      realName?: string
      mobile?: string
      email?: string
      status?: string
      roleIds?: string[]
    }
  ): Promise<ApiResponse<AdminUserVO>> {
    return request.put(`/admin/users/${userId}`, data)
  },

  resetPassword(userId: string, data: { password: string }): Promise<ApiResponse<void>> {
    return request.post(`/admin/users/${userId}/reset-password`, data)
  },

  deleteUser(userId: string): Promise<ApiResponse<void>> {
    return request.delete(`/admin/users/${userId}`)
  },

  listRoles(params?: { keyword?: string; status?: string }): Promise<ApiResponse<AdminRoleVO[]>> {
    return request.get('/admin/roles', { params })
  },

  createRole(data: {
    roleCode: string
    roleName: string
    status?: string
    remark?: string
  }): Promise<ApiResponse<AdminRoleVO>> {
    return request.post('/admin/roles', data)
  },

  updateRole(
    roleId: string,
    data: {
      roleName?: string
      status?: string
      remark?: string
    }
  ): Promise<ApiResponse<AdminRoleVO>> {
    return request.put(`/admin/roles/${roleId}`, data)
  },

  deleteRole(roleId: string): Promise<ApiResponse<void>> {
    return request.delete(`/admin/roles/${roleId}`)
  },

  listConfigs(params?: { keyword?: string; status?: string }): Promise<ApiResponse<AdminConfigVO[]>> {
    return request.get('/admin/configs', { params })
  },

  listMissedQuestions(params?: {
    keyword?: string
    routeMode?: string
    status?: string
  }): Promise<ApiResponse<AdminMissedQuestionVO[]>> {
    return request.get('/admin/missed-questions', { params })
  },

  getMissedQuestionDashboard(): Promise<ApiResponse<AdminMissedQuestionDashboardVO>> {
    return request.get('/admin/missed-questions/dashboard')
  },

  saveConfig(data: {
    configKey: string
    configName: string
    configValue?: string
    valueType?: string
    remark?: string
    status?: string
  }): Promise<ApiResponse<AdminConfigVO>> {
    return request.post('/admin/configs', data)
  }
}
