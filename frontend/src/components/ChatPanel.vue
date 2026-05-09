<template>
  <div
    class="chat-panel"
    :class="{ 'is-dragover': dragOver }"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragOver"
    @dragleave.prevent="handlePanelDragLeave"
    @drop.prevent="handleDrop"
  >
    <div class="mode-bar">
      <el-dropdown trigger="click" @command="handleModeCommand">
        <button type="button" class="mode-trigger">
          <span class="mode-trigger__title">{{ currentModeLabel }}</span>
          <el-icon class="mode-trigger__arrow"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu class="mode-menu">
            <el-dropdown-item command="daily">
              <div class="mode-menu__item">
                <div class="mode-menu__title">日常模式</div>
                <div class="mode-menu__desc">适用于普通日常聊天对话</div>
              </div>
            </el-dropdown-item>
            <el-dropdown-item command="work">
              <div class="mode-menu__item">
                <div class="mode-menu__title">工作模式</div>
                <div class="mode-menu__desc">优先进行企业知识库检索</div>
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <div ref="messageListRef" class="message-list">
      <div class="message-shell">
        <div v-if="chatStore.loading" class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <span>加载中...</span>
        </div>

        <template v-else>
          <MessageBubble
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :message="msg"
            @use-question="handleSuggestedQuestion"
            @confirm-upload="handleConfirmUpload"
          />

          <div v-if="chatStore.messages.length === 0" class="empty-container">
            <div class="empty-hero">
              <span class="empty-badge">{{ currentModeLabel }}</span>
              <div class="empty-title">
                {{ chatMode === 'work' ? '围绕企业知识库进行业务问答与文档处理' : '适合普通聊天、闲聊与通用问题对话' }}
              </div>
              <div class="empty-description">
                {{
                  chatMode === 'work'
                    ? '工作模式下会优先结合企业知识库进行检索问答，更适合业务场景与资料型提问。'
                    : '日常模式更适合自由对话、开放问答和一般信息交流，不会优先走知识库检索。'
                }}
              </div>
            </div>

            <div class="quick-questions">
              <button
                v-for="question in quickQuestions"
                :key="question"
                type="button"
                class="quick-question"
                @click="handleSuggestedQuestion(question)"
              >
                <span class="quick-question__label">推荐提问</span>
                <span class="quick-question__text">{{ question }}</span>
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div v-if="dragOver" class="drag-overlay">
      <div class="drag-overlay__title">拖拽文档到这里</div>
      <div class="drag-overlay__desc">
        支持 PDF、DOC、DOCX、TXT、MD、MARKDOWN。松手后文档会先挂到输入区，发送时再按你的指令处理。
      </div>
    </div>

    <div class="input-area">
      <div class="input-card">
        <input
          ref="fileInputRef"
          type="file"
          class="hidden-file-input"
          accept=".pdf,.doc,.docx,.txt,.md,.markdown"
          @change="handleFileChange"
        />

        <div v-if="pendingFile" class="pending-file-bar">
          <div class="pending-file-info">
            <div class="pending-file-name">{{ pendingFile.name }}</div>
            <div class="pending-file-tip">
              文档已加入输入区。你可以继续输入要求，比如“总结这份文档”“提取电话和邮箱”“判断适合归入哪个知识库”。
            </div>
          </div>
          <button type="button" class="pending-file-remove" @click="clearPendingFile">
            移除
          </button>
        </div>

        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入你的问题或处理要求，支持 Ctrl + Enter 发送。"
            :disabled="chatStore.streaming"
            @keydown.enter.ctrl="handleSend"
          />
          <div class="action-slot">
            <el-button
              v-if="chatStore.streaming"
              class="stop-button"
              circle
              @click="handleStop"
            >
              停
            </el-button>
            <el-button
              v-else-if="showSendButton"
              class="send-button"
              type="primary"
              circle
              @click="handleSend"
            >
              <el-icon><Top /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="input-footer">
          <div class="footer-tools">
            <el-dropdown trigger="click" @command="handlePlusCommand">
              <button type="button" class="plus-trigger" :disabled="processingDocument">
                <span>+</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="upload" :disabled="processingDocument">
                    {{ processingDocument ? '处理中...' : '上传文档' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <button
              type="button"
              class="tool-link"
              :disabled="processingDocument"
              @click="openFilePicker"
            >
              {{ processingDocument ? '处理中...' : '上传文档' }}
            </button>
            <span v-if="chatMode === 'work'" class="tool-divider"></span>
            <span v-if="chatMode === 'work'" class="tool-meta">
              {{ selectedKnowledgeBaseName || '自动选择知识库' }}
            </span>
          </div>

          <div class="mode-hint">
            {{
              chatMode === 'work'
                ? (selectedKnowledgeBaseName
                  ? `当前为工作模式，将优先检索“${selectedKnowledgeBaseName}”。`
                  : '当前为工作模式，将优先检索可用企业知识库。')
                : '当前为日常模式，适用于普通聊天与通用问题对话。'
            }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ArrowDown, Loading, Top } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api/knowledge'
import { useChatStore, type Message } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { useAuthStore } from '@/stores/auth'
import MessageBubble from './MessageBubble.vue'
import type { AiDocumentAnalyzeResponse, KnowledgeBaseVO } from '@/types'

const chatStore = useChatStore()
const sessionStore = useSessionStore()
const authStore = useAuthStore()

const quickQuestions = [
  '介绍一下退款与售后政策',
  '总结这份实验报告的核心内容',
  '目前有哪些知识库可以查看',
  '产品报价流程是怎么样的',
  '客服处理规范有哪些重点'
]

const inputText = ref('')
const chatMode = ref<'daily' | 'work'>('daily')
const selectedKnowledgeBase = ref<string | undefined>()
const knowledgeBases = ref<KnowledgeBaseVO[]>([])
const messageListRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const processingDocument = ref(false)
const pendingFile = ref<File | null>(null)
let dragDepth = 0

const selectedKnowledgeBaseName = computed(() => {
  return knowledgeBases.value.find((kb) => kb.id === selectedKnowledgeBase.value)?.knowledgeBaseName || ''
})

const activeKnowledgeBaseId = computed(() => {
  return chatMode.value === 'work' ? selectedKnowledgeBase.value : undefined
})

const currentModeLabel = computed(() => {
  return chatMode.value === 'work' ? '工作模式' : '日常模式'
})

const showSendButton = computed(() => {
  return !!inputText.value.trim() || !!pendingFile.value
})

watch(
  () => sessionStore.currentSessionId,
  (id) => {
    if (id) {
      chatStore.fetchHistory(id)
    }
  },
  { immediate: true }
)

watch(
  () => chatStore.messages.length,
  () => {
    nextTick(() => {
      scrollToBottom()
    })
  }
)

onMounted(() => {
  loadKnowledgeBases()
})

function setChatMode(mode: 'daily' | 'work') {
  chatMode.value = mode
}

function handleModeCommand(command: string) {
  setChatMode(command === 'work' ? 'work' : 'daily')
}

async function loadKnowledgeBases() {
  try {
    const res = await knowledgeApi.listBases()
    knowledgeBases.value = res.data || []
    if (!selectedKnowledgeBase.value && knowledgeBases.value.length > 0) {
      selectedKnowledgeBase.value = knowledgeBases.value[0].id
    }
  } catch {
    knowledgeBases.value = []
  }
}

function sendQuestion(text: string) {
  const question = text.trim()
  if (!question || chatStore.streaming) return

  chatStore.sendMessage(question, activeKnowledgeBaseId.value)
  inputText.value = ''
}

function handleSend() {
  if (pendingFile.value) {
    void handleSendWithDocument()
    return
  }
  sendQuestion(inputText.value)
}

function handleSuggestedQuestion(question: string) {
  sendQuestion(question)
}

function handleStop() {
  chatStore.stopStreaming()
}

function openFilePicker() {
  if (processingDocument.value) return
  fileInputRef.value?.click()
}

function handlePlusCommand(command: string) {
  if (command === 'upload') {
    openFilePicker()
  }
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    attachPendingFile(file)
  }
  input.value = ''
}

function hasFiles(event: DragEvent) {
  return Array.from(event.dataTransfer?.types || []).includes('Files')
}

function handleDragEnter(event: DragEvent) {
  if (!hasFiles(event)) return
  dragDepth += 1
  dragOver.value = true
}

function handleDragOver(event: DragEvent) {
  if (!hasFiles(event)) return
  event.dataTransfer!.dropEffect = 'copy'
  dragOver.value = true
}

function handlePanelDragLeave(event: DragEvent) {
  if (!hasFiles(event)) return
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) {
    dragOver.value = false
  }
}

function handleDrop(event: DragEvent) {
  dragDepth = 0
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    attachPendingFile(file)
  }
}

function isSupportedDocument(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  return ['pdf', 'doc', 'docx', 'txt', 'md', 'markdown'].includes(extension)
}

function buildLocalMessageId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function attachPendingFile(file: File) {
  if (!isSupportedDocument(file)) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX、TXT、MD、MARKDOWN 文档。')
    return
  }
  pendingFile.value = file
  ElMessage.success('文档已加入输入区')
}

function clearPendingFile() {
  pendingFile.value = null
}

async function executeDocumentTask(file: File, instruction: string) {
  if (processingDocument.value) return
  if (!isSupportedDocument(file)) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX、TXT、MD、MARKDOWN 文档。')
    return
  }
  if (!sessionStore.currentSessionId || !authStore.user) {
    ElMessage.warning('请先进入一个会话后再上传文档。')
    return
  }

  const effectiveInstruction = instruction.trim() || `请分析文档《${file.name}》并给出处理建议`
  sessionStore.setSessionTitle(
    sessionStore.currentSessionId,
    buildAttachmentSessionTitle(effectiveInstruction, file.name)
  )

  const userMessageId = buildLocalMessageId('file-user')
  chatStore.appendLocalMessage({
    id: userMessageId,
    sessionId: sessionStore.currentSessionId,
    userId: authStore.user.userId,
    role: 'user',
    content: effectiveInstruction,
    referenceContent: null,
    modelName: null,
    tokenCount: null,
    createTime: new Date().toISOString(),
    localOnly: true
  })

  const assistantMessageId = buildLocalMessageId('file-ai')
  chatStore.appendLocalMessage({
    id: assistantMessageId,
    sessionId: sessionStore.currentSessionId,
    userId: authStore.user.userId,
    role: 'assistant',
    content: '正在按你的指令处理文档，请稍候。',
    referenceContent: null,
    modelName: null,
    tokenCount: null,
    createTime: new Date().toISOString(),
    isStreaming: true,
    localOnly: true
  })

  processingDocument.value = true
  try {
    const res = await knowledgeApi.executeDocumentTaskWithAi(file, effectiveInstruction, activeKnowledgeBaseId.value)
    applyAnalysisMessage(assistantMessageId, file, res.data)
  } catch {
    chatStore.replaceLocalMessage(assistantMessageId, (message) => {
      message.isStreaming = false
      message.error = true
      message.content = '文档 AI 处理失败，请稍后重试。'
    })
  } finally {
    processingDocument.value = false
  }
}

function buildAttachmentSessionTitle(userText: string, fileName: string) {
  const base = (userText || fileName).replace(/\s+/g, ' ').trim()
  if (!base) return '文档处理'
  return base.length <= 20 ? base : `${base.slice(0, 20)}...`
}

async function handleSendWithDocument() {
  const file = pendingFile.value
  if (!file) return
  const userText = inputText.value.trim()
  pendingFile.value = null
  inputText.value = ''
  await executeDocumentTask(file, userText)
}

function applyAnalysisMessage(messageId: string, file: File, analysis: AiDocumentAnalyzeResponse) {
  const baseContent = analysis.answer || (
    analysis.canUpload
      ? `已完成文档处理，推荐上传到“${analysis.suggestedKnowledgeBaseName || '未识别知识库'}”。`
      : '已完成文档处理。'
  )

  chatStore.replaceLocalMessage(messageId, (message) => {
    message.isStreaming = false
    message.content = baseContent
    message.aiDocumentAnalysis = {
      analysisId: analysis.analysisId,
      taskType: analysis.taskType,
      fileName: file.name,
      suggestedKnowledgeBaseId: analysis.suggestedKnowledgeBaseId || null,
      suggestedKnowledgeBaseName: analysis.suggestedKnowledgeBaseName || null,
      suggestedDocumentName: analysis.suggestedDocumentName,
      summary: analysis.summary,
      tags: analysis.tags || [],
      recommendedAction: analysis.recommendedAction || null,
      reason: analysis.reason || null,
      canUpload: analysis.canUpload,
      uploadDeniedReason: analysis.uploadDeniedReason || null,
      status: 'analyzed'
    }
  })
}

async function handleConfirmUpload(messageId: string) {
  const targetMessage = chatStore.messages.find((message) => message.id === messageId) as Message | undefined
  const analysis = targetMessage?.aiDocumentAnalysis
  if (!targetMessage || !analysis || !analysis.canUpload) return

  chatStore.replaceLocalMessage(messageId, (message) => {
    if (message.aiDocumentAnalysis) {
      message.aiDocumentAnalysis.status = 'uploading'
    }
  })

  try {
    const res = await knowledgeApi.confirmAiUpload({
      analysisId: analysis.analysisId,
      knowledgeBaseId: analysis.suggestedKnowledgeBaseId || undefined,
      documentName: analysis.suggestedDocumentName
    })
    chatStore.replaceLocalMessage(messageId, (message) => {
      if (message.aiDocumentAnalysis) {
        message.aiDocumentAnalysis.status = 'uploaded'
      }
      message.content = `已提交上传，文档《${res.data.documentName}》正在知识库中后台处理。`
    })
  } catch {
    chatStore.replaceLocalMessage(messageId, (message) => {
      if (message.aiDocumentAnalysis) {
        message.aiDocumentAnalysis.status = 'failed'
      }
      message.error = true
      message.content = '确认上传失败，请稍后重试。'
    })
  }
}

function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}
</script>

<style lang="scss" scoped>
.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  padding: 16px 28px 10px;
  background: transparent;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
  animation: content-rise-in 0.48s ease;

  &.is-dragover {
    box-shadow: inset 0 0 0 1px rgba(111, 151, 194, 0.14);
  }
}

.mode-bar {
  width: 100%;
  margin: 0 0 10px;
  display: flex;
  justify-content: flex-start;
}

.mode-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--app-text);
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: rgba(111, 151, 194, 0.18);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.05);
  }
}

.mode-trigger__title {
  font-size: 14px;
  font-weight: 600;
}

.mode-trigger__arrow {
  font-size: 13px;
  color: var(--app-text-tertiary);
}

:deep(.mode-menu) {
  min-width: 220px;
}

:deep(.mode-menu__item) {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

:deep(.mode-menu__title) {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text);
}

:deep(.mode-menu__desc) {
  font-size: 12px;
  color: var(--app-text-tertiary);
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px 28px;
  min-height: 0;
}

.message-shell {
  width: min(760px, calc(100% - 160px));
  max-width: 100%;
  min-height: 100%;
  margin: 0 auto;
}

.loading-container,
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 340px);
  color: var(--app-text-secondary);
}

.empty-hero {
  max-width: 720px;
  padding: 28px 24px 10px;
  text-align: center;
}

.empty-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(111, 151, 194, 0.1);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.empty-title {
  margin-top: 18px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--app-text);
}

.empty-description {
  margin-top: 14px;
  max-width: 640px;
  line-height: 1.8;
  color: var(--app-text-secondary);
}

.quick-questions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  width: 100%;
  max-width: 900px;
  margin-top: 18px;
}

.quick-question {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 18px 18px 20px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--app-text);
  cursor: pointer;
  text-align: left;
  box-shadow: var(--app-shadow-sm);
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: rgba(111, 151, 194, 0.24);
    background: rgba(255, 255, 255, 0.96);
    box-shadow: var(--app-shadow-md);
    transform: translateY(-2px);
  }
}

.quick-question__label {
  font-size: 12px;
  font-weight: 700;
  color: var(--app-primary);
}

.quick-question__text {
  font-size: 15px;
  line-height: 1.7;
}

.loading-icon {
  font-size: 32px;
  animation: spin 1s linear infinite;
  color: var(--app-primary);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

.drag-overlay {
  position: absolute;
  inset: 64px 30px 170px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed rgba(111, 151, 194, 0.38);
  border-radius: 24px;
  background: rgba(241, 247, 255, 0.92);
  color: #284764;
  text-align: center;
  pointer-events: none;
  z-index: 4;
  box-shadow: 0 0 0 8px rgba(111, 151, 194, 0.08);
}

.drag-overlay__title {
  font-size: 22px;
  font-weight: 700;
}

.drag-overlay__desc {
  margin-top: 8px;
  max-width: 520px;
  line-height: 1.7;
  color: #5d748d;
}

.input-area {
  position: relative;
  z-index: 2;
  padding-top: 10px;
  padding-bottom: 4px;
  display: flex;
  justify-content: center;
}

.input-card {
  width: min(700px, calc(100% - 220px));
  margin: 0 auto;
  padding: 8px 10px 8px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus-within {
    border-color: rgba(111, 151, 194, 0.2);
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  }
}

.hidden-file-input {
  display: none;
}

.pending-file-bar {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid rgba(111, 151, 194, 0.14);
  border-radius: 12px;
  background: #f7fbff;
}

.pending-file-info {
  min-width: 0;
}

.pending-file-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text);
  word-break: break-all;
}

.pending-file-tip {
  margin-top: 4px;
  font-size: 11px;
  color: var(--app-text-secondary);
  line-height: 1.55;
}

.pending-file-remove {
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1px solid #d6dfeb;
  border-radius: 10px;
  background: #fff;
  color: var(--app-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: rgba(111, 151, 194, 0.16);
    color: var(--app-primary);
  }
}

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;

  :deep(.el-textarea) {
    width: 100%;
  }

  :deep(.el-textarea__inner) {
    min-height: 54px !important;
    padding: 10px 12px;
    border: none;
    border-radius: 12px;
    box-shadow: none;
    font-size: 13px;
    line-height: 1.6;
  }
}

.action-slot {
  display: flex;
  align-items: flex-end;
  flex-shrink: 0;
  padding-bottom: 2px;
  min-width: 42px;
  justify-content: flex-end;
}

.send-button,
.stop-button {
  width: 42px;
  height: 42px;
  min-width: 42px;
  border-radius: 999px;
  padding: 0;
  box-shadow: none;
}

.send-button :deep(.el-icon) {
  font-size: 16px;
}

.stop-button {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #f3f4f6;
  color: transparent;

  &:hover {
    background: #eceff3;
    border-color: rgba(15, 23, 42, 0.12);
  }

  :deep(span) {
    display: none;
  }

  &::before {
    content: '';
    width: 12px;
    height: 12px;
    border-radius: 4px;
    background: #111827;
  }
}

.input-footer {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.footer-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.plus-trigger {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover:not(:disabled) {
    background: rgba(148, 163, 184, 0.12);
    color: var(--app-text);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.tool-link {
  display: none;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s ease, opacity 0.2s ease;

  &:hover:not(:disabled) {
    color: var(--app-text);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.tool-divider {
  width: 1px;
  height: 12px;
  background: rgba(148, 163, 184, 0.28);
}

.tool-meta {
  font-size: 12px;
  color: var(--app-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mode-hint {
  font-size: 11px;
  color: var(--app-text-tertiary);
  text-align: right;
}

@keyframes content-rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 960px) {
  .chat-panel {
    padding: 14px 18px 12px;
  }

  .mode-bar,
  .message-shell,
  .input-card {
    width: min(100%, 720px);
  }

  .empty-hero {
    padding: 18px 6px 10px;
  }

  .empty-title {
    font-size: 28px;
  }

  .quick-questions {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .chat-panel {
    padding: 12px 12px 10px;
  }

  .message-list {
    padding: 0 0 12px;
  }

  .mode-bar,
  .message-shell,
  .input-card {
    width: 100%;
  }

  .mode-trigger {
    width: 100%;
    justify-content: space-between;
  }

  .drag-overlay {
    inset: 56px 12px 150px;
    border-radius: 20px;
  }

  .input-card {
    padding: 8px;
    border-radius: 14px;
  }

  .input-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .footer-tools {
    width: 100%;
  }

  .mode-hint {
    text-align: left;
  }
}
</style>
