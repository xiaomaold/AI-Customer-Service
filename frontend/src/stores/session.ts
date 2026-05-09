import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sessionApi } from '@/api/session'
import type { CreateSessionRequest, SessionVO } from '@/types'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<SessionVO[]>([])
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)

  const currentSession = computed(() => sessions.value.find((session) => session.id === currentSessionId.value))

  async function fetchSessions() {
    loading.value = true
    try {
      const res = await sessionApi.list()
      sessions.value = res.data || []
      if (sessions.value.length > 0 && !currentSessionId.value) {
        currentSessionId.value = sessions.value[0].id
      }
    } catch {
      ElMessage.error('获取会话列表失败')
    } finally {
      loading.value = false
    }
  }

  async function createSession(name?: string) {
    try {
      const data: CreateSessionRequest = {
        sessionName: name || `新会话 ${sessions.value.length + 1}`
      }
      const res = await sessionApi.create(data)
      sessions.value.unshift(res.data)
      currentSessionId.value = res.data.id
      ElMessage.success('创建成功')
    } catch {
      ElMessage.error('创建会话失败')
    }
  }

  async function renameSession(id: string, sessionName: string) {
    try {
      const res = await sessionApi.rename(id, sessionName.trim())
      replaceSession(res.data)
      ElMessage.success('会话已重命名')
    } catch {
      ElMessage.error('重命名失败')
      throw new Error('rename failed')
    }
  }

  async function togglePinSession(id: string) {
    try {
      const res = await sessionApi.togglePin(id)
      replaceSession(res.data)
      reorderSessions()
      ElMessage.success(res.data?.pinned === 1 ? '已置顶' : '已取消置顶')
    } catch {
      ElMessage.error('置顶操作失败')
    }
  }

  async function deleteSession(id: string) {
    try {
      await sessionApi.delete(id)
      sessions.value = sessions.value.filter((session) => session.id !== id)
      if (currentSessionId.value === id) {
        currentSessionId.value = sessions.value[0]?.id || null
      }
      ElMessage.success('删除成功')
    } catch {
      ElMessage.error('删除会话失败')
    }
  }

  function setCurrentSession(id: string) {
    currentSessionId.value = id
  }

  function setSessionTitle(id: string, sessionName: string) {
    const target = sessions.value.find((session) => session.id === id)
    if (target) {
      target.sessionName = sessionName
    }
  }

  function isPinned(id: string) {
    return sessions.value.find((session) => session.id === id)?.pinned === 1
  }

  function reset() {
    sessions.value = []
    currentSessionId.value = null
    loading.value = false
  }

  function replaceSession(nextSession: SessionVO) {
    const index = sessions.value.findIndex((session) => session.id === nextSession.id)
    if (index === -1) {
      sessions.value.unshift(nextSession)
    } else {
      sessions.value.splice(index, 1, nextSession)
    }
  }

  function reorderSessions() {
    sessions.value = [...sessions.value].sort((a, b) => {
      const aPinned = a.pinned === 1 ? 1 : 0
      const bPinned = b.pinned === 1 ? 1 : 0
      if (aPinned !== bPinned) return bPinned - aPinned

      const aTime = new Date(a.lastMessageTime || a.createTime).getTime()
      const bTime = new Date(b.lastMessageTime || b.createTime).getTime()
      return bTime - aTime
    })
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    loading,
    fetchSessions,
    createSession,
    renameSession,
    togglePinSession,
    deleteSession,
    setCurrentSession,
    setSessionTitle,
    isPinned,
    reset
  }
})
