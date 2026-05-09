import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { LoginResponse, UserInfo } from '@/types'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!getAccessToken() && !!user.value)
  const canManageKnowledge = computed(() => hasRole('KB_ADMIN'))
  const canManageAdmin = computed(() => hasRole('ADMIN'))

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await authApi.login({ username, password })
      applyLogin(res.data)
      return res.data
    } finally {
      loading.value = false
    }
  }

  async function fetchCurrentUser() {
    const token = getAccessToken()
    if (!token) {
      user.value = null
      return null
    }
    const res = await authApi.me()
    user.value = res.data
    return res.data
  }

  function applyLogin(payload: LoginResponse) {
    setAccessToken(payload.accessToken)
    user.value = payload.userInfo
  }

  function logout() {
    clearAccessToken()
    user.value = null
  }

  function hasRole(role: string) {
    return !!user.value?.roles.includes(role)
  }

  function hasAnyRole(roles: string[]) {
    return roles.some((role) => hasRole(role))
  }

  return {
    user,
    loading,
    isAuthenticated,
    canManageKnowledge,
    canManageAdmin,
    login,
    fetchCurrentUser,
    logout,
    hasRole,
    hasAnyRole
  }
})
