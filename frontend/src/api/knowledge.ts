import { request } from '@/utils/request'
import type {
  AiDocumentAnalyzeResponse,
  AiDocumentUploadConfirmRequest,
  ApiResponse,
  KnowledgeBaseVO,
  KnowledgeChunkVO,
  KnowledgeDocVO,
  KnowledgeDocumentUploadResponse,
  KnowledgePermissionGrantVO,
  KnowledgePermissionUserVO,
  UserKnowledgePermissionVO
} from '@/types'

export const knowledgeApi = {
  listBases(): Promise<ApiResponse<KnowledgeBaseVO[]>> {
    return request.get('/knowledge/bases')
  },

  getBaseDetail(id: string): Promise<ApiResponse<KnowledgeBaseVO>> {
    return request.get(`/knowledge/bases/${id}`)
  },

  myPermissions(): Promise<ApiResponse<UserKnowledgePermissionVO[]>> {
    return request.get('/knowledge/bases/my-permissions')
  },

  listAssignableUsers(): Promise<ApiResponse<KnowledgePermissionUserVO[]>> {
    return request.get('/knowledge/permissions/users')
  },

  listBasePermissions(knowledgeBaseId: string): Promise<ApiResponse<KnowledgePermissionGrantVO[]>> {
    return request.get(`/knowledge/bases/${knowledgeBaseId}/permissions`)
  },

  grantBasePermission(knowledgeBaseId: string, data: { userId: string; permissionType: string }): Promise<ApiResponse<void>> {
    return request.post(`/knowledge/bases/${knowledgeBaseId}/permissions`, data)
  },

  revokeBasePermission(knowledgeBaseId: string, userId: string): Promise<ApiResponse<void>> {
    return request.delete(`/knowledge/bases/${knowledgeBaseId}/permissions/${userId}`)
  },

  createBase(data: { knowledgeBaseName: string; description?: string }): Promise<ApiResponse<KnowledgeBaseVO>> {
    return request.post('/knowledge/bases', data)
  },

  deleteBase(id: string): Promise<ApiResponse<void>> {
    return request.delete(`/knowledge/bases/${id}`)
  },

  listDocuments(knowledgeBaseId?: string): Promise<ApiResponse<KnowledgeDocVO[]>> {
    const url = knowledgeBaseId
      ? `/knowledge/documents?knowledgeBaseId=${knowledgeBaseId}`
      : '/knowledge/documents'
    return request.get(url)
  },

  listChunks(documentId: string): Promise<ApiResponse<KnowledgeChunkVO[]>> {
    return request.get(`/knowledge/documents/${documentId}/chunks`)
  },

  uploadDocument(knowledgeBaseId: string, file: File): Promise<ApiResponse<KnowledgeDocumentUploadResponse>> {
    const formData = new FormData()
    formData.append('file', file)
    return request.post(`/knowledge/documents/upload?knowledgeBaseId=${knowledgeBaseId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  analyzeDocumentWithAi(file: File, knowledgeBaseId?: string): Promise<ApiResponse<AiDocumentAnalyzeResponse>> {
    const formData = new FormData()
    formData.append('file', file)
    if (knowledgeBaseId) {
      formData.append('knowledgeBaseId', knowledgeBaseId)
    }
    return request.post('/knowledge/documents/ai/analyze', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  executeDocumentTaskWithAi(file: File, instruction: string, knowledgeBaseId?: string): Promise<ApiResponse<AiDocumentAnalyzeResponse>> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('instruction', instruction)
    if (knowledgeBaseId) {
      formData.append('knowledgeBaseId', knowledgeBaseId)
    }
    return request.post('/knowledge/documents/ai/execute', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  confirmAiUpload(data: AiDocumentUploadConfirmRequest): Promise<ApiResponse<KnowledgeDocumentUploadResponse>> {
    return request.post('/knowledge/documents/ai/confirm-upload', data)
  },

  deleteDocument(id: string): Promise<ApiResponse<void>> {
    return request.delete(`/knowledge/documents/${id}`)
  }
}
