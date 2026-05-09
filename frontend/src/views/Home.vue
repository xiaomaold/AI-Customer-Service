<template>
  <div class="app-container">
    <el-container class="main-layout">
      <el-aside :width="sidebarCollapsed ? '74px' : '280px'" :class="['sidebar', { collapsed: sidebarCollapsed }]">
        <SessionSidebar :collapsed="sidebarCollapsed" @toggle="toggleSidebar" />
      </el-aside>
      <el-container class="workspace">
        <el-main class="main-content">
          <ChatPanel />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import ChatPanel from '@/components/ChatPanel.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'

const SIDEBAR_COLLAPSED_KEY = 'ai-rag-sidebar-collapsed'

const sidebarCollapsed = ref(loadSidebarCollapsed())

watch(sidebarCollapsed, (value) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(value))
  }
})

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function loadSidebarCollapsed() {
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
}
</script>

<style lang="scss" scoped>
.app-container {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  padding: 0;
  animation: page-fade-in 0.45s ease;
  background: #ffffff;
}

.main-layout {
  height: 100%;
  gap: 0;
  background: transparent;
}

.sidebar {
  overflow: hidden;
  background: #f3f4f6;
  border-right: 1px solid rgba(148, 163, 184, 0.12);
  transition: width 0.24s ease;

  &.collapsed {
    border-right-color: rgba(148, 163, 184, 0.08);
  }
}

.workspace {
  overflow: hidden;
  min-width: 0;
  background: #ffffff;
}

.main-content {
  height: 100%;
  background: transparent;
  padding: 0;
}

@keyframes page-fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
