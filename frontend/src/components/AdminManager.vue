<template>
  <div class="admin-manager">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users">
        <div class="toolbar">
          <div class="filter-group">
            <el-input
              v-model="userFilters.keyword"
              placeholder="搜索用户名/昵称/姓名/邮箱"
              clearable
              style="width: 260px"
              @keyup.enter="loadUsers"
            />
            <el-select v-model="userFilters.status" clearable placeholder="状态" style="width: 120px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
            <el-select v-model="userFilters.roleCode" clearable filterable placeholder="角色" style="width: 160px">
              <el-option
                v-for="role in roles"
                :key="role.roleCode"
                :label="`${role.roleName} (${role.roleCode})`"
                :value="role.roleCode"
              />
            </el-select>
            <el-button @click="loadUsers">查询</el-button>
          </div>
          <el-button type="primary" @click="openCreateUserDialog">新增用户</el-button>
        </div>

        <el-table :data="users" v-loading="loadingUsers">
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="nickname" label="昵称" min-width="120" />
          <el-table-column prop="realName" label="姓名" min-width="120" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <div class="role-tags">
                <el-tag v-for="role in row.roles" :key="role" size="small" effect="plain">
                  {{ role }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginTime" label="最近登录" width="180">
            <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="openEditUserDialog(row)">编辑</el-button>
              <el-button type="warning" size="small" text @click="toggleUserStatus(row)">
                {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
              </el-button>
              <el-button type="danger" size="small" text @click="handleResetPassword(row)">
                重置密码
              </el-button>
              <el-button type="danger" size="small" text @click="handleDeleteUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="角色管理" name="roles">
        <div class="toolbar">
          <div class="filter-group">
            <el-input
              v-model="roleFilters.keyword"
              placeholder="搜索角色编码/名称/说明"
              clearable
              style="width: 260px"
              @keyup.enter="loadRoles"
            />
            <el-select v-model="roleFilters.status" clearable placeholder="状态" style="width: 120px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
            <el-button @click="loadRoles">查询</el-button>
          </div>
          <el-button type="primary" @click="openCreateRoleDialog">新增角色</el-button>
        </div>

        <el-table :data="roles" v-loading="loadingRoles">
          <el-table-column prop="roleCode" label="角色编码" width="140" />
          <el-table-column prop="roleName" label="角色名称" min-width="160" />
          <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="openEditRoleDialog(row)">编辑</el-button>
              <el-button type="danger" size="small" text @click="handleDeleteRole(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="系统配置" name="configs">
        <div class="toolbar">
          <div class="filter-group">
            <el-input
              v-model="configFilters.keyword"
              placeholder="搜索配置键/名称/说明"
              clearable
              style="width: 260px"
              @keyup.enter="loadConfigs"
            />
            <el-select v-model="configFilters.status" clearable placeholder="状态" style="width: 120px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
            <el-button @click="loadConfigs">查询</el-button>
          </div>
          <el-button type="primary" @click="openCreateConfigDialog">新增配置</el-button>
        </div>

        <el-table :data="configs" v-loading="loadingConfigs">
          <el-table-column prop="configKey" label="配置键" width="220" />
          <el-table-column prop="configName" label="配置名称" width="180" />
          <el-table-column prop="configValue" label="配置值" min-width="220" show-overflow-tooltip />
          <el-table-column prop="valueType" label="类型" width="100" />
          <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="openEditConfigDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="知识运营看板" name="dashboard">
        <MissedQuestionDashboard />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showUserDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="!!editingUser" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.mobile" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="userForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="userForm.roleIds"
            multiple
            filterable
            style="width: 100%"
            :disabled="isEditingCurrentUser"
          >
            <el-option
              v-for="role in allRoles"
              :key="role.roleId"
              :label="`${role.roleName} (${role.roleCode})`"
              :value="role.roleId"
            />
          </el-select>
          <div v-if="isEditingCurrentUser" class="hint-text">
            当前登录管理员不能修改自己的角色，避免误删或误降权。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRoleDialog" :title="editingRole ? '编辑角色' : '新增角色'" width="460px">
      <el-form :model="roleForm" label-width="90px">
        <el-form-item label="角色编码">
          <el-input v-model="roleForm.roleCode" :disabled="!!editingRole" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.roleName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="roleForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="roleForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showConfigDialog" :title="editingConfig ? '编辑配置' : '新增配置'" width="520px">
      <el-form :model="configForm" label-width="90px">
        <el-form-item label="配置键">
          <el-input v-model="configForm.configKey" :disabled="!!editingConfig" />
        </el-form-item>
        <el-form-item label="配置名称">
          <el-input v-model="configForm.configName" />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="configForm.configValue" type="textarea" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="configForm.valueType">
            <el-option label="字符串" value="STRING" />
            <el-option label="数字" value="NUMBER" />
            <el-option label="布尔" value="BOOLEAN" />
            <el-option label="JSON" value="JSON" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="configForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="configForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import MissedQuestionDashboard from '@/components/MissedQuestionDashboard.vue'
import type { AdminConfigVO, AdminRoleVO, AdminUserVO } from '@/types'

const authStore = useAuthStore()
const activeTab = ref('users')
const users = ref<AdminUserVO[]>([])
const roles = ref<AdminRoleVO[]>([])
const allRoles = ref<AdminRoleVO[]>([])
const configs = ref<AdminConfigVO[]>([])
const loadingUsers = ref(false)
const loadingRoles = ref(false)
const loadingConfigs = ref(false)

const userFilters = ref({
  keyword: '',
  status: '',
  roleCode: ''
})

const roleFilters = ref({
  keyword: '',
  status: ''
})

const configFilters = ref({
  keyword: '',
  status: ''
})

const showUserDialog = ref(false)
const showRoleDialog = ref(false)
const showConfigDialog = ref(false)
const editingUser = ref<AdminUserVO | null>(null)
const editingRole = ref<AdminRoleVO | null>(null)
const editingConfig = ref<AdminConfigVO | null>(null)
const isEditingCurrentUser = ref(false)

const userForm = ref({
  username: '',
  password: '',
  nickname: '',
  realName: '',
  mobile: '',
  email: '',
  status: 'ACTIVE',
  roleIds: [] as string[]
})

const roleForm = ref({
  roleCode: '',
  roleName: '',
  status: 'ACTIVE',
  remark: ''
})

const configForm = ref({
  configKey: '',
  configName: '',
  configValue: '',
  valueType: 'STRING',
  remark: '',
  status: 'ACTIVE'
})

onMounted(() => {
  loadAll()
})

async function loadAll() {
  await Promise.all([loadRolesForOptions(), loadUsers(), loadRoles(), loadConfigs()])
}

async function loadUsers() {
  loadingUsers.value = true
  try {
    const res = await adminApi.listUsers({
      keyword: userFilters.value.keyword || undefined,
      status: userFilters.value.status || undefined,
      roleCode: userFilters.value.roleCode || undefined
    })
    users.value = res.data || []
  } finally {
    loadingUsers.value = false
  }
}

async function loadRoles() {
  loadingRoles.value = true
  try {
    const res = await adminApi.listRoles({
      keyword: roleFilters.value.keyword || undefined,
      status: roleFilters.value.status || undefined
    })
    roles.value = res.data || []
  } finally {
    loadingRoles.value = false
  }
}

async function loadRolesForOptions() {
  const res = await adminApi.listRoles()
  allRoles.value = res.data || []
}

async function loadConfigs() {
  loadingConfigs.value = true
  try {
    const res = await adminApi.listConfigs({
      keyword: configFilters.value.keyword || undefined,
      status: configFilters.value.status || undefined
    })
    configs.value = res.data || []
  } finally {
    loadingConfigs.value = false
  }
}

function openCreateUserDialog() {
  editingUser.value = null
  isEditingCurrentUser.value = false
  userForm.value = {
    username: '',
    password: '',
    nickname: '',
    realName: '',
    mobile: '',
    email: '',
    status: 'ACTIVE',
    roleIds: []
  }
  showUserDialog.value = true
}

function openEditUserDialog(user: AdminUserVO) {
  editingUser.value = user
  isEditingCurrentUser.value = String(authStore.user?.userId || '') === user.userId
  userForm.value = {
    username: user.username,
    password: '',
    nickname: user.nickname || '',
    realName: user.realName || '',
    mobile: user.mobile || '',
    email: user.email || '',
    status: user.status,
    roleIds: [...(user.roleIds || [])]
  }
  showUserDialog.value = true
}

async function handleSaveUser() {
  if (!userForm.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!editingUser.value && !userForm.value.password.trim()) {
    ElMessage.warning('请输入初始密码')
    return
  }
  try {
    if (editingUser.value) {
      await adminApi.updateUser(editingUser.value.userId, {
        nickname: userForm.value.nickname,
        realName: userForm.value.realName,
        mobile: userForm.value.mobile,
        email: userForm.value.email,
        status: userForm.value.status,
        roleIds: isEditingCurrentUser.value ? undefined : userForm.value.roleIds
      })
    } else {
      await adminApi.createUser({ ...userForm.value })
    }
    ElMessage.success('用户保存成功')
    showUserDialog.value = false
    await Promise.all([loadUsers(), loadRolesForOptions()])
  } catch {
    // handled globally
  }
}

async function toggleUserStatus(user: AdminUserVO) {
  try {
    await adminApi.updateUser(user.userId, {
      nickname: user.nickname || '',
      realName: user.realName || '',
      mobile: user.mobile || '',
      email: user.email || '',
      status: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
      roleIds: user.roleIds
    })
    ElMessage.success('用户状态已更新')
    await loadUsers()
  } catch {
    // handled globally
  }
}

async function handleResetPassword(user: AdminUserVO) {
  try {
    const { value } = await ElMessageBox.prompt(`请输入 ${user.username} 的新密码`, '重置密码', {
      inputType: 'password',
      inputPattern: /^.{6,}$/,
      inputErrorMessage: '密码至少 6 位'
    })
    await adminApi.resetPassword(user.userId, { password: value })
    ElMessage.success('密码重置成功')
  } catch {
    // ignore cancel
  }
}

async function handleDeleteUser(user: AdminUserVO) {
  if (String(authStore.user?.userId || '') === user.userId) {
    ElMessage.warning('不能删除当前登录账号')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username} 吗？`, '提示', { type: 'warning' })
    await adminApi.deleteUser(user.userId)
    ElMessage.success('用户删除成功')
    await loadUsers()
  } catch {
    // ignore cancel
  }
}

function openCreateRoleDialog() {
  editingRole.value = null
  roleForm.value = {
    roleCode: '',
    roleName: '',
    status: 'ACTIVE',
    remark: ''
  }
  showRoleDialog.value = true
}

function openEditRoleDialog(role: AdminRoleVO) {
  editingRole.value = role
  roleForm.value = {
    roleCode: role.roleCode,
    roleName: role.roleName,
    status: role.status,
    remark: role.remark || ''
  }
  showRoleDialog.value = true
}

async function handleSaveRole() {
  if (!roleForm.value.roleCode.trim() || !roleForm.value.roleName.trim()) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  try {
    if (editingRole.value) {
      await adminApi.updateRole(editingRole.value.roleId, {
        roleName: roleForm.value.roleName,
        status: roleForm.value.status,
        remark: roleForm.value.remark
      })
    } else {
      await adminApi.createRole(roleForm.value)
    }
    ElMessage.success('角色保存成功')
    showRoleDialog.value = false
    await Promise.all([loadRolesForOptions(), loadRoles(), loadUsers()])
  } catch {
    // handled globally
  }
}

async function handleDeleteRole(role: AdminRoleVO) {
  try {
    await ElMessageBox.confirm(`确定删除角色 ${role.roleCode} 吗？`, '提示', { type: 'warning' })
    await adminApi.deleteRole(role.roleId)
    ElMessage.success('角色删除成功')
    await Promise.all([loadRolesForOptions(), loadRoles()])
  } catch {
    // ignore cancel
  }
}

function openCreateConfigDialog() {
  editingConfig.value = null
  configForm.value = {
    configKey: '',
    configName: '',
    configValue: '',
    valueType: 'STRING',
    remark: '',
    status: 'ACTIVE'
  }
  showConfigDialog.value = true
}

function openEditConfigDialog(config: AdminConfigVO) {
  editingConfig.value = config
  configForm.value = {
    configKey: config.configKey,
    configName: config.configName,
    configValue: config.configValue || '',
    valueType: config.valueType,
    remark: config.remark || '',
    status: config.status
  }
  showConfigDialog.value = true
}

async function handleSaveConfig() {
  if (!configForm.value.configKey.trim() || !configForm.value.configName.trim()) {
    ElMessage.warning('请填写配置键和配置名称')
    return
  }
  try {
    await adminApi.saveConfig(configForm.value)
    ElMessage.success('配置保存成功')
    showConfigDialog.value = false
    await loadConfigs()
  } catch {
    // handled globally
  }
}

function formatDate(date?: string | null) {
  if (!date) return '-'
  return new Date(date).toLocaleString()
}
</script>

<style lang="scss" scoped>
.admin-manager {
  min-height: 480px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hint-text {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7f92;
}
</style>
