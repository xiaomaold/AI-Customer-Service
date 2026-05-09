export interface SessionVO {
  id: string
  sessionName: string
  userId: number
  sessionStatus: string
  pinned?: number
  lastMessageTime: string
  createTime: string
}

export interface ChatMessageVO {
  id: string
  sessionId: string
  userId: number
  role: 'user' | 'assistant'
  content: string
  referenceContent: string | null
  modelName: string | null
  tokenCount: number | null
  createTime: string
}

export interface KnowledgeDocVO {
  id: string
  knowledgeBaseId: string
  documentName: string
  fileName: string
  fileExt: string
  contentType: string
  fileSize: number
  parseStatus: string
  chunkCount: number
  embeddingModel: string | null
  remark: string | null
  createTime: string
}

export interface KnowledgeBaseVO {
  id: string
  knowledgeBaseName: string
  description: string | null
  status: string
  documentCount: number
  createTime: string
}

export interface KnowledgeChunkVO {
  id: string
  knowledgeBaseId: string
  documentId: string
  chunkIndex: number
  fileName: string
  content: string
}

export interface KnowledgePermissionUserVO {
  userId: string
  username: string
  nickname: string
  roles: string[]
}

export interface KnowledgePermissionGrantVO {
  userId: string
  username: string
  nickname: string
  roles: string[]
  permissionType: string
  grantedAt: string
}

export interface UserKnowledgePermissionVO {
  knowledgeBaseId: string
  knowledgeBaseName: string
  description: string | null
  permissionType: string
  status: string
}

export interface ChatSendRequest {
  sessionId: string
  knowledgeBaseId?: string
  question: string
  carryoverKnowledgeBaseName?: string
  carryoverDocumentName?: string
}

export interface CreateSessionRequest {
  sessionName?: string
}

export interface KnowledgeDocumentUploadResponse {
  documentId: string
  knowledgeBaseId: string
  documentName: string
  chunkCount: number
  parseStatus: string
}

export interface AiDocumentAnalyzeResponse {
  analysisId: string
  taskType: string
  answer: string
  suggestedKnowledgeBaseId?: string | null
  suggestedKnowledgeBaseName?: string | null
  suggestedDocumentName: string
  summary: string
  tags: string[]
  recommendedAction: string
  reason: string
  canUpload: boolean
  uploadDeniedReason?: string | null
}

export interface AiDocumentUploadConfirmRequest {
  analysisId: string
  knowledgeBaseId?: string
  documentName?: string
}

export interface UserInfo {
  userId: number
  username: string
  nickname: string
  roles: string[]
}

export interface AdminUserVO {
  userId: string
  username: string
  nickname: string | null
  realName: string | null
  mobile: string | null
  email: string | null
  status: string
  lastLoginTime: string | null
  createTime: string
  roles: string[]
  roleIds: string[]
}

export interface AdminRoleVO {
  roleId: string
  roleCode: string
  roleName: string
  status: string
  remark: string | null
  createTime: string
}

export interface AdminConfigVO {
  configId: string
  configKey: string
  configName: string
  configValue: string | null
  valueType: string
  remark: string | null
  status: string
  updateTime: string
}

export interface AdminMissedQuestionVO {
  missedQuestionId: string
  userId: number
  sessionId: string
  knowledgeBaseId: string | null
  routeMode: string
  question: string
  answer: string
  missReason: string
  status: string
  createTime: string
}

export interface AdminMissedQuestionDashboardVO {
  totalCount: number
  openCount: number
  todayCount: number
  lastSevenDaysCount: number
  routeModeCounts: {
    key: string
    count: number
  }[]
  missReasonCounts: {
    key: string
    count: number
  }[]
  recentMissedQuestions: AdminMissedQuestionVO[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
