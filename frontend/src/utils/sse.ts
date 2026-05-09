import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { ChatSendRequest } from '@/types'

const API_BASE = '/api'

export interface SSEOptions {
  onMessage: (text: string) => void
  onError?: (error: Error) => void
  onClose?: () => void
  signal?: AbortSignal
}

export function createSSEConnection(
  request: ChatSendRequest,
  options: SSEOptions
): Promise<void> {
  const ctrl = new AbortController()
  const signal = options.signal || ctrl.signal

  return fetchEventSource(`${API_BASE}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(request),
    signal,
    async onopen(response) {
      if (response.ok) return
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    },
    onmessage(event) {
      if (event.data === '[DONE]') {
        options.onClose?.()
        return
      }
      options.onMessage(event.data)
    },
    onerror(error) {
      options.onError?.(error)
      throw error
    }
  })
}

export function streamChat(request: ChatSendRequest): {
  promise: Promise<void>
  abort: () => void
} {
  const ctrl = new AbortController()
  
  const promise = fetchEventSource(`${API_BASE}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(request),
    signal: ctrl.signal,
    async onopen(response) {
      if (response.ok) return
      throw new Error(`HTTP ${response.status}`)
    },
    onmessage(event) {
      if (event.data === '[DONE]') return
      request as unknown as { onMessage: (text: string) => void }
    }
  })

  return {
    promise,
    abort: () => ctrl.abort()
  }
}
