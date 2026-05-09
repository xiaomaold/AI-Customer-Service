<template>
  <div :class="['session-sidebar', { collapsed }]">
    <div class="sidebar-topbar">
      <div v-if="!collapsed" class="brand-mark">
        <ProjectMark :size="32" />
      </div>

      <button type="button" class="toggle-button" @click="$emit('toggle')">
        <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
      </button>
    </div>

    <div class="sidebar-header">
      <el-button class="create-button" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        <span v-if="!collapsed">新建会话</span>
      </el-button>
    </div>

    <div v-if="!collapsed" class="session-list" v-loading="sessionStore.loading">
      <div
        v-for="session in sessionStore.sessions"
        :key="session.id"
        :class="['session-item', { active: session.id === sessionStore.currentSessionId }]"
        @click="handleSelect(session.id)"
      >
        <div class="session-info">
          <div class="session-name-row">
            <span v-if="sessionStore.isPinned(session.id)" class="pinned-mark">置顶</span>
            <div class="session-name">{{ session.sessionName }}</div>
          </div>
          <div class="session-time">{{ formatTime(session.lastMessageTime || session.createTime) }}</div>
        </div>

        <el-dropdown
          class="session-actions"
          trigger="click"
          placement="bottom-end"
          @command="(command) => handleCommand(command, session)"
        >
          <button class="more-button" type="button" @click.stop>
            <el-icon><MoreFilled /></el-icon>
          </button>

          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="sessionStore.isPinned(session.id) ? 'unpin' : 'pin'">
                <el-icon><Top /></el-icon>
                <span>{{ sessionStore.isPinned(session.id) ? '取消置顶' : '置顶' }}</span>
              </el-dropdown-item>
              <el-dropdown-item command="rename">
                <el-icon><EditPen /></el-icon>
                <span>重命名</span>
              </el-dropdown-item>
              <el-dropdown-item command="delete" divided>
                <el-icon><Delete /></el-icon>
                <span>删除</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-empty
        v-if="!sessionStore.loading && sessionStore.sessions.length === 0"
        description="还没有会话"
      />
    </div>

    <div class="sidebar-footer">
      <el-dropdown class="account-dropdown" trigger="click" placement="top-start">
        <div :class="['account-card', { compact: collapsed }]">
          <div class="account-main">
            <el-avatar :size="30" class="account-avatar">
              {{ (authStore.user?.nickname || authStore.user?.username || 'U').slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div v-if="!collapsed" class="account-copy">
              <strong>{{ authStore.user?.nickname || authStore.user?.username }}</strong>
              <span>个人相关</span>
            </div>
          </div>
          <el-icon v-if="!collapsed" class="account-arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="authStore.canManageAdmin" @click="showAdminManager = true">
              系统管理
            </el-dropdown-item>
            <el-dropdown-item v-if="authStore.canManageKnowledge" @click="showKnowledgeManager = true">
              知识库管理
            </el-dropdown-item>
            <el-dropdown-item @click="showKnowledgeLibrary = true">我的知识库</el-dropdown-item>
            <el-dropdown-item @click="showPersonalCenter = true">个人中心</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-dialog v-model="showKnowledgeLibrary" title="我的知识库" width="1120px" destroy-on-close>
      <KnowledgeLibrary />
    </el-dialog>

    <el-dialog v-model="showPersonalCenter" title="个人中心" width="680px" destroy-on-close>
      <PersonalCenter />
    </el-dialog>
    <el-dialog v-model="showKnowledgeManager" title="知识库管理" width="1240px" destroy-on-close>
      <KnowledgeManager />
    </el-dialog>
    <el-dialog v-model="showAdminManager" title="系统管理" width="1320px" destroy-on-close>
      <AdminManager />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowDown,
  Delete,
  EditPen,
  Expand,
  Fold,
  MoreFilled,
  Plus,
  Top
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import AdminManager from '@/components/AdminManager.vue'
import KnowledgeLibrary from '@/components/KnowledgeLibrary.vue'
import KnowledgeManager from '@/components/KnowledgeManager.vue'
import PersonalCenter from '@/components/PersonalCenter.vue'
import ProjectMark from '@/components/ProjectMark.vue'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { SessionVO } from '@/types'

defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  (event: 'toggle'): void
}>()

const router = useRouter()
const sessionStore = useSessionStore()
const chatStore = useChatStore()
const authStore = useAuthStore()
const showPersonalCenter = ref(false)
const showKnowledgeLibrary = ref(false)
const showKnowledgeManager = ref(false)
const showAdminManager = ref(false)

onMounted(() => {
  sessionStore.fetchSessions()
})

function handleCreate() {
  void sessionStore.createSession()
}

function handleSelect(id: string) {
  sessionStore.setCurrentSession(id)
  void chatStore.fetchHistory(id)
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定删除这个会话吗？', '提示', { type: 'warning' })
    await sessionStore.deleteSession(id)
  } catch {
    // ignore cancel
  }
}

async function handleRename(session: SessionVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: session.sessionName,
      inputPattern: /\S+/,
      inputErrorMessage: '会话名称不能为空'
    })

    if (typeof value === 'string' && value.trim() && value.trim() !== session.sessionName) {
      await sessionStore.renameSession(session.id, value)
    }
  } catch {
    // ignore cancel
  }
}

function handleCommand(command: string, session: SessionVO) {
  if (command === 'pin' || command === 'unpin') {
    void sessionStore.togglePinSession(session.id)
    return
  }

  if (command === 'rename') {
    void handleRename(session)
    return
  }

  if (command === 'delete') {
    void handleDelete(session.id)
  }
}

function handleLogout() {
  authStore.logout()
  chatStore.reset()
  sessionStore.reset()
  router.push('/login')
}

function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return date.toLocaleDateString()
}
</script>

<style lang="scss" scoped>
.session-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  color: var(--app-text);
  animation: sidebar-slide-in 0.45s ease;
  background: #f3f4f6;
  transition: background-color 0.24s ease;

  &.collapsed {
    align-items: center;
  }
}

.sidebar-topbar {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 18px 18px 12px;
  transition: padding 0.24s ease, gap 0.24s ease;

  .session-sidebar.collapsed & {
    justify-content: center;
    gap: 0;
    padding: 18px 0 12px;
  }
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  animation: sidebar-content-fade-in 0.28s ease;
}

.toggle-button {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  transition: color 0.2s ease, transform 0.2s ease;

  &:hover {
    color: #475569;
    transform: translateY(-1px);
  }
}

.sidebar-header {
  width: 100%;
  padding: 8px 14px 10px;
  background: #f3f4f6;
  transition: padding 0.24s ease;

  .session-sidebar.collapsed & {
    display: flex;
    justify-content: center;
    padding: 8px 0 10px;
  }
}

.create-button {
  width: 100%;
  min-height: 38px;
  justify-content: flex-start;
  gap: 10px;
  padding: 0 14px;
  border-radius: 18px;
  background: #eaf2ff;
  border: 1px solid rgba(96, 165, 250, 0.45);
  color: #2563eb;
  box-shadow: none;

  :deep(.el-icon) {
    font-size: 18px;
  }

  .session-sidebar.collapsed & {
    width: 46px;
    min-width: 46px;
    min-height: 46px;
    justify-content: center;
    padding: 0;
    border-radius: 14px;
  }
}


.session-list {
  width: 100%;
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px 12px;
  scrollbar-width: none;
  animation: sidebar-content-fade-in 0.3s ease;

  &::-webkit-scrollbar {
    width: 0;
  }

  &:hover {
    scrollbar-width: thin;
  }

  &:hover::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.55);
    border-radius: 999px;
  }
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  margin-bottom: 2px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
  background: transparent;

  &:hover {
    background: rgba(15, 23, 42, 0.04);

    .more-button {
      opacity: 1;
    }
  }

  &.active {
    background: #e5e7eb;

    .session-name {
      color: #1f2937;
    }
  }
}

.session-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.session-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.pinned-mark {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(79, 98, 119, 0.1);
  color: #4f6277;
  font-size: 10px;
  font-weight: 600;
}

.session-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  overflow: hidden;
}

.session-time {
  font-size: 11px;
  color: var(--app-text-tertiary);
  margin-top: 2px;
}

.session-actions {
  flex-shrink: 0;
}

.more-button {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #9aa4b2;
  cursor: pointer;
  opacity: 0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    opacity 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.96);
    color: #4b5563;
    box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
    transform: translateY(-1px);
  }
}

.sidebar-footer {
  width: 100%;
  padding: 12px 12px 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
  background: #f3f4f6;
  margin-top: auto;
  transition: padding 0.24s ease;

  .session-sidebar.collapsed & {
    display: flex;
    justify-content: center;
    padding: 12px 0 14px;
  }
}

.account-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.12);
  cursor: pointer;

  &.compact {
    justify-content: center;
    padding: 10px 0;
  }
}

.account-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.account-avatar {
  flex-shrink: 0;
  background: #7ea3cb;
  color: #fff;
}

.account-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    font-size: 13px;
    font-weight: 600;
    color: var(--app-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  span {
    font-size: 11px;
    color: var(--app-text-tertiary);
  }
}

.account-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--app-text-tertiary);
}

@keyframes sidebar-slide-in {
  from {
    opacity: 0;
    transform: translateX(-10px);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes sidebar-content-fade-in {
  from {
    opacity: 0;
    transform: translateX(-6px);
  }

  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
