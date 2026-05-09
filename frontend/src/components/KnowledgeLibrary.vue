<template>
  <div class="knowledge-library">
    <div class="library-sidebar" v-loading="loadingPermissions">
      <div class="sidebar-title">我的知识库</div>
      <div
        v-for="item in permissions"
        :key="item.knowledgeBaseId"
        :class="['kb-item', { active: item.knowledgeBaseId === selectedKnowledgeBaseId }]"
        @click="handleSelectKnowledgeBase(item.knowledgeBaseId)"
      >
        <div class="kb-item-name">{{ item.knowledgeBaseName }}</div>
        <div class="kb-item-meta">
          <el-tag size="small" :type="permissionTagType(item.permissionType)">
            {{ permissionText(item.permissionType) }}
          </el-tag>
        </div>
      </div>
      <el-empty v-if="!loadingPermissions && permissions.length === 0" description="暂无可访问知识库" />
    </div>

    <div class="library-main">
      <template v-if="selectedKnowledgeBase">
        <div class="library-header">
          <div>
            <div class="library-title">{{ selectedKnowledgeBase.knowledgeBaseName }}</div>
            <div class="library-desc">{{ selectedKnowledgeBase.description || '暂无描述' }}</div>
          </div>
          <div class="library-stats">
            <span>文档数 {{ documents.length }}</span>
          </div>
        </div>

        <div class="library-content">
          <div class="document-column">
            <div class="column-title">文档列表</div>
            <div class="document-list" v-loading="loadingDocuments">
              <div
                v-for="doc in documents"
                :key="doc.id"
                :class="['document-item', { active: doc.id === selectedDocumentId }]"
                @click="handleSelectDocument(doc.id)"
              >
                <div class="document-name">{{ doc.documentName }}</div>
                <div class="document-meta">{{ doc.fileExt?.toUpperCase() || 'FILE' }} · {{ doc.chunkCount }} 分块</div>
              </div>
              <el-empty v-if="!loadingDocuments && documents.length === 0" description="暂无文档" />
            </div>
          </div>

          <div class="chunk-column">
            <div class="column-title">
              文档内容
              <span v-if="selectedDocument" class="column-subtitle">{{ selectedDocument.documentName }}</span>
            </div>
            <div class="chunk-list" v-loading="loadingChunks">
              <div v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
                <div class="chunk-index">分块 {{ chunk.chunkIndex + 1 }}</div>
                <div class="chunk-content">{{ chunk.content }}</div>
              </div>
              <el-empty v-if="!loadingChunks && chunks.length === 0" description="选择文档后查看内容分块" />
            </div>
          </div>
        </div>
      </template>

      <div v-else class="library-empty">
        <el-empty description="选择左侧知识库查看内容" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { knowledgeApi } from '@/api/knowledge'
import type { KnowledgeBaseVO, KnowledgeChunkVO, KnowledgeDocVO, UserKnowledgePermissionVO } from '@/types'

const loadingPermissions = ref(false)
const loadingDocuments = ref(false)
const loadingChunks = ref(false)

const permissions = ref<UserKnowledgePermissionVO[]>([])
const selectedKnowledgeBaseId = ref<string>('')
const selectedKnowledgeBase = ref<KnowledgeBaseVO | null>(null)
const documents = ref<KnowledgeDocVO[]>([])
const selectedDocumentId = ref<string>('')
const chunks = ref<KnowledgeChunkVO[]>([])

const selectedDocument = computed(() =>
  documents.value.find((doc) => doc.id === selectedDocumentId.value) || null
)

onMounted(() => {
  loadPermissions()
})

async function loadPermissions() {
  loadingPermissions.value = true
  try {
    const res = await knowledgeApi.myPermissions()
    permissions.value = res.data || []
    if (permissions.value.length > 0) {
      await handleSelectKnowledgeBase(permissions.value[0].knowledgeBaseId)
    }
  } finally {
    loadingPermissions.value = false
  }
}

async function handleSelectKnowledgeBase(id: string) {
  if (!id) return
  selectedKnowledgeBaseId.value = id
  selectedDocumentId.value = ''
  chunks.value = []
  await Promise.all([loadKnowledgeBaseDetail(id), loadDocuments(id)])
}

async function loadKnowledgeBaseDetail(id: string) {
  const res = await knowledgeApi.getBaseDetail(id)
  selectedKnowledgeBase.value = res.data
}

async function loadDocuments(knowledgeBaseId: string) {
  loadingDocuments.value = true
  try {
    const res = await knowledgeApi.listDocuments(knowledgeBaseId)
    documents.value = res.data || []
    if (documents.value.length > 0) {
      await handleSelectDocument(documents.value[0].id)
    }
  } finally {
    loadingDocuments.value = false
  }
}

async function handleSelectDocument(documentId: string) {
  selectedDocumentId.value = documentId
  loadingChunks.value = true
  try {
    const res = await knowledgeApi.listChunks(documentId)
    chunks.value = res.data || []
  } finally {
    loadingChunks.value = false
  }
}

function permissionText(permissionType: string) {
  const map: Record<string, string> = {
    MANAGE: '管理',
    WRITE: '编辑',
    READ: '查看'
  }
  return map[permissionType] || permissionType
}

function permissionTagType(permissionType: string): 'warning' | 'primary' | 'success' | 'info' {
  const map: Record<string, 'warning' | 'primary' | 'success' | 'info'> = {
    MANAGE: 'warning',
    WRITE: 'primary',
    READ: 'success'
  }
  return map[permissionType] || 'info'
}
</script>

<style lang="scss" scoped>
.knowledge-library {
  height: 70vh;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
}

.library-sidebar,
.library-main {
  min-height: 0;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--app-shadow-sm);
}

.library-sidebar {
  padding: 16px;
  overflow-y: auto;
  background: #f8fafc;
}

.sidebar-title,
.column-title {
  font-size: 16px;
  font-weight: 700;
}

.sidebar-title {
  color: var(--app-text);
}

.kb-item {
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.82);
  cursor: pointer;
  transition: all 0.2s ease;
}

.kb-item:hover,
.kb-item.active {
  border-color: rgba(126, 163, 203, 0.24);
  background: #edf5ff;
  transform: translateY(-1px);
}

.kb-item-name {
  font-weight: 600;
  color: var(--app-text);
}

.kb-item-meta {
  margin-top: 8px;
}

.library-main {
  display: flex;
  flex-direction: column;
  padding: 18px;
}

.library-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 2px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.library-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text);
}

.library-desc,
.library-stats,
.column-subtitle,
.document-meta {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.library-content {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  padding-top: 16px;
}

.document-column,
.chunk-column {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.column-title {
  color: var(--app-text);
}

.document-list,
.chunk-list {
  margin-top: 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 18px;
  padding: 12px;
  background: #fbfcfe;
}

.document-item {
  padding: 12px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.document-item:hover,
.document-item.active {
  background: rgba(111, 151, 194, 0.08);
}

.document-name {
  font-weight: 600;
  color: var(--app-text);
}

.chunk-item {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: #fff;
}

.chunk-item + .chunk-item {
  margin-top: 10px;
}

.chunk-index {
  font-size: 12px;
  font-weight: 700;
  color: var(--app-primary);
}

.chunk-content {
  margin-top: 8px;
  line-height: 1.7;
  color: #25384c;
  white-space: pre-wrap;
  word-break: break-word;
}

.library-empty {
  flex: 1;
  display: grid;
  place-items: center;
}

@media (max-width: 960px) {
  .knowledge-library,
  .library-content {
    grid-template-columns: 1fr;
  }

  .knowledge-library {
    height: auto;
    min-height: 70vh;
  }
}
</style>
