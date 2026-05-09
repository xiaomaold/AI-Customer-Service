import { request } from '@/utils/request'
import type { ChatMessageVO, ApiResponse } from '@/types'

export const chatApi = {
  getHistory(sessionId: string): Promise<ApiResponse<ChatMessageVO[]>> {
    return request.get(`/chat/sessions/${sessionId}/messages`)
  }
}
