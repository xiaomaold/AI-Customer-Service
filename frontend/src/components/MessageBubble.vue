<template>
  <div :class="['message-bubble', message.role]">
    <div class="content-wrapper">
      <transition name="tip-fade">
        <div v-if="message.corrected" class="correction-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ message.correctionPending ? '正在为你更新更准确的回答' : '回答已更新为更准确的版本' }}</span>
        </div>
      </transition>

      <div :class="contentClass" v-html="renderedContent"></div>

      <div v-if="showDocumentAnalysisCard && analysisCard" class="document-analysis-card">
        <div class="document-analysis-title">AI 文档识别结果</div>
        <div class="document-analysis-item">
          <span class="label">文件</span>
          <span class="value">{{ analysisCard.fileName }}</span>
        </div>
        <div class="document-analysis-item">
          <span class="label">推荐知识库</span>
          <span class="value">{{ analysisCard.suggestedKnowledgeBaseName || '暂未识别' }}</span>
        </div>
        <div class="document-analysis-item">
          <span class="label">推荐文档名</span>
          <span class="value">{{ analysisCard.suggestedDocumentName }}</span>
        </div>
        <div class="document-analysis-item">
          <span class="label">摘要</span>
          <span class="value">{{ analysisCard.summary }}</span>
        </div>
        <div v-if="analysisCard.tags.length > 0" class="document-analysis-tags">
          <el-tag
            v-for="tag in analysisCard.tags"
            :key="tag"
            size="small"
            effect="plain"
          >
            {{ tag }}
          </el-tag>
        </div>
        <div v-if="analysisCard.uploadDeniedReason" class="document-analysis-tip">
          {{ analysisCard.uploadDeniedReason }}
        </div>
        <div v-if="analysisCard.canUpload" class="document-analysis-actions">
          <button
            type="button"
            class="follow-up-action"
            :disabled="analysisCard.status === 'uploading' || analysisCard.status === 'uploaded'"
            @click="emit('confirm-upload', message.id)"
          >
            {{
              analysisCard.status === 'uploading'
                ? '上传中...'
                : analysisCard.status === 'uploaded'
                  ? '已提交上传'
                  : '确认上传到知识库'
            }}
          </button>
        </div>
      </div>

      <div v-if="referenceMeta" class="reference-card">
        <div v-if="referenceMeta.scopeHint" class="scope-hint">
          <el-icon><Compass /></el-icon>
          <span>当前在“{{ referenceMeta.scopeHint }}”范围内检索</span>
        </div>

        <template v-if="referenceMeta.references.length > 0">
          <div class="reference-title">参考来源知识库</div>
          <div class="reference-tags">
            <el-tag
              v-for="reference in referenceMeta.references"
              :key="reference"
              size="small"
              effect="plain"
              class="reference-tag"
            >
              {{ reference }}
            </el-tag>
          </div>
        </template>

        <div v-else-if="referenceMeta.summary" class="reference-summary">
          <div class="reference-title">参考来源</div>
          <div class="reference-content">{{ referenceMeta.summary }}</div>
        </div>

        <div v-if="referenceMeta.followUps.length > 0" class="follow-up-block">
          <div class="follow-up-title">推荐追问</div>
          <div class="follow-up-actions">
            <button
              v-for="followUp in referenceMeta.followUps"
              :key="followUp"
              type="button"
              class="follow-up-action"
              @click="emit('use-question', followUp)"
            >
              {{ followUp }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="message.isStreaming" class="streaming-indicator">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </div>

      <div v-if="message.error" class="error-tip">
        <el-icon><WarningFilled /></el-icon>
        <span>消息发送失败</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import { Compass, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import type { Message } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import 'highlight.js/styles/github-dark.css'

interface ParsedReferenceMeta {
  scopeHint: string | null
  references: string[]
  summary: string | null
  followUps: string[]
}

const props = defineProps<{
  message: Message
}>()

const authStore = useAuthStore()

const emit = defineEmits<{
  (event: 'use-question', question: string): void
  (event: 'confirm-upload', messageId: string): void
}>()

function formatStructuredContent(content: string) {
  const normalized = content.replace(/\r\n/g, '\n').trim()

  const withSectionBreaks = normalized
    .replace(/([。！？])\s*(退款规则：|退款申请方式（任选其一）：|审核与到账时效：|补充说明：)/g, '$1\n\n$2')
    .replace(/(如下：?\s*(退款规则：))/g, '$1\n\n$2')

  const lines = withSectionBreaks.split('\n')
  const formattedLines: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      formattedLines.push('')
      continue
    }

    if (/^(退款规则：|退款申请方式（任选其一）：|审核与到账时效：|补充说明：)$/.test(line)) {
      formattedLines.push(`### ${line}`)
      continue
    }

    if (/^(标准版|已开通|定制开发|拨打客服电话|发送邮件|通过在线工单|退款申请将由|审核通过后|发票已开具|优惠券抵扣)/.test(line)) {
      formattedLines.push(`- ${line}`)
      continue
    }

    formattedLines.push(line)
  }

  return formattedLines.join('\n')
}

function parseReferenceContent(content?: string | null): ParsedReferenceMeta | null {
  const normalizedLines = (content || '')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)

  if (normalizedLines.length === 0) return null

  const meta: ParsedReferenceMeta = {
    scopeHint: null,
    references: [],
    summary: null,
    followUps: []
  }

  const summaryLines: string[] = []

  for (const line of normalizedLines) {
    if (
      line.startsWith('ROUTE_MODE:')
      || line.startsWith('ROUTE_REASON:')
      || line.startsWith('ROUTE_DOMAIN:')
      || line.startsWith('ROUTE_INTENT:')
      || line.startsWith('REFERENCE_COUNT:')
    ) {
      continue
    }

    if (line.startsWith('SCOPE_HINT:')) {
      meta.scopeHint = line.slice('SCOPE_HINT:'.length).trim() || null
      continue
    }

    const referenceMatch = line.match(/^REFERENCE_(\d+):\s*(.+)$/)
    if (referenceMatch?.[2]) {
      meta.references.push(referenceMatch[2].trim())
      continue
    }

    const followUpMatch = line.match(/^FOLLOW_UP_(\d+):\s*(.+)$/)
    if (followUpMatch?.[2]) {
      meta.followUps.push(followUpMatch[2].trim())
      continue
    }

    summaryLines.push(line)
  }

  meta.summary = summaryLines.length > 0 ? summaryLines.join('\n') : null
  if (!meta.scopeHint && meta.references.length === 0 && !meta.summary && meta.followUps.length === 0) {
    return null
  }
  return meta
}

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  return marked.parse(formatStructuredContent(props.message.content)) as string
})

const contentClass = computed(() => [
  'content',
  {
    'is-correcting': props.message.correctionPending,
    'is-fading-out': props.message.correctionPhase === 'fade-out',
    'is-fading-in': props.message.correctionPhase === 'fade-in'
  }
])

const referenceMeta = computed(() => parseReferenceContent(props.message.referenceContent))
const analysisCard = computed(() => props.message.aiDocumentAnalysis ?? null)
const showDocumentAnalysisCard = computed(() => authStore.canManageKnowledge && !!props.message.aiDocumentAnalysis)
</script>

<style lang="scss" scoped>
.message-bubble {
  display: flex;
  gap: 0;
  margin-bottom: 28px;
  animation: bubble-fade-in 0.34s ease;

  &:not(.user) {
    .content {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      border-radius: 0;
    }
  }

  &.user {
    justify-content: flex-end;

    .content-wrapper {
      align-items: flex-end;
      max-width: min(54%, 440px);
    }

    .content {
      background: #eeeeef;
      color: #2f3743;
      border-color: rgba(148, 163, 184, 0.2);
      box-shadow: 0 8px 16px rgba(15, 23, 42, 0.04);
      border-radius: 24px;

      :deep(h1, h2, h3, h4),
      :deep(strong) {
        color: #2f3743;
      }
    }
  }
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
}

.correction-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(111, 151, 194, 0.1);
  color: var(--app-primary);
  font-size: 12px;
  line-height: 1;
}

.content {
  background: rgba(255, 255, 255, 0.96);
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: var(--app-shadow-sm);
  word-break: break-word;
  transform-origin: center top;
  transition:
    opacity 0.28s ease,
    transform 0.28s ease,
    filter 0.28s ease,
    box-shadow 0.28s ease;

  &.is-correcting {
    box-shadow: 0 12px 24px rgba(111, 151, 194, 0.12);
  }

  &.is-fading-out {
    opacity: 0.38;
    transform: translateY(4px) scale(0.992);
    filter: saturate(0.9);
  }

  &.is-fading-in {
    opacity: 0.88;
    transform: translateY(0) scale(1.008);
  }

  :deep(pre) {
    background: #0f172a;
    color: #e6edf3;
    padding: 14px;
    border-radius: 14px;
    overflow-x: auto;
    margin: 10px 0;
    border: 1px solid rgba(255, 255, 255, 0.08);
    white-space: pre-wrap;
  }

  :deep(code) {
    font-family: 'Fira Code', monospace;
    font-size: 13px;
  }

  :deep(pre code) {
    display: block;
    color: inherit;
    background: transparent;
    line-height: 1.7;
    white-space: pre-wrap;
  }

  :deep(p) {
    margin: 0 0 10px;
    line-height: 1.8;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(h1, h2, h3, h4) {
    margin: 0 0 10px;
    line-height: 1.45;
    color: var(--app-text);
  }

  :deep(h3) {
    font-size: 15px;
    font-weight: 700;
  }

  :deep(ul),
  :deep(ol) {
    margin: 8px 0 0;
    padding-left: 18px;
  }

  :deep(li) {
    margin-bottom: 8px;
    line-height: 1.8;

    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(strong) {
    color: var(--app-text);
  }
}

.reference-card,
.document-analysis-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 100%;
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(248, 251, 255, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: var(--app-shadow-sm);
}

.document-analysis-title {
  font-size: 13px;
  font-weight: 700;
  color: #2e4761;
}

.document-analysis-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.document-analysis-item .label {
  font-size: 12px;
  color: #71859a;
}

.document-analysis-item .value {
  color: var(--app-text);
  line-height: 1.7;
  word-break: break-word;
}

.document-analysis-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.document-analysis-tip {
  font-size: 12px;
  color: #8b6a29;
  background: #fff9e8;
  border: 1px solid #f2dfaa;
  padding: 8px 10px;
  border-radius: 10px;
}

.document-analysis-actions {
  display: flex;
  justify-content: flex-start;
}

.scope-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #47627c;
  font-size: 12px;
}

.reference-title,
.follow-up-title {
  font-size: 12px;
  font-weight: 700;
  color: #395168;
}

.reference-tags,
.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:deep(.reference-tag) {
  color: var(--app-accent-ink);
  border-color: #d7dfe8;
  background: var(--app-accent-ink-soft);
}

.reference-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.reference-content {
  line-height: 1.7;
  word-break: break-word;
  color: #5e6d7e;
  font-size: 12px;
}

.follow-up-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.follow-up-action {
  padding: 8px 12px;
  border: 1px solid #c7d8ea;
  border-radius: 999px;
  background: #fff;
  color: #275681;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    border-color: #8fb8e0;
    background: #eaf4ff;
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.streaming-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 2px;

  .dot {
    width: 6px;
    height: 6px;
    background: var(--app-primary);
    border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out both;

    &:nth-child(1) {
      animation-delay: -0.32s;
    }

    &:nth-child(2) {
      animation-delay: -0.16s;
    }
  }
}

.error-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--app-danger);
  font-size: 12px;
  margin-top: 4px;
}

.tip-fade-enter-active,
.tip-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.tip-fade-enter-from,
.tip-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

@keyframes bubble-fade-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 960px) {
  .content-wrapper {
    max-width: 100%;
  }

  .message-bubble.user .content-wrapper {
    max-width: min(72%, 100%);
  }
}

@media (max-width: 720px) {
  .message-bubble {
    margin-bottom: 18px;
  }

  .content-wrapper {
    max-width: 100%;
  }

  .content,
  .reference-card,
  .document-analysis-card {
    border-radius: 18px;
  }
}
</style>
