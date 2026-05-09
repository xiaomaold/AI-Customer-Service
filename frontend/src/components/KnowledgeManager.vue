<template>
  <div class="knowledge-manager">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="知识库" name="bases">
        <div class="toolbar">
          <el-button type="primary" @click="showCreateBase = true">
            <el-icon><Plus /></el-icon>
            新建知识库
          </el-button>
        </div>

        <el-table :data="knowledgeBases" v-loading="loadingBases">
          <el-table-column prop="knowledgeBaseName" label="名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="documentCount" label="文档数" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="selectBase(row.id)">
                文档
              </el-button>
              <el-button type="warning" size="small" text @click="openPermissionDialog(row)">
                授权
              </el-button>
              <el-button type="danger" size="small" text @click="handleDeleteBase(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="文档管理" name="documents">
        <div class="toolbar">
          <el-select v-model="selectedBaseId" placeholder="选择知识库" style="width: 220px">
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.knowledgeBaseName"
              :value="kb.id"
            />
          </el-select>

          <el-upload
            :action="uploadUrl"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            accept=".pdf,.doc,.docx,.txt,.md,.markdown"
            :disabled="!selectedBaseId"
            :headers="uploadHeaders"
          >
            <el-button type="primary" :disabled="!selectedBaseId">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
          </el-upload>
        </div>

        <el-table :data="documents" v-loading="loadingDocs">
          <el-table-column prop="documentName" label="文档名称" />
          <el-table-column prop="fileExt" label="类型" width="80" />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="parseStatus" label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.parseStatus)">
                {{ getStatusText(row.parseStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块数" width="90" />
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createTime" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="danger" size="small" text @click="handleDeleteDoc(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreateBase" title="新建知识库" width="400px">
      <el-form :model="newBaseForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="newBaseForm.knowledgeBaseName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newBaseForm.description" type="textarea" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateBase = false">取消</el-button>
        <el-button type="primary" @click="handleCreateBase">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showPermissionDialog"
      :title="permissionDialogTitle"
      width="820px"
      destroy-on-close
    >
      <div class="permission-panel">
        <div class="grant-toolbar">
          <el-select v-model="grantForm.userId" filterable placeholder="选择普通用户" style="width: 240px">
            <el-option
              v-for="user in assignableUsers"
              :key="user.userId"
              :label="`${user.nickname} (${user.username})`"
              :value="user.userId"
            />
          </el-select>
          <el-select v-model="grantForm.permissionType" placeholder="权限类型" style="width: 140px">
            <el-option label="查看" value="READ" />
            <el-option label="编辑" value="WRITE" />
          </el-select>
          <el-button type="primary" :disabled="!grantForm.userId" @click="handleGrantPermission">
            授权
          </el-button>
        </div>

        <div class="hint-text">这里只展示普通用户，后台管理员和知识库管理员不参与该授权列表。</div>

        <el-table :data="permissionGrants" v-loading="loadingPermissions">
          <el-table-column prop="nickname" label="用户" min-width="140">
            <template #default="{ row }">
              <div>{{ row.nickname }}</div>
              <div class="sub-text">@{{ row.username }}</div>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="160">
            <template #default="{ row }">
              <div class="role-tags">
                <el-tag v-for="role in row.roles" :key="role" size="small" effect="plain">
                  {{ formatRole(role) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="权限" width="100">
            <template #default="{ row }">
              <el-tag :type="row.permissionType === 'WRITE' ? 'primary' : 'success'">
                {{ row.permissionType === 'WRITE' ? '编辑' : '查看' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="grantedAt" label="授权时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.grantedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="danger" size="small" text @click="handleRevokePermission(row.userId)">
                取消授权
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api/knowledge'
import type {
  ApiResponse,
  KnowledgeBaseVO,
  KnowledgeDocVO,
  KnowledgeDocumentUploadResponse,
  KnowledgePermissionGrantVO,
  KnowledgePermissionUserVO
} from '@/types'
import { getAccessToken } from '@/utils/request'

const activeTab = ref('bases')
const knowledgeBases = ref<KnowledgeBaseVO[]>([])
const documents = ref<KnowledgeDocVO[]>([])
const assignableUsers = ref<KnowledgePermissionUserVO[]>([])
const permissionGrants = ref<KnowledgePermissionGrantVO[]>([])
const loadingBases = ref(false)
const loadingDocs = ref(false)
const loadingPermissions = ref(false)
const selectedBaseId = ref<string | undefined>()
const showCreateBase = ref(false)
const showPermissionDialog = ref(false)
const permissionKnowledgeBase = ref<KnowledgeBaseVO | null>(null)
const newBaseForm = ref({
  knowledgeBaseName: '',
  description: ''
})
const grantForm = ref({
  userId: '',
  permissionType: 'READ'
})

const uploadUrl = computed(() => {
  if (!selectedBaseId.value) return ''
  return `/api/knowledge/documents/upload?knowledgeBaseId=${selectedBaseId.value}`
})

const uploadHeaders = computed(() => {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const permissionDialogTitle = computed(() =>
  permissionKnowledgeBase.value
    ? `知识库授权 - ${permissionKnowledgeBase.value.knowledgeBaseName}`
    : '知识库授权'
)

onMounted(() => {
  loadBases()
})

watch(selectedBaseId, () => {
  if (selectedBaseId.value) {
    loadDocuments()
  } else {
    documents.value = []
  }
})

async function loadBases() {
  loadingBases.value = true
  try {
    const res = await knowledgeApi.listBases()
    knowledgeBases.value = res.data || []
  } catch {
    ElMessage.error('加载知识库失败')
  } finally {
    loadingBases.value = false
  }
}

async function loadDocuments() {
  if (!selectedBaseId.value) return
  loadingDocs.value = true
  try {
    const res = await knowledgeApi.listDocuments(selectedBaseId.value)
    documents.value = res.data || []
  } catch {
    ElMessage.error('加载文档失败')
  } finally {
    loadingDocs.value = false
  }
}

async function loadAssignableUsers() {
  const res = await knowledgeApi.listAssignableUsers()
  assignableUsers.value = res.data || []
}

async function loadPermissionGrants() {
  if (!permissionKnowledgeBase.value) return
  loadingPermissions.value = true
  try {
    const res = await knowledgeApi.listBasePermissions(permissionKnowledgeBase.value.id)
    permissionGrants.value = res.data || []
  } finally {
    loadingPermissions.value = false
  }
}

async function handleCreateBase() {
  if (!newBaseForm.value.knowledgeBaseName.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }

  try {
    await knowledgeApi.createBase(newBaseForm.value)
    ElMessage.success('创建成功')
    showCreateBase.value = false
    newBaseForm.value = { knowledgeBaseName: '', description: '' }
    await loadBases()
  } catch {
    ElMessage.error('创建失败')
  }
}

async function handleDeleteBase(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该知识库吗？', '提示', { type: 'warning' })
    await knowledgeApi.deleteBase(id)
    ElMessage.success('删除成功')
    if (selectedBaseId.value === id) {
      selectedBaseId.value = undefined
    }
    await loadBases()
  } catch {
    // ignore cancel
  }
}

async function handleDeleteDoc(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该文档吗？', '提示', { type: 'warning' })
    await knowledgeApi.deleteDocument(id)
    ElMessage.success('删除成功')
    await loadDocuments()
  } catch {
    // ignore cancel
  }
}

async function openPermissionDialog(base: KnowledgeBaseVO) {
  permissionKnowledgeBase.value = base
  grantForm.value = {
    userId: '',
    permissionType: 'READ'
  }
  showPermissionDialog.value = true
  await Promise.all([loadAssignableUsers(), loadPermissionGrants()])
}

async function handleGrantPermission() {
  if (!permissionKnowledgeBase.value || !grantForm.value.userId) {
    return
  }
  try {
    await knowledgeApi.grantBasePermission(permissionKnowledgeBase.value.id, grantForm.value)
    ElMessage.success('授权成功')
    grantForm.value.userId = ''
    grantForm.value.permissionType = 'READ'
    await loadPermissionGrants()
  } catch {
    ElMessage.error('授权失败')
  }
}

async function handleRevokePermission(userId: string) {
  if (!permissionKnowledgeBase.value) return
  try {
    await ElMessageBox.confirm('确定取消该用户的知识库权限吗？', '提示', { type: 'warning' })
    await knowledgeApi.revokeBasePermission(permissionKnowledgeBase.value.id, userId)
    ElMessage.success('取消授权成功')
    await loadPermissionGrants()
  } catch {
    // ignore cancel
  }
}

function selectBase(id: string) {
  selectedBaseId.value = id
  activeTab.value = 'documents'
}

function beforeUpload(file: File) {
  const allowed = ['pdf', 'doc', 'docx', 'txt', 'md', 'markdown']
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !allowed.includes(ext)) {
    ElMessage.error('仅支持 PDF、Word、TXT、Markdown 文件')
    return false
  }
  return true
}

function handleUploadSuccess(response: ApiResponse<KnowledgeDocumentUploadResponse>) {
  if (response?.code !== 200 || !response.data) {
    ElMessage.error(response?.message || '上传失败')
    return
  }

  const { documentName, parseStatus, chunkCount } = response.data
  if (parseStatus === 'SUCCESS') {
    ElMessage.success(`上传并入库成功：${documentName}，共 ${chunkCount} 个分块`)
  } else {
    ElMessage.warning(`文件已上传，当前状态：${getStatusText(parseStatus)}`)
  }
  loadDocuments()
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

function formatDate(date?: string): string {
  if (!date) return '-'
  return new Date(date).toLocaleString()
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function getStatusType(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SUCCESS: 'success',
    PROCESSING: 'warning',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

function getStatusText(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: '已完成',
    PROCESSING: '处理中',
    FAILED: '失败'
  }
  return map[status] || status
}

function formatRole(role: string): string {
  const map: Record<string, string> = {
    ADMIN: '后台管理员',
    KB_ADMIN: '知识库管理员',
    USER: '普通用户'
  }
  return map[role] || role
}
</script>

<style lang="scss" scoped>
.knowledge-manager {
  min-height: 400px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.permission-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.grant-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.hint-text {
  font-size: 13px;
  color: #6b7f92;
}

.sub-text {
  font-size: 12px;
  color: #7a8da1;
  margin-top: 4px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
