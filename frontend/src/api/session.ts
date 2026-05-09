import { request } from '@/utils/request'
import type { SessionVO, CreateSessionRequest, ApiResponse } from '@/types'

export const sessionApi = {
  create(data: CreateSessionRequest): Promise<ApiResponse<SessionVO>> {
    return request.post('/chat/sessions', data)
  },

  list(): Promise<ApiResponse<SessionVO[]>> {
    return request.get('/chat/sessions')
  },

  rename(sessionId: string, sessionName: string): Promise<ApiResponse<SessionVO>> {
    return request.put(`/chat/sessions/${sessionId}/name`, { sessionName })
  },

  togglePin(sessionId: string): Promise<ApiResponse<SessionVO>> {
    return request.put(`/chat/sessions/${sessionId}/pin`)
  },

  delete(sessionId: string): Promise<ApiResponse<void>> {
    return request.delete(`/chat/sessions/${sessionId}`)
  }
}
