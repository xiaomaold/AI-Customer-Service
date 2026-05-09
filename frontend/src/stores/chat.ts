import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatApi } from '@/api/chat'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { ChatMessageVO, ChatSendRequest } from '@/types'
import { ElMessage } from 'element-plus'
import { useSessionStore } from './session'
import { useAuthStore } from './auth'
import { getAccessToken } from '@/utils/request'

const SESSION_TITLE_MAX_LENGTH = 20
const DEFAULT_SESSION_TITLE = '新建会话'
const LOCAL_MESSAGE_STORAGE_KEY = 'ai-rag-local-messages'
const TITLE_PREFIXES = ['请问', '帮我', '麻烦帮我', '我想了解', '我想知道', '介绍一下', '说说', '请介绍', '帮我看看']
const WEAK_FOLLOW_UP_PREFIXES = ['再', '继续', '接着', '然后', '顺便', '另外', '那就', '那再']
const WEAK_FOLLOW_UP_PHRASES = [
  '再简短一点',
  '再详细一点',
  '再正式一点',
  '再口语一点',
  '换个说法',
  '换一种说法',
  '重写一下',
  '润色一下',
  '精简一下',
  '补充一下',
  '继续',
  '接着说',
  '展开一点',
  '详细一点',
  '简短一点',
  '口语一点',
  '正式一点',
  '改成表格',
  '改成列表'
]

const DOCUMENT_FOLLOW_UP_KEYWORDS = [
  '这个',
  '这篇',
  '这份',
  '文档',
  '文件',
  '内容',
  '主要',
  '讲了什么',
  '总结',
  '概述',
  '摘要',
  '说了什么'
]

export interface Message extends ChatMessageVO {
  isStreaming?: boolean
  error?: boolean
  corrected?: boolean
  correctionPending?: boolean
  correctionPhase?: 'idle' | 'fade-out' | 'fade-in'
  localOnly?: boolean
  aiDocumentAnalysis?: {
    analysisId: string
    taskType: string
    fileName: string
    suggestedKnowledgeBaseName?: string | null
    suggestedKnowledgeBaseId?: string | null
    suggestedDocumentName: string
    summary: string
    tags: string[]
    recommendedAction?: string | null
    reason?: string | null
    canUpload: boolean
    uploadDeniedReason?: string | null
    status: 'analyzed' | 'uploading' | 'uploaded' | 'failed'
  }
}

function normalizePersistedMessage(message: Message): Message {
  return {
    ...message,
    isStreaming: false,
    corrected: message.corrected ?? false,
    correctionPending: false,
    correctionPhase: 'idle' as const
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<Message[]>([])
  const loading = ref(false)
  const streaming = ref(false)
  const abortController = ref<AbortController | null>(null)

  function readLocalMessageMap(): Record<string, Message[]> {
    try {
      const raw = localStorage.getItem(LOCAL_MESSAGE_STORAGE_KEY)
      return raw ? (JSON.parse(raw) as Record<string, Message[]>) : {}
    } catch {
      return {}
    }
  }

  function writeLocalMessageMap(messageMap: Record<string, Message[]>) {
    localStorage.setItem(LOCAL_MESSAGE_STORAGE_KEY, JSON.stringify(messageMap))
  }

  function getLocalMessages(sessionId: string) {
    return (readLocalMessageMap()[sessionId] || []).map(normalizePersistedMessage)
  }

  function persistLocalMessages(sessionId: string) {
    const messageMap = readLocalMessageMap()
    const localMessages = messages.value
      .filter((message) => message.sessionId === sessionId && message.localOnly)
      .map((message) => ({
        ...message,
        isStreaming: false,
        correctionPending: false,
        correctionPhase: 'idle' as const
      }))

    if (localMessages.length > 0) {
      messageMap[sessionId] = localMessages
    } else {
      delete messageMap[sessionId]
    }
    writeLocalMessageMap(messageMap)
  }

  function mergePersistedLocalMessages(sessionId: string, remoteMessages: Message[]) {
    const localMessages = getLocalMessages(sessionId)
    if (localMessages.length === 0) {
      return remoteMessages
    }

    const merged = [...remoteMessages]
    for (const localMessage of localMessages) {
      if (!merged.some((message) => message.id === localMessage.id)) {
        merged.push(localMessage)
      }
    }

    return merged.sort((left, right) => new Date(left.createTime).getTime() - new Date(right.createTime).getTime())
  }

  function updateStreamingMessage(updater: (message: Message) => void) {
    const lastIndex = messages.value.length - 1
    if (lastIndex < 0) return

    const lastMessage = messages.value[lastIndex]
    if (!lastMessage || lastMessage.role !== 'assistant') return

    updater(lastMessage)
    messages.value.splice(lastIndex, 1, { ...lastMessage })
    if (lastMessage.sessionId && lastMessage.localOnly) {
      persistLocalMessages(lastMessage.sessionId)
    }
  }

  function updateMessageById(messageId: string, updater: (message: Message) => void) {
    const index = messages.value.findIndex((message) => message.id === messageId)
    if (index < 0) return

    const target = messages.value[index]
    updater(target)
    messages.value.splice(index, 1, { ...target })
    if (target.sessionId && target.localOnly) {
      persistLocalMessages(target.sessionId)
    }
  }

  function normalizeContentForCompare(content?: string | null) {
    return (content || '').replace(/\s+/g, ' ').trim()
  }

  function stripTrailingPunctuation(value: string) {
    return value.replace(/[，。；;!！?？]+$/g, '').trim()
  }

  function normalizeQuestion(question: string) {
    let normalized = question.replace(/\s+/g, ' ').replace(/[\r\n]+/g, ' ').trim()
    normalized = stripTrailingPunctuation(normalized)

    for (const prefix of TITLE_PREFIXES) {
      if (normalized.startsWith(prefix) && normalized.length > prefix.length) {
        normalized = normalized.slice(prefix.length).trim()
        break
      }
    }

    return normalized
  }

  function isDefaultTitle(sessionName?: string | null) {
    if (!sessionName) return true
    return sessionName === DEFAULT_SESSION_TITLE || sessionName.startsWith('新会话')
  }

  function isWeakFollowUp(question: string) {
    const normalized = normalizeQuestion(question)
    if (!normalized) return false
    if (normalized.length <= 8 && WEAK_FOLLOW_UP_PREFIXES.some((prefix) => normalized.startsWith(prefix))) {
      return true
    }
    return WEAK_FOLLOW_UP_PHRASES.some((phrase) => normalized.includes(phrase))
  }

  function buildSessionTitle(question: string) {
    const normalized = normalizeQuestion(question)
    if (!normalized) {
      return DEFAULT_SESSION_TITLE
    }
    if (normalized.length <= SESSION_TITLE_MAX_LENGTH) {
      return normalized
    }
    return normalized.slice(0, SESSION_TITLE_MAX_LENGTH) + '...'
  }

  function shouldCarryDocumentContext(question: string) {
    const normalized = normalizeQuestion(question)
    if (!normalized) return false
    if (normalized.length <= 12) {
      return true
    }
    return DOCUMENT_FOLLOW_UP_KEYWORDS.some((keyword) => normalized.includes(keyword))
  }

  function resolveDocumentCarryover(question: string) {
    if (!shouldCarryDocumentContext(question)) {
      return null
    }

    const startIndex = Math.max(0, messages.value.length - 6)
    for (let index = messages.value.length - 1; index >= startIndex; index -= 1) {
      const analysis = messages.value[index].aiDocumentAnalysis
      if (!analysis || analysis.status === 'failed') {
        continue
      }
      return {
        carryoverKnowledgeBaseName: analysis.suggestedKnowledgeBaseName || undefined,
        carryoverDocumentName: analysis.suggestedDocumentName
      }
    }

    return null
  }

  async function fetchHistory(sessionId: string) {
    loading.value = true
    messages.value = []
    try {
      const res = await chatApi.getHistory(sessionId)
      const remoteMessages = (res.data || []).map((message) => ({
        ...message,
        isStreaming: false,
        corrected: false,
        correctionPending: false,
        correctionPhase: 'idle' as const
      }))
      messages.value = mergePersistedLocalMessages(sessionId, remoteMessages)
    } catch {
      ElMessage.error('获取历史消息失败')
    } finally {
      loading.value = false
    }
  }

  function sendMessage(question: string, knowledgeBaseId?: string) {
    const sessionStore = useSessionStore()
    const authStore = useAuthStore()
    const sessionId = sessionStore.currentSessionId

    if (!sessionId) {
      ElMessage.warning('请先选择或创建会话')
      return
    }
    if (!authStore.user) {
      ElMessage.warning('登录状态已失效，请重新登录')
      return
    }

    const userMsg: Message = {
      id: String(Date.now()),
      sessionId,
      userId: authStore.user.userId,
      role: 'user',
      content: question,
      referenceContent: null,
      modelName: null,
      tokenCount: null,
      createTime: new Date().toISOString()
    }
    messages.value.push(userMsg)

    if (!(isWeakFollowUp(question) && !isDefaultTitle(sessionStore.currentSession?.sessionName))) {
      sessionStore.setSessionTitle(sessionId, buildSessionTitle(question))
    }

    const aiMsg: Message = {
      id: String(Date.now() + 1),
      sessionId,
      userId: authStore.user.userId,
      role: 'assistant',
      content: '',
      referenceContent: null,
      modelName: null,
      tokenCount: null,
      createTime: new Date().toISOString(),
      isStreaming: true,
      corrected: false,
      correctionPending: false,
      correctionPhase: 'idle'
    }
    messages.value.push(aiMsg)

    streaming.value = true
    abortController.value = new AbortController()

    const documentCarryover = resolveDocumentCarryover(question)
    const request: ChatSendRequest = {
      sessionId,
      knowledgeBaseId,
      question,
      carryoverKnowledgeBaseName: documentCarryover?.carryoverKnowledgeBaseName,
      carryoverDocumentName: documentCarryover?.carryoverDocumentName
    }

    fetchEventSource('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${getAccessToken()}`
      },
      body: JSON.stringify(request),
      signal: abortController.value.signal,
      async onopen(response) {
        if (response.ok) return
        throw new Error(`HTTP ${response.status}`)
      },
      onmessage(event) {
        if (event.event === 'replace') {
          updateStreamingMessage((message) => {
            const nextContent = event.data || ''
            const changed = normalizeContentForCompare(message.content) !== normalizeContentForCompare(nextContent)
            if (!changed) return

            message.corrected = true
            message.correctionPending = true
            message.correctionPhase = 'fade-out'
            const messageId = message.id

            window.setTimeout(() => {
              updateMessageById(messageId, (target) => {
                target.content = nextContent
                target.correctionPhase = 'fade-in'
              })
            }, 180)

            window.setTimeout(() => {
              updateMessageById(messageId, (target) => {
                target.correctionPending = false
                target.correctionPhase = 'idle'
              })
            }, 560)
          })
          return
        }

        if (event.event === 'reference') {
          updateStreamingMessage((message) => {
            message.referenceContent = event.data || null
          })
          return
        }

        if (event.event === 'done' || event.data === '[DONE]') {
          updateStreamingMessage((message) => {
            message.isStreaming = false
          })
          streaming.value = false
          return
        }

        if (event.event === 'error') {
          updateStreamingMessage((message) => {
            message.isStreaming = false
            message.error = true
          })
          streaming.value = false
          ElMessage.error(event.data || '消息发送失败')
          return
        }

        updateStreamingMessage((message) => {
          message.content += event.data
        })
      },
      onerror(error) {
        updateStreamingMessage((message) => {
          message.isStreaming = false
          message.error = true
        })
        streaming.value = false
        ElMessage.error('消息发送失败：' + error.message)
      }
    }).catch(() => {
      updateStreamingMessage((message) => {
        message.isStreaming = false
      })
      streaming.value = false
    })
  }

  function stopStreaming() {
    if (!abortController.value) return

    abortController.value.abort()
    streaming.value = false
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.isStreaming) {
      lastMsg.isStreaming = false
    }
  }

  function clearMessages() {
    const sessionId = useSessionStore().currentSessionId
    messages.value = []
    if (sessionId) {
      persistLocalMessages(sessionId)
    }
  }

  function appendLocalMessage(message: Message) {
    messages.value.push(message)
    persistLocalMessages(message.sessionId)
  }

  function replaceLocalMessage(messageId: string, updater: (message: Message) => void) {
    updateMessageById(messageId, updater)
  }

  function reset() {
    messages.value = []
    loading.value = false
    streaming.value = false
    abortController.value = null
  }

  return {
    messages,
    loading,
    streaming,
    fetchHistory,
    sendMessage,
    stopStreaming,
    clearMessages,
    appendLocalMessage,
    replaceLocalMessage,
    reset
  }
})
