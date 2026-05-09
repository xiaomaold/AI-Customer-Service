import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getAccessToken } from '@/utils/request'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const token = getAccessToken()

  if (to.meta.public) {
    if (token) {
      if (!authStore.user) {
        try {
          await authStore.fetchCurrentUser()
        } catch {
          authStore.logout()
        }
      }
      if (authStore.user) {
        return '/'
      }
    }
    return true
  }

  if (!token) {
    return '/login'
  }

  if (!authStore.user) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      authStore.logout()
      return '/login'
    }
  }

  return true
})

export default router
