<template>
  <div class="personal-center" v-loading="loading">
    <div class="profile-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="profile-main">
        <div class="profile-name">{{ displayName }}</div>
        <div class="profile-username">@{{ authStore.user?.username }}</div>
        <div class="profile-roles">
          <el-tag
            v-for="role in authStore.user?.roles || []"
            :key="role"
            size="small"
            type="info"
            effect="plain"
          >
            {{ roleLabelMap[role] || role }}
          </el-tag>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">当前角色</div>
        <div class="stat-value">{{ primaryRoleLabel }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">我的会话</div>
        <div class="stat-value">{{ sessionStore.sessions.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">可用知识库</div>
        <div class="stat-value">{{ permissions.length }}</div>
      </div>
    </div>

    <el-descriptions :column="1" border class="details-card">
      <el-descriptions-item label="用户 ID">
        {{ authStore.user?.userId }}
      </el-descriptions-item>
      <el-descriptions-item label="用户名">
        {{ authStore.user?.username }}
      </el-descriptions-item>
      <el-descriptions-item label="昵称">
        {{ authStore.user?.nickname || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="角色说明">
        {{ primaryRoleDescription }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="permission-section">
      <div class="section-title">我的知识库权限</div>
      <div class="section-subtitle">这里展示当前账号可访问或可管理的知识库。</div>
      <el-table :data="permissions" border empty-text="当前没有可用知识库权限">
        <el-table-column prop="knowledgeBaseName" label="知识库" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="权限" width="120">
          <template #default="{ row }">
            <el-tag :type="permissionTagType(row.permissionType)">
              {{ permissionText(row.permissionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { knowledgeApi } from '@/api/knowledge'
import { useAuthStore } from '@/stores/auth'
import { useSessionStore } from '@/stores/session'
import type { UserKnowledgePermissionVO } from '@/types'

const authStore = useAuthStore()
const sessionStore = useSessionStore()

const loading = ref(false)
const permissions = ref<UserKnowledgePermissionVO[]>([])

const roleLabelMap: Record<string, string> = {
  ADMIN: '后台管理员',
  KB_ADMIN: '知识库管理员',
  USER: '普通用户'
}

const roleDescriptionMap: Record<string, string> = {
  ADMIN: '可管理后台、用户与系统配置。',
  KB_ADMIN: '可维护知识库、文档与索引。',
  USER: '可发起问答、查看自己的会话与可用知识库。'
}

const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '未登录用户')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const primaryRole = computed(() => authStore.user?.roles?.[0] || 'USER')
const primaryRoleLabel = computed(() => roleLabelMap[primaryRole.value] || primaryRole.value)
const primaryRoleDescription = computed(() => roleDescriptionMap[primaryRole.value] || '暂无角色说明。')

onMounted(() => {
  loadPermissions()
})

async function loadPermissions() {
  loading.value = true
  try {
    const res = await knowledgeApi.myPermissions()
    permissions.value = res.data || []
  } catch {
    permissions.value = []
  } finally {
    loading.value = false
  }
}

function permissionText(permissionType: string) {
  const map: Record<string, string> = {
    MANAGE: '管理',
    READ: '查看',
    WRITE: '编辑'
  }
  return map[permissionType] || permissionType
}

function permissionTagType(permissionType: string): 'success' | 'warning' | 'info' | 'primary' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'primary'> = {
    MANAGE: 'warning',
    WRITE: 'primary',
    READ: 'success'
  }
  return map[permissionType] || 'info'
}
</script>

<style lang="scss" scoped>
.personal-center {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile-card {
  display: flex;
  gap: 16px;
  padding: 22px;
  border-radius: 24px;
  background: #ffffff;
  color: var(--app-text);
  box-shadow: var(--app-shadow-md);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.avatar {
  width: 60px;
  height: 60px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  background: #eaf3ff;
  color: #7ea3cb;
  font-size: 24px;
  font-weight: 700;
}

.profile-main {
  min-width: 0;
}

.profile-name {
  font-size: 24px;
  font-weight: 700;
}

.profile-username {
  margin-top: 4px;
  color: var(--app-text-secondary);
}

.profile-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: var(--app-shadow-sm);
}

.stat-label {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text);
}

.details-card {
  overflow: hidden;
  border-radius: 20px;

  :deep(.el-descriptions__label) {
    width: 120px;
  }
}

.permission-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
}

.section-subtitle {
  color: var(--app-text-secondary);
  font-size: 13px;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .profile-card {
    flex-direction: column;
  }
}
</style>
